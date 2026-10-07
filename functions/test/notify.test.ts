import {
  FALLBACK_NAME,
  buildData,
  isInvalidTokenError,
  recipients,
  senderName,
  truncate,
  unmuted,
  validateMessage,
} from "../src/notify";

describe("recipients", () => {
  it("never includes the sender", () => {
    expect(recipients(["a", "b"], "a")).toEqual(["b"]);
    expect(recipients(["a", "a"], "a")).toEqual([]);
  });
  it("ignores non-arrays and bad ids", () => {
    expect(recipients("a,b", "a")).toEqual([]);
    expect(recipients(["b", 3, "../x", "b"], "a")).toEqual(["b"]);
  });
});

describe("truncate", () => {
  it("keeps short text and cuts long text with an ellipsis", () => {
    expect(truncate(" hi ", 10)).toBe("hi");
    const t = truncate("x".repeat(500), 100);
    expect(Array.from(t)).toHaveLength(100);
    expect(t.endsWith("…")).toBe(true);
  });
  it("does not split emoji", () => {
    expect(truncate("😀😀😀", 2)).toBe("😀…");
  });
});

describe("validateMessage", () => {
  it("accepts a well-formed message", () => {
    expect(validateMessage("a_b", { senderId: "abc", text: "hi" })).toEqual({ senderId: "abc", text: "hi" });
  });
  it("rejects bad shapes", () => {
    expect(validateMessage("a/b", { senderId: "abc", text: "hi" })).toBeNull();
    expect(validateMessage("a_b", undefined)).toBeNull();
    expect(validateMessage("a_b", { senderId: 1, text: "hi" })).toBeNull();
    expect(validateMessage("a_b", { senderId: "abc", text: "  " })).toBeNull();
    expect(validateMessage("a_b", { senderId: "abc" })).toBeNull();
  });
});

describe("payload", () => {
  it("carries sender name, chatId and recipient only, never message text", () => {
    const d = buildData("Bob", "a_b", "alice");
    expect(Object.keys(d).sort()).toEqual(["chatId", "recipientUid", "senderName", "type"]);
    expect(d).toEqual({ type: "message", chatId: "a_b", senderName: "Bob", recipientUid: "alice" });
    expect("preview" in d).toBe(false);
    expect("text" in d).toBe(false);
  });
  it("binds each payload to its recipient", () => {
    expect(recipients(["alice", "bob"], "bob").map((uid) => buildData("Bob", "alice_bob", uid).recipientUid))
      .toEqual(["alice"]);
  });
  it("falls back when the sender name is missing", () => {
    expect(senderName({}, "a")).toBe(FALLBACK_NAME);
    expect(senderName(null, "a")).toBe(FALLBACK_NAME);
    expect(senderName({ a: "Al" }, "a")).toBe("Al");
  });
});

describe("isInvalidTokenError", () => {
  it("classifies prune-worthy codes", () => {
    expect(isInvalidTokenError("messaging/registration-token-not-registered")).toBe(true);
    expect(isInvalidTokenError("messaging/invalid-registration-token")).toBe(true);
    expect(isInvalidTokenError("messaging/internal-error")).toBe(false);
    expect(isInvalidTokenError(undefined)).toBe(false);
  });
});

describe("unmuted", () => {
  it("drops only recipients who muted the chat", () => {
    expect(unmuted(["a", "b", "c"], { a: true, b: false })).toEqual(["b", "c"]);
  });
  it("tolerates a missing or malformed muted field", () => {
    expect(unmuted(["a"], undefined)).toEqual(["a"]);
    expect(unmuted(["a"], "x")).toEqual(["a"]);
  });
});
