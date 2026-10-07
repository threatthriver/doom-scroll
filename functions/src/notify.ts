/** Pure helpers for the new-message push. No Firebase imports so they are unit-testable. */

export const CHAT_ID_RE = /^[A-Za-z0-9_-]{1,128}$/;
export const UID_RE = /^[A-Za-z0-9]{1,128}$/;
export const MAX_NAME = 60;
export const FALLBACK_NAME = "New message";

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

export function truncate(text: string, max: number): string {
  const chars = Array.from(text.trim()); // don't split surrogate pairs
  return chars.length <= max ? chars.join("") : chars.slice(0, max - 1).join("") + "…";
}

/** Everyone in the chat except the sender. Never includes the sender. */
export function recipients(participants: unknown, senderId: string): string[] {
  if (!Array.isArray(participants)) return [];
  return [...new Set(participants.filter((p): p is string => typeof p === "string" && UID_RE.test(p)))]
    .filter((p) => p !== senderId);
}

/** Drops recipients who muted this chat (chat.muted is a uid -> true map). */
export function unmuted(uids: string[], muted: unknown): string[] {
  const m = typeof muted === "object" && muted !== null ? (muted as Record<string, unknown>) : {};
  return uids.filter((u) => m[u] !== true);
}

export interface ValidMessage {
  senderId: string;
  text: string;
}

/** Returns null unless the message doc has the expected shape. */
export function validateMessage(chatId: string, data: unknown): ValidMessage | null {
  if (!CHAT_ID_RE.test(chatId) || typeof data !== "object" || data === null) return null;
  const { senderId, text } = data as Record<string, unknown>;
  if (typeof senderId !== "string" || !UID_RE.test(senderId)) return null;
  if (typeof text !== "string" || text.trim().length === 0) return null;
  return { senderId, text };
}

export function senderName(participantNames: unknown, senderId: string): string {
  const names = typeof participantNames === "object" && participantNames !== null
    ? (participantNames as Record<string, unknown>)
    : {};
  const n = names[senderId];
  return typeof n === "string" && n.trim() ? truncate(n, MAX_NAME) : FALLBACK_NAME;
}

/**
 * Data-only payload so the app decides whether to show it (suppressed while the chat is open).
 * Carries no message text: FCM only sees who wrote, in which chat, and for whom.
 * The client drops it unless recipientUid is the signed-in user.
 */
export function buildData(name: string, chatId: string, recipientUid: string): Record<string, string> {
  return { type: "message", chatId, senderName: name, recipientUid };
}

export function isInvalidTokenError(code: string | undefined): boolean {
  return code !== undefined && INVALID_TOKEN_CODES.has(code);
}
