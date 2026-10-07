import assert from "node:assert/strict";
import { beforeEach, describe, it } from "node:test";
import type { Firestore, FsDoc, FsFields } from "../src/firestore.ts";
import { handle, type Deps } from "../src/handler.ts";
import { AuthError } from "../src/jwt.ts";
import { RateLimiter, RecentSet, type FcmOutcome } from "../src/logic.ts";

const NOW = 1_800_000_000_000;
const A = "alice1";
const B = "bob1";
const CHAT = `${A}_${B}`;

const s = (v: string) => ({ stringValue: v });
const boolMapValue = (m: Record<string, boolean>) => ({
  mapValue: { fields: Object.fromEntries(Object.entries(m).map(([k, v]) => [k, { booleanValue: v }])) },
});

class FakeFirestore {
  docs = new Map<string, FsFields>();
  tokens = new Map<string, string[]>();
  deleted: string[] = [];
  fail = false;

  async get(path: string): Promise<FsDoc | null> {
    if (this.fail) throw new Error("outage");
    const f = this.docs.get(path);
    return f ? { name: path, fields: f } : null;
  }
  async listIds(path: string, limit: number) {
    return (this.tokens.get(path) ?? []).slice(0, limit);
  }
  async delete(path: string) {
    this.deleted.push(decodeURIComponent(path));
  }
}

let fs: FakeFirestore;
let sends: Array<{ token: string; data: Record<string, string>; chatId: string }>;
let outcomeFor: (token: string) => FcmOutcome;
let now: number;
let logs: Array<Record<string, unknown>>;
let authedUid: string | null;

function deps(): Deps {
  return {
    // Real signature checking is covered in jwt.test.ts; here the token "valid" means user `authedUid`.
    verifyToken: async () => {
      if (!authedUid) throw new AuthError("no");
      return { uid: authedUid };
    },
    firestore: fs as unknown as Firestore,
    send: async (token, data, chatId) => {
      sends.push({ token, data, chatId });
      return outcomeFor(token);
    },
    now: () => now,
    limiter: new RateLimiter(60, 60_000),
    handled: new RecentSet(300_000),
    log: (e) => logs.push(e),
  };
}

beforeEach(() => {
  now = NOW;
  sends = [];
  logs = [];
  authedUid = A;
  outcomeFor = () => "ok";
  fs = new FakeFirestore();
  fs.docs.set(`chats/${CHAT}`, {
    participants: { arrayValue: { values: [s(A), s(B)] } },
    participantNames: { mapValue: { fields: { [A]: s("Alice"), [B]: s("Bob") } } },
    muted: boolMapValue({}),
  });
  fs.docs.set(`chats/${CHAT}/messages/m1`, {
    senderId: s(A),
    timestamp: { timestampValue: new Date(NOW - 1000).toISOString() },
  });
  fs.tokens.set(`users/${B}/fcmTokens`, ["tokB1", "tokB2"]);
  fs.tokens.set(`users/${A}/fcmTokens`, ["tokA1"]);
});

function post(body: unknown, headers: Record<string, string> = { Authorization: "Bearer valid" }) {
  return new Request("https://x/v1/notify", {
    method: "POST",
    headers,
    body: typeof body === "string" ? body : JSON.stringify(body),
  });
}
const ok = { chatId: CHAT, messageId: "m1" };

describe("handler", () => {
  it("health check and routing", async () => {
    assert.equal((await handle(new Request("https://x/healthz"), deps())).status, 200);
    assert.equal((await handle(new Request("https://x/nope"), deps())).status, 404);
    assert.equal((await handle(new Request("https://x/v1/notify"), deps())).status, 405);
  });

  it("sends to every token of the other participant only", async () => {
    const res = await handle(post(ok), deps());
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { sent: 2, pruned: 0 });
    assert.deepEqual(sends.map((x) => x.token).sort(), ["tokB1", "tokB2"]);
    assert.deepEqual(sends[0].data, { type: "message", chatId: CHAT, senderName: "Alice", recipientUid: B });
    assert.ok(!JSON.stringify(logs).includes("tokB1"), "tokens must never be logged");
  });

  it("requires a valid token", async () => {
    assert.equal((await handle(post(ok, {}), deps())).status, 401);
    authedUid = null;
    assert.equal((await handle(post(ok), deps())).status, 401);
    assert.equal(sends.length, 0);
  });

  it("rejects malformed bodies", async () => {
    assert.equal((await handle(post("{nope"), deps())).status, 400);
    assert.equal((await handle(post({ chatId: "x/y", messageId: "m1" }), deps())).status, 400);
    assert.equal((await handle(post("x".repeat(2000)), deps())).status, 413);
  });

  it("forbids non-participants", async () => {
    authedUid = "mallory";
    assert.equal((await handle(post(ok), deps())).status, 403);
    assert.equal(sends.length, 0);
  });

  it("forbids pushing for someone else's message", async () => {
    authedUid = B; // B is in the chat but m1 was sent by A
    assert.equal((await handle(post(ok), deps())).status, 403);
    assert.equal(sends.length, 0);
  });

  it("404s on unknown chat or message", async () => {
    assert.equal((await handle(post({ chatId: "nope", messageId: "m1" }), deps())).status, 404);
    assert.equal((await handle(post({ chatId: CHAT, messageId: "zzz" }), deps())).status, 404);
  });

  it("refuses old messages (replay protection)", async () => {
    now = NOW + 10 * 60_000;
    assert.equal((await handle(post(ok), deps())).status, 409);
    assert.equal(sends.length, 0);
  });

  it("doesn't push twice for the same message", async () => {
    const d = deps();
    assert.equal((await handle(post(ok), d)).status, 200);
    const again = await handle(post(ok), d);
    assert.deepEqual(await again.json(), { sent: 0, duplicate: true });
    assert.equal(sends.length, 2);
  });

  it("skips muted recipients", async () => {
    fs.docs.get(`chats/${CHAT}`)!.muted = boolMapValue({ [B]: true });
    const res = await handle(post(ok), deps());
    assert.deepEqual(await res.json(), { sent: 0, pruned: 0 });
    assert.equal(sends.length, 0);
  });

  it("prunes dead tokens and still counts the live ones", async () => {
    outcomeFor = (t) => (t === "tokB1" ? "unregistered" : "ok");
    const res = await handle(post(ok), deps());
    assert.deepEqual(await res.json(), { sent: 1, pruned: 1 });
    assert.deepEqual(fs.deleted, [`users/${B}/fcmTokens/tokB1`]);
  });

  it("asks the client to retry when every send failed transiently", async () => {
    outcomeFor = () => "retry";
    const d = deps();
    assert.equal((await handle(post(ok), d)).status, 502);
    outcomeFor = () => "ok"; // the retry must not be swallowed as a duplicate
    assert.equal((await handle(post(ok), d)).status, 200);
  });

  it("doesn't ask for retries when a failure is permanent", async () => {
    outcomeFor = () => "fatal";
    const res = await handle(post(ok), deps());
    assert.equal(res.status, 200);
    assert.deepEqual(await res.json(), { sent: 0, pruned: 0 });
  });

  it("returns 200 with sent:0 when the recipient has no device registered yet", async () => {
    fs.tokens.set(`users/${B}/fcmTokens`, []);
    const res = await handle(post(ok), deps());
    assert.deepEqual(await res.json(), { sent: 0, pruned: 0 });
  });

  it("maps a backend outage to 503", async () => {
    fs.fail = true;
    assert.equal((await handle(post(ok), deps())).status, 503);
  });

  it("rate limits a single sender", async () => {
    const d = { ...deps(), limiter: new RateLimiter(1, 60_000) };
    assert.equal((await handle(post({ chatId: CHAT, messageId: "m1" }), d)).status, 200);
    assert.equal((await handle(post({ chatId: CHAT, messageId: "m2" }), d)).status, 429);
  });
});
