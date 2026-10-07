import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  FRESH_WINDOW_MS,
  RateLimiter,
  RecentSet,
  buildData,
  buildFcmMessage,
  classifyFcmResponse,
  isFresh,
  parseNotifyRequest,
  recipientsFor,
  senderName,
  truncate,
} from "../src/logic.ts";

describe("parseNotifyRequest", () => {
  it("accepts a valid request", () => {
    assert.deepEqual(parseNotifyRequest({ chatId: "a1_b2", messageId: "AbC123xyz" }), {
      chatId: "a1_b2",
      messageId: "AbC123xyz",
    });
  });
  it("rejects anything else", () => {
    for (const bad of [null, "x", [], {}, { chatId: "a" }, { chatId: "a/b", messageId: "m" }, { chatId: "a", messageId: "m/1" },
      { chatId: "a", messageId: "m", extra: 1 }, { chatId: 1, messageId: "m" }, { chatId: "a".repeat(129), messageId: "m" }]) {
      assert.equal(parseNotifyRequest(bad), null, JSON.stringify(bad));
    }
  });
});

describe("recipientsFor", () => {
  it("excludes the sender, duplicates and invalid ids", () => {
    assert.deepEqual(recipientsFor(["a", "b", "b", "bad id"], "a", {}), ["b"]);
  });
  it("skips users who muted the chat", () => {
    assert.deepEqual(recipientsFor(["a", "b", "c"], "a", { b: true, c: false }), ["c"]);
  });
  it("never includes the sender even if listed twice", () => {
    assert.deepEqual(recipientsFor(["a", "a"], "a", {}), []);
  });
});

describe("names and payload", () => {
  it("falls back when the name is missing or blank", () => {
    assert.equal(senderName({}, "a"), "New message");
    assert.equal(senderName({ a: "  " }, "a"), "New message");
  });
  it("truncates long names without splitting emoji", () => {
    const out = senderName({ a: "😀".repeat(100) }, "a");
    assert.equal(Array.from(out).length, 60);
    assert.ok(out.endsWith("…"));
  });
  it("truncate keeps short text", () => assert.equal(truncate(" hi ", 10), "hi"));
  it("data payload carries no message text", () => {
    const d = buildData("Bob", "c1", "u2");
    assert.deepEqual(d, { type: "message", chatId: "c1", senderName: "Bob", recipientUid: "u2" });
  });
  it("FCM message is high priority, collapsible and expires", () => {
    const m = buildFcmMessage("tok", buildData("Bob", "c1", "u2"), "c1").message;
    assert.equal(m.token, "tok");
    assert.equal(m.android.priority, "HIGH");
    assert.equal(m.android.collapse_key, "c1");
    assert.equal(m.android.ttl, "86400s");
    assert.ok(!("notification" in m), "must stay data-only so the app controls display");
  });
});

describe("isFresh", () => {
  const now = 1_000_000_000_000;
  it("accepts recent and slightly-future timestamps", () => {
    assert.ok(isFresh(now - 1000, now));
    assert.ok(isFresh(now + 30_000, now));
  });
  it("rejects old and far-future timestamps", () => {
    assert.ok(!isFresh(now - FRESH_WINDOW_MS - 1, now));
    assert.ok(!isFresh(now + 120_000, now));
  });
});

describe("classifyFcmResponse", () => {
  it("ok", () => assert.equal(classifyFcmResponse(200, {}), "ok"));
  it("unregistered by error code or 404", () => {
    assert.equal(classifyFcmResponse(404, { error: { details: [{ errorCode: "UNREGISTERED" }] } }), "unregistered");
    assert.equal(classifyFcmResponse(404, null), "unregistered");
  });
  it("auth problems", () => {
    assert.equal(classifyFcmResponse(401, {}), "auth");
    assert.equal(classifyFcmResponse(403, {}), "auth");
  });
  it("transient problems", () => {
    for (const s of [429, 500, 503]) assert.equal(classifyFcmResponse(s, {}), "retry");
  });
  it("a token FCM calls invalid is dead; other 400s are fatal, never pruned", () => {
    const badToken = { error: { status: "INVALID_ARGUMENT", details: [{ errorCode: "INVALID_ARGUMENT" }, { fieldViolations: [{ field: "message.token" }] }] } };
    assert.equal(classifyFcmResponse(400, badToken), "unregistered");
    const badPayload = { error: { status: "INVALID_ARGUMENT", details: [{ fieldViolations: [{ field: "message.data" }] }] } };
    assert.equal(classifyFcmResponse(400, badPayload), "fatal");
  });
  it("a bad request is fatal and not retried", () => {
    assert.equal(classifyFcmResponse(400, { error: { status: "INVALID_ARGUMENT" } }), "fatal");
  });
});

describe("RateLimiter", () => {
  it("blocks after the limit and resets after the window", () => {
    const rl = new RateLimiter(2, 1000);
    assert.ok(rl.allow("u", 0));
    assert.ok(rl.allow("u", 10));
    assert.ok(!rl.allow("u", 20));
    assert.ok(rl.allow("other", 20));
    assert.ok(rl.allow("u", 1000));
  });
});

describe("RecentSet", () => {
  it("rejects a repeat and allows it again after forget", () => {
    const s = new RecentSet(1000);
    assert.ok(s.add("k", 0));
    assert.ok(!s.add("k", 1));
    s.forget("k");
    assert.ok(s.add("k", 2));
  });
});
