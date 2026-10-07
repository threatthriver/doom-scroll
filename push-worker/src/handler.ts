/** The relay's request handler, with its dependencies injected so it can be tested end to end. */

import type { Firestore } from "./firestore.ts";
import { boolMap, str, strArray, strMap, timestampMs } from "./firestore.ts";
import { AuthError, type VerifiedToken } from "./jwt.ts";
import {
  MAX_TOKENS_PER_USER,
  RateLimiter,
  RecentSet,
  buildData,
  isFresh,
  parseNotifyRequest,
  recipientsFor,
  senderName,
  type FcmOutcome,
} from "./logic.ts";

const MAX_BODY_BYTES = 1024;

export interface Deps {
  /** Verifies a Firebase ID token; throws AuthError when it isn't valid. */
  verifyToken: (token: string) => Promise<VerifiedToken>;
  firestore: Firestore;
  send: (deviceToken: string, data: Record<string, string>, chatId: string) => Promise<FcmOutcome>;
  now: () => number;
  /** Per-sender limit so a signed-in user can't turn the relay into a spam cannon. */
  limiter: RateLimiter;
  handled: RecentSet;
  log: (event: Record<string, unknown>) => void;
}

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

/** Error class and HTTP status only, so logs explain failures without leaking tokens or text. */
function errInfo(e: unknown): string {
  if (e instanceof Error) {
    const status = (e as { status?: number }).status;
    return status ? `${e.name}:${status}` : `${e.name}:${e.message.slice(0, 80)}`;
  }
  return "unknown";
}

export async function handle(req: Request, d: Deps): Promise<Response> {
  const url = new URL(req.url);
  if (url.pathname === "/healthz" && req.method === "GET") return json(200, { ok: true });
  if (url.pathname !== "/v1/notify") return json(404, { error: "not_found" });
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });

  const started = d.now();

  // 1. Who is calling? A real, unexpired Firebase ID token for this project.
  const auth = req.headers.get("Authorization") ?? "";
  const m = /^Bearer\s+(.+)$/i.exec(auth);
  if (!m) return json(401, { error: "unauthorized" });
  let uid: string;
  try {
    ({ uid } = await d.verifyToken(m[1].trim()));
  } catch (e) {
    if (e instanceof AuthError) return json(401, { error: "unauthorized" });
    d.log({ evt: "auth_backend_error", err: errInfo(e) });
    return json(503, { error: "try_again" });
  }

  // 2. Well-formed, small request.
  const raw = await req.text();
  if (raw.length > MAX_BODY_BYTES) return json(413, { error: "too_large" });
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return json(400, { error: "bad_request" });
  }
  const r = parseNotifyRequest(parsed);
  if (!r) return json(400, { error: "bad_request" });

  if (!d.limiter.allow(uid, d.now())) return json(429, { error: "rate_limited" });

  try {
    // 3. The caller must belong to the chat, and the message must be theirs and brand new.
    const chat = await d.firestore.get(`chats/${r.chatId}`);
    if (!chat) return json(404, { error: "not_found" });
    const participants = strArray(chat.fields, "participants");
    if (!participants.includes(uid)) return json(403, { error: "forbidden" });

    const msg = await d.firestore.get(`chats/${r.chatId}/messages/${r.messageId}`);
    if (!msg) return json(404, { error: "not_found" });
    if (str(msg.fields, "senderId") !== uid) return json(403, { error: "forbidden" });
    const sentAt = timestampMs(msg.fields, "timestamp");
    if (sentAt === undefined || !isFresh(sentAt, d.now())) return json(409, { error: "stale_message" });

    // 4. Already handled (client retry / replay)? Do nothing.
    const dedupeKey = `${r.chatId}/${r.messageId}`;
    if (!d.handled.add(dedupeKey, d.now())) return json(200, { sent: 0, duplicate: true });

    // 5. Fan out to the other participant(s), skipping anyone who muted the chat.
    const to = recipientsFor(participants, uid, boolMap(chat.fields, "muted"));
    const name = senderName(strMap(chat.fields, "participantNames"), uid);

    let sent = 0;
    let pruned = 0;
    let failed = 0;
    let transient = 0;
    for (const recipient of to) {
      const tokens = await d.firestore.listIds(`users/${recipient}/fcmTokens`, MAX_TOKENS_PER_USER);
      const data = buildData(name, r.chatId, recipient);
      const outcomes = await Promise.all(tokens.map((t) => d.send(t, data, r.chatId)));
      const stale: string[] = [];
      outcomes.forEach((o, i) => {
        if (o === "ok") sent++;
        else if (o === "unregistered") stale.push(tokens[i]);
        else {
          failed++;
          if (o === "retry" || o === "auth") transient++;
        }
      });
      // Dead tokens are removed so they don't cost a request on every future message.
      await Promise.all(
        stale.map((t) =>
          d.firestore.delete(`users/${recipient}/fcmTokens/${encodeURIComponent(t)}`).catch(() => undefined),
        ),
      );
      pruned += stale.length;
    }

    // Nothing was delivered and at least one failure may clear up on its own: let the client retry.
    // (Permanent per-token failures are not retried; retrying can't help.)
    if (sent === 0 && transient > 0) {
      d.handled.forget(dedupeKey);
      d.log({ evt: "notify_failed", chatId: r.chatId, failed, ms: d.now() - started });
      return json(502, { error: "delivery_failed" });
    }
    d.log({ evt: "notify", chatId: r.chatId, recipients: to.length, sent, pruned, failed, ms: d.now() - started });
    return json(200, { sent, pruned });
  } catch (e) {
    // Firestore/Google outage: not the caller's fault, so invite a retry.
    d.log({ evt: "backend_error", chatId: r.chatId, err: errInfo(e), ms: d.now() - started });
    return json(503, { error: "try_again" });
  }
}
