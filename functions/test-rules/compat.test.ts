// Tests for firestore.rules (the compatibility rules deployed while 1.3.x and 1.4.x apps coexist).
// Each "works" case mirrors a write the shipped apps really make, so a deploy can't break them.
import { readFileSync } from "fs";
import { resolve } from "path";
import {
  RulesTestEnvironment,
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Firestore,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  increment,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from "firebase/firestore";

const A = "alice1";
const B = "bob1";
const C = "carol1";
const CHAT = `${A}_${B}`;
let env: RulesTestEnvironment;

// Compat rules don't look at email_verified, so unverified users keep working.
const db = (uid: string, verified = false): Firestore =>
  env.authenticatedContext(uid, { email: `${uid}@x.com`, email_verified: verified }).firestore() as unknown as Firestore;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-compat",
    firestore: { rules: readFileSync(resolve(__dirname, "../../firestore.rules"), "utf8") },
  });
});
afterAll(async () => env.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    for (const [uid, name] of [[A, "Alice"], [B, "Bob"], [C, "Carol"]]) {
      await f.doc(`users/${uid}`).set({ uid, displayName: name, username: uid, bio: "", photoUrl: "" });
    }
    await f.doc(`chats/${CHAT}`).set({ participants: [A, B], participantNames: { [A]: "Alice", [B]: "Bob" } });
    await f.doc(`chats/${CHAT}/messages/m1`).set({ text: "hi", senderId: B, timestamp: new Date(), reactions: {} });
  });
});

describe("what the shipped apps do keeps working", () => {
  it("1.3.x send (dotted unreadCount key in a merge-set)", async () => {
    const f = db(A);
    const b = writeBatch(f);
    b.set(doc(f, `chats/${CHAT}/messages/n1`), { text: "x", senderId: A, timestamp: serverTimestamp(), reactions: {} });
    b.set(doc(f, `chats/${CHAT}`), { lastMessage: "x", lastSenderId: A, lastMessageAt: serverTimestamp(), [`unreadCount.${B}`]: increment(1) }, { merge: true });
    await assertSucceeds(b.commit());
  });
  it("1.4.x send (nested unreadCount map) with a reply quote", async () => {
    const f = db(A);
    const b = writeBatch(f);
    b.set(doc(f, `chats/${CHAT}/messages/n2`), {
      text: "x", senderId: A, timestamp: serverTimestamp(), reactions: {},
      replyToId: "m1", replyToText: "q==", replyToSender: B,
    });
    b.set(doc(f, `chats/${CHAT}`), { lastMessage: "x", lastSenderId: A, lastMessageAt: serverTimestamp(), unreadCount: { [B]: increment(1) } }, { merge: true });
    await assertSucceeds(b.commit());
  });
  it("opening a chat, archive, mute, pin, mark read", async () => {
    await assertSucceeds(setDoc(doc(db(A), `chats/${CHAT}`), { participants: [A, B], participantNames: { [A]: "Alice", [B]: "Bob" } }, { mergeFields: ["participants", "participantNames"] }));
    await assertSucceeds(updateDoc(doc(db(A), `chats/${CHAT}`), { [`archived.${A}`]: true, [`muted.${A}`]: true, pinned: true, [`unreadCount.${A}`]: 0 }));
  });
  it("profile sign-up without an email field (current apps)", async () => {
    const f = db("dave1");
    const b = writeBatch(f);
    b.set(doc(f, "users/dave1"), { uid: "dave1", username: "dave1", displayName: "Dave", photoUrl: "", bio: "", createdAt: serverTimestamp() });
    b.set(doc(f, "usernames/dave1"), { uid: "dave1" });
    await assertSucceeds(b.commit());
  });
  it("profile sign-up from very old apps that sent their own email", async () => {
    const f = db("erin1");
    const b = writeBatch(f);
    b.set(doc(f, "users/erin1"), { uid: "erin1", username: "erin1", displayName: "Erin", emailLower: "erin1@x.com" });
    b.set(doc(f, "usernames/erin1"), { uid: "erin1" });
    await assertSucceeds(b.commit());
  });
  it("deleting your own message", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`chats/${CHAT}/messages/mine`).set({ text: "y", senderId: A, timestamp: new Date() });
    });
    await assertSucceeds(deleteDoc(doc(db(A), `chats/${CHAT}/messages/mine`)));
  });
});

describe("newly allowed, and safe", () => {
  it("push tokens are owner-only", async () => {
    const tok = { createdAt: serverTimestamp(), platform: "android" };
    await assertSucceeds(setDoc(doc(db(A), `users/${A}/fcmTokens/t1`), tok));
    await assertSucceeds(deleteDoc(doc(db(A), `users/${A}/fcmTokens/t1`)));
    await assertFails(setDoc(doc(db(B), `users/${A}/fcmTokens/t2`), tok));
    await assertFails(getDoc(doc(db(B), `users/${A}/fcmTokens/t1`)));
    await assertFails(setDoc(doc(db(A), `users/${A}/fcmTokens/t3`), { ...tok, extra: 1 }));
  });
  it("public keys: everyone reads, only the owner writes", async () => {
    await assertSucceeds(setDoc(doc(db(A), `publicKeys/${A}`), { publicKey: "MFkw==", updatedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(doc(db(B), `publicKeys/${A}`)));
    await assertFails(setDoc(doc(db(B), `publicKeys/${A}`), { publicKey: "evil", updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db(A), `publicKeys/${A}`)));
  });
  it("profile edits: own name/bio only, bounded, no email", async () => {
    await assertSucceeds(updateDoc(doc(db(A), `users/${A}`), { displayName: "Ali", bio: "hey", photoUrl: "" }));
    await assertFails(updateDoc(doc(db(A), `users/${B}`), { displayName: "Hacked" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { username: "other" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { emailLower: "a@x.com" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { displayName: "" }));
  });
  it("a profile can't claim someone else's email", async () => {
    const f = db("fay1");
    const b = writeBatch(f);
    b.set(doc(f, "users/fay1"), { uid: "fay1", username: "fay1", displayName: "Fay", emailLower: "bob1@x.com" });
    b.set(doc(f, "usernames/fay1"), { uid: "fay1" });
    await assertFails(b.commit());
  });
  it("reactions: only your own, and never the text", async () => {
    const m = (u: string) => doc(db(u), `chats/${CHAT}/messages/m1`);
    await assertSucceeds(updateDoc(m(A), { [`reactions.${A}`]: "👍" }));
    await assertSucceeds(updateDoc(m(A), { [`reactions.${A}`]: deleteField() }));
    await assertFails(updateDoc(m(A), { [`reactions.${B}`]: "👍" }));
    await assertFails(updateDoc(m(A), { text: "edited" }));
    await assertFails(updateDoc(doc(db(C), `chats/${CHAT}/messages/m1`), { [`reactions.${C}`]: "👍" }));
  });
});

describe("still blocked", () => {
  it("outsiders can't read or write a chat", async () => {
    await assertFails(getDoc(doc(db(C), `chats/${CHAT}`)));
    await assertFails(getDoc(doc(db(C), `chats/${CHAT}/messages/m1`)));
    await assertFails(updateDoc(doc(db(C), `chats/${CHAT}`), { pinned: true }));
  });
  it("you can't send as someone else or delete their message", async () => {
    await assertFails(setDoc(doc(db(A), `chats/${CHAT}/messages/n3`), { text: "x", senderId: B, timestamp: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db(A), `chats/${CHAT}/messages/m1`)));
  });
  it("signed-out users get nothing", async () => {
    const anon = env.unauthenticatedContext().firestore() as unknown as Firestore;
    await assertFails(getDoc(doc(anon, `users/${A}`)));
    await assertFails(getDoc(doc(anon, `publicKeys/${A}`)));
  });
});
