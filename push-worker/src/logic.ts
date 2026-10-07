/** Pure helpers for the push relay. No I/O, so every rule here is unit tested. */

export const CHAT_ID_RE = /^[A-Za-z0-9_-]{1,128}$/;
export const UID_RE = /^[A-Za-z0-9]{1,128}$/;
export const MSG_ID_RE = /^[A-Za-z0-9]{1,64}$/;
export const MAX_NAME = 60;
export const FALLBACK_NAME = "New message";
/** A push is only sent for a message created this recently (blocks replaying old message ids). */
export const FRESH_WINDOW_MS = 2 * 60 * 1000;
/** Future timestamps beyond this are rejected (clock skew allowance). */
export const FUTURE_SLACK_MS = 60 * 1000;
/** Keeps one request within the free plan's subrequest budget. */
export const MAX_TOKENS_PER_USER = 10;

export interface NotifyRequest {
  chatId: string;
  messageId: string;
}

/** Parses the request body; returns null for anything that isn't exactly {chatId, messageId}. */
export function parseNotifyRequest(body: unknown): NotifyRequest | null {
  if (typeof body !== "object" || body === null || Array.isArray(body)) return null;
  const { chatId, messageId, ...rest } = body as Record<string, unknown>;
  if (Object.keys(rest).length > 0) return null;
  if (typeof chatId !== "string" || !CHAT_ID_RE.test(chatId)) return null;
  if (typeof messageId !== "string" || !MSG_ID_RE.test(messageId)) return null;
  return { chatId, messageId };
}

export function truncate(text: string, max: number): string {
  const chars = Array.from(text.trim()); // don't split surrogate pairs
  return chars.length <= max ? chars.join("") : chars.slice(0, max - 1).join("") + "…";
}

export function senderName(names: Record<string, string>, senderId: string): string {
  const n = names[senderId];
  return typeof n === "string" && n.trim() ? truncate(n, MAX_NAME) : FALLBACK_NAME;
}

/** Everyone in the chat except the sender and anyone who muted it. */
export function recipientsFor(
  participants: string[],
  senderId: string,
  muted: Record<string, boolean>,
): string[] {
  const unique = [...new Set(participants.filter((p) => UID_RE.test(p)))];
  return unique.filter((p) => p !== senderId && muted[p] !== true);
}

export function isFresh(messageMs: number, nowMs: number): boolean {
  return messageMs >= nowMs - FRESH_WINDOW_MS && messageMs <= nowMs + FUTURE_SLACK_MS;
}

/**
 * Data-only payload: it names who wrote and where, never what (messages are end-to-end
 * encrypted). The app drops it unless recipientUid is the signed-in user.
 */
export function buildData(name: string, chatId: string, recipientUid: string): Record<string, string> {
  return { type: "message", chatId, senderName: name, recipientUid };
}

export function buildFcmMessage(token: string, data: Record<string, string>, chatId: string) {
  return {
    message: {
      token,
      data,
      android: {
        priority: "HIGH",
        // A newer push for the same chat replaces a still-undelivered older one.
        collapse_key: chatId.slice(0, 64),
        ttl: "86400s",
      },
    },
  };
}

export type FcmOutcome = "ok" | "unregistered" | "retry" | "auth" | "fatal";

/** Maps an FCM HTTP v1 response to what the relay should do next. */
export function classifyFcmResponse(status: number, body: unknown): FcmOutcome {
  if (status >= 200 && status < 300) return "ok";
  const err = (
    body as {
      error?: {
        status?: string;
        details?: Array<{ errorCode?: string; fieldViolations?: Array<{ field?: string }> }>;
      };
    } | null
  )?.error;
  const code = err?.details?.find((d) => typeof d?.errorCode === "string")?.errorCode;
  if (code === "UNREGISTERED" || status === 404) return "unregistered";
  // A token FCM calls malformed will never work. Only treat it as dead when FCM blames the token
  // field itself, so a bug in our own payload can never wipe everyone's tokens.
  const tokenRejected = err?.details?.some((d) => d?.fieldViolations?.some((v) => v?.field === "message.token"));
  if (status === 400 && tokenRejected) return "unregistered";
  if (status === 401 || status === 403 || err?.status === "UNAUTHENTICATED") return "auth";
  if (status === 429 || status >= 500 || code === "QUOTA_EXCEEDED" || code === "UNAVAILABLE") return "retry";
  return "fatal";
}

/** Fixed-window limiter per key, best effort (lives in one isolate's memory). */
export class RateLimiter {
  private hits = new Map<string, { windowStart: number; count: number }>();
  constructor(
    private readonly limit: number,
    private readonly windowMs: number,
  ) {}

  allow(key: string, nowMs: number): boolean {
    const h = this.hits.get(key);
    if (!h || nowMs - h.windowStart >= this.windowMs) {
      this.hits.set(key, { windowStart: nowMs, count: 1 });
      this.prune(nowMs);
      return true;
    }
    if (h.count >= this.limit) return false;
    h.count++;
    return true;
  }

  private prune(nowMs: number) {
    if (this.hits.size < 1000) return;
    for (const [k, v] of this.hits) if (nowMs - v.windowStart >= this.windowMs) this.hits.delete(k);
  }
}

/** Remembers recently handled message ids so a retry or replay doesn't push twice. */
export class RecentSet {
  private seen = new Map<string, number>();
  constructor(private readonly ttlMs: number) {}

  /** Returns true if newly added, false if already seen. */
  add(key: string, nowMs: number): boolean {
    this.prune(nowMs);
    if (this.seen.has(key)) return false;
    this.seen.set(key, nowMs + this.ttlMs);
    return true;
  }

  forget(key: string) {
    this.seen.delete(key);
  }

  private prune(nowMs: number) {
    if (this.seen.size < 500) return;
    for (const [k, exp] of this.seen) if (exp <= nowMs) this.seen.delete(k);
  }
}
