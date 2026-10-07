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
  getDocs,
  collection,
  increment,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const A = "alice1";
const B = "bob1";
const C = "carol1";
const CHAT = `${A}_${B}`;

let env: RulesTestEnvironment;

function db(uid: string, verified = true): Firestore {
  return env
    .authenticatedContext(uid, { email: `${uid}@x.com`, email_verified: verified })
    .firestore() as unknown as Firestore;
}

const names = { [A]: "Alice", [B]: "Bob" };
const newChat = () => ({ participants: [A, B], participantNames: names });

async function seed(withChat = true) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    for (const [uid, name] of [[A, "Alice"], [B, "Bob"], [C, "Carol"]]) {
      await f.doc(`users/${uid}`).set({ uid, displayName: name, username: uid, bio: "", photoUrl: "" });
      await f.doc(`usernames/${uid}`).set({ uid });
    }
    if (withChat) {
      await f.doc(`chats/${CHAT}`).set({
        participants: [A, B],
        participantNames: names,
        lastMessage: "cipher",
        lastSenderId: B,
        unreadCount: { [A]: 2, [B]: 1 },
        muted: {},
        archived: {},
        pinned: false,
      });
      await f.doc(`chats/${CHAT}/messages/m1`).set({
        text: "hi",
        senderId: B,
        timestamp: new Date(),
        reactions: {},
      });
      await f.doc(`chats/${CHAT}/messages/m2`).set({
        text: "yo",
        senderId: A,
        timestamp: new Date(),
        reactions: {},
      });
    }
  });
}

/** Mirrors FirestoreChatRepository.sendMessage: message create + chat metadata in one batch. */
function sendBatch(
  f: Firestore,
  sender: string,
  text: string,
  msgOverrides: Record<string, unknown> = {},
  id = "new1",
) {
  const b = writeBatch(f);
  const other = sender === A ? B : A;
  b.set(doc(f, `chats/${CHAT}/messages/${id}`), {
    text,
    senderId: sender,
    timestamp: serverTimestamp(),
    reactions: {},
    ...msgOverrides,
  });
  // Same shape as the app: a merge-set with a NESTED unreadCount map (a dotted key in set() would
  // be stored as a literal top-level field name, not a nested path).
  b.set(
    doc(f, `chats/${CHAT}`),
    {
      lastMessage: text,
      lastMessageAt: serverTimestamp(),
      lastSenderId: sender,
      unreadCount: { [other]: increment(1) },
    },
    { merge: true },
  );
  return b.commit();
}

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-secure-message",
    firestore: { rules: readFileSync(resolve(__dirname, "../../firestore.rules.next"), "utf8") },
  });
});
afterAll(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

describe("chat create", () => {
  beforeEach(() => seed(false));

  it("allows a valid chat", async () => {
    await assertSucceeds(setDoc(doc(db(A), `chats/${CHAT}`), newChat()));
  });
  it("allows the app's merge-set on an existing chat (re-open)", async () => {
    await seed(true);
    await assertSucceeds(setDoc(doc(db(A), `chats/${CHAT}`), newChat(), { mergeFields: ["participants", "participantNames"] }));
  });
  it("rejects a bad id, unsorted or third participants", async () => {
    await assertFails(setDoc(doc(db(A), `chats/${B}_${A}`), newChat()));
    await assertFails(setDoc(doc(db(A), `chats/whatever`), newChat()));
    await assertFails(setDoc(doc(db(A), `chats/${CHAT}`), { ...newChat(), participants: [B, A] }));
    await assertFails(setDoc(doc(db(A), `chats/${CHAT}`), { ...newChat(), participants: [A, B, C] }));
  });
  it("rejects a chat the caller isn't in", async () => {
    await assertFails(setDoc(doc(db(C), `chats/${CHAT}`), newChat()));
  });
  it("rejects spoofed participant names", async () => {
    await assertFails(setDoc(doc(db(A), `chats/${CHAT}`), { ...newChat(), participantNames: { [A]: "Alice", [B]: "Bank Support" } }));
  });
  it("rejects extra fields", async () => {
    await assertFails(setDoc(doc(db(A), `chats/${CHAT}`), { ...newChat(), pinned: true }));
  });
  it("rejects unverified users", async () => {
    await assertFails(setDoc(doc(db(A, false), `chats/${CHAT}`), newChat()));
  });
});

describe("chat read", () => {
  beforeEach(() => seed(true));

  it("lets a verified participant read and list", async () => {
    await assertSucceeds(getDoc(doc(db(A), `chats/${CHAT}`)));
    await assertSucceeds(getDocs(query(collection(db(A), "chats"), where("participants", "array-contains", A))));
  });
  it("blocks outsiders and unverified users", async () => {
    await assertFails(getDoc(doc(db(C), `chats/${CHAT}`)));
    await assertFails(getDoc(doc(db(A, false), `chats/${CHAT}`)));
    await assertFails(getDoc(doc(db(A, false), `chats/${CHAT}/messages/m1`)));
    await assertFails(getDoc(doc(db(C), `chats/${CHAT}/messages/m1`)));
  });
});

describe("chat update", () => {
  beforeEach(() => seed(true));
  const chat = (uid = A) => doc(db(uid), `chats/${CHAT}`);

  it("archives and mutes only under your own key", async () => {
    await assertSucceeds(updateDoc(chat(), { [`archived.${A}`]: true }));
    await assertSucceeds(updateDoc(chat(), { [`muted.${A}`]: true }));
    await assertFails(updateDoc(chat(), { [`archived.${B}`]: true }));
    await assertFails(updateDoc(chat(), { [`muted.${B}`]: true }));
    await assertFails(updateDoc(chat(), { [`archived.${A}`]: "yes" }));
  });
  it("toggles pin with a boolean only", async () => {
    await assertSucceeds(updateDoc(chat(), { pinned: true }));
    await assertFails(updateDoc(chat(), { pinned: "x" }));
  });
  it("lets you reset only your own unread counter", async () => {
    await assertSucceeds(updateDoc(chat(A), { [`unreadCount.${A}`]: 0 }));
    await assertFails(updateDoc(chat(A), { [`unreadCount.${A}`]: 5 }));
    await assertFails(updateDoc(chat(A), { [`unreadCount.${B}`]: 0 }));
  });
  it("lets you bump only the other person's counter by one", async () => {
    await assertSucceeds(updateDoc(chat(A), { [`unreadCount.${B}`]: increment(1) }));
    await assertFails(updateDoc(chat(A), { [`unreadCount.${B}`]: increment(5) }));
    await assertFails(updateDoc(chat(A), { [`unreadCount.${A}`]: increment(1) }));
  });
  it("never changes participants, and refreshes names only to the real display name", async () => {
    await assertFails(updateDoc(chat(), { participants: [A, C] }));
    await assertSucceeds(updateDoc(chat(), { [`participantNames.${B}`]: "Bob" }));
    await assertFails(updateDoc(chat(), { [`participantNames.${B}`]: "Bank Support" }));
  });
  it("accepts a renamed display name once the profile has it", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc(`users/${B}`).update({ displayName: "Robert" });
    });
    await assertSucceeds(updateDoc(chat(), { [`participantNames.${B}`]: "Robert" }));
  });
  it("rejects unknown fields and bad previews", async () => {
    await assertFails(updateDoc(chat(), { evil: 1 }));
    await assertFails(updateDoc(chat(), { lastSenderId: C }));
    await assertFails(updateDoc(chat(), { lastMessage: "x".repeat(9001) }));
  });
  it("blocks outsiders, unverified users and deletes", async () => {
    await assertFails(updateDoc(chat(C), { pinned: true }));
    await assertFails(updateDoc(doc(db(A, false), `chats/${CHAT}`), { pinned: true }));
    await assertFails(deleteDoc(chat()));
  });
});

describe("messages", () => {
  beforeEach(() => seed(true));

  it("sends like the app does (message + chat metadata)", async () => {
    await assertSucceeds(sendBatch(db(A), A, "ciphertext=="));
    await assertSucceeds(sendBatch(db(A), A, "again==", {}, "new2")); // consecutive sends by one person
  });
  it("increments the other person's unread counter when sending", async () => {
    await sendBatch(db(A), A, "ciphertext==");
    await env.withSecurityRulesDisabled(async (ctx) => {
      const d = (await ctx.firestore().doc(`chats/${CHAT}`).get()).data()!;
      expect(d.unreadCount[B]).toBe(2);
      expect(d.unreadCount[A]).toBe(2);
    });
  });
  it("rejects a dotted literal field (the old bug) instead of silently storing it", async () => {
    const f = db(A);
    const b = writeBatch(f);
    b.set(doc(f, `chats/${CHAT}`), { lastMessage: "x", [`unreadCount.${B}`]: increment(1) }, { merge: true });
    await assertFails(b.commit());
  });
  it("sends a reply with its quote", async () => {
    await assertSucceeds(
      sendBatch(db(A), A, "body==", { replyToId: "m1", replyToText: "quote==", replyToSender: B }),
    );
  });
  it("rejects someone else's senderId, extra fields, client timestamps, pre-filled reactions", async () => {
    await assertFails(sendBatch(db(A), A, "x", { senderId: B }));
    await assertFails(sendBatch(db(A), A, "x", { extra: 1 }));
    await assertFails(sendBatch(db(A), A, "x", { timestamp: new Date() }));
    await assertFails(sendBatch(db(A), A, "x", { reactions: { [A]: "👍" } }));
  });
  it("rejects empty and oversized bodies", async () => {
    await assertFails(sendBatch(db(A), A, "", { text: "" }));
    await assertFails(sendBatch(db(A), A, "x", { text: "x".repeat(9001) }));
  });
  it("rejects outsiders and unverified senders", async () => {
    await assertFails(setDoc(doc(db(C), `chats/${CHAT}/messages/evil`), { text: "x", senderId: C, timestamp: serverTimestamp() }));
    await assertFails(sendBatch(db(A, false), A, "x"));
  });
  it("never lets text be edited", async () => {
    await assertFails(updateDoc(doc(db(B), `chats/${CHAT}/messages/m1`), { text: "edited" }));
    await assertFails(updateDoc(doc(db(A), `chats/${CHAT}/messages/m1`), { senderId: A }));
  });
  it("lets you set and clear only your own reaction", async () => {
    const m = (uid: string) => doc(db(uid), `chats/${CHAT}/messages/m1`);
    await assertSucceeds(updateDoc(m(A), { [`reactions.${A}`]: "👍" }));
    await assertFails(updateDoc(m(A), { [`reactions.${B}`]: "👍" }));
    await assertFails(updateDoc(m(A), { [`reactions.${A}`]: "x".repeat(17) }));
    await assertSucceeds(updateDoc(m(A), { [`reactions.${A}`]: deleteField() }));
    await assertFails(updateDoc(doc(db(C), `chats/${CHAT}/messages/m1`), { [`reactions.${C}`]: "👍" }));
  });
  it("lets only the sender delete", async () => {
    await assertFails(deleteDoc(doc(db(A), `chats/${CHAT}/messages/m1`))); // m1 is Bob's
    await assertSucceeds(deleteDoc(doc(db(A), `chats/${CHAT}/messages/m2`)));
    await assertFails(deleteDoc(doc(db(C), `chats/${CHAT}/messages/m2`)));
  });
});

describe("users and usernames", () => {
  beforeEach(() => seed(false));

  it("lets you edit only your own name and bio", async () => {
    await assertSucceeds(updateDoc(doc(db(A), `users/${A}`), { displayName: "Alice B", bio: "hi", photoUrl: "" }));
    await assertFails(updateDoc(doc(db(A), `users/${B}`), { displayName: "Hacked" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { username: "someoneelse" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { displayName: "" }));
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { displayName: "x".repeat(51) }));
  });
  it("never stores an email on the public profile", async () => {
    await assertFails(updateDoc(doc(db(A), `users/${A}`), { emailLower: "a@x.com" }));
    const f = db("dave1");
    const b = writeBatch(f);
    b.set(doc(f, "users/dave1"), { uid: "dave1", username: "dave1", displayName: "Dave", photoUrl: "", bio: "", emailLower: "d@x.com" });
    b.set(doc(f, "usernames/dave1"), { uid: "dave1" });
    await assertFails(b.commit());
  });
  it("creates a profile together with its username claim", async () => {
    const f = db("dave1");
    const b = writeBatch(f);
    b.set(doc(f, "users/dave1"), { uid: "dave1", username: "dave1", displayName: "Dave", photoUrl: "", bio: "", createdAt: serverTimestamp() });
    b.set(doc(f, "usernames/dave1"), { uid: "dave1" });
    await assertSucceeds(b.commit());
  });
  it("rejects taking a username someone already owns", async () => {
    const f = db("dave1");
    const b = writeBatch(f);
    b.set(doc(f, "users/dave1"), { uid: "dave1", username: A, displayName: "Dave", photoUrl: "", bio: "", createdAt: serverTimestamp() });
    b.set(doc(f, `usernames/${A}`), { uid: "dave1" });
    await assertFails(b.commit());
  });
  it("never lets a username or profile be deleted", async () => {
    await assertFails(deleteDoc(doc(db(A), `usernames/${A}`)));
    await assertFails(deleteDoc(doc(db(A), `users/${A}`)));
  });
});

describe("public keys and push tokens", () => {
  beforeEach(() => seed(false));

  it("lets you publish only your own key, with a sane size", async () => {
    await assertSucceeds(setDoc(doc(db(A), `publicKeys/${A}`), { publicKey: "MFkw==", updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db(A), `publicKeys/${B}`), { publicKey: "MFkw==", updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(db(A), `publicKeys/${A}`), { publicKey: "x".repeat(1001), updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(db(A), `publicKeys/${A}`)));
    await assertSucceeds(getDoc(doc(db(B), `publicKeys/${A}`)));
  });
  it("keeps FCM tokens owner-only", async () => {
    const tok = { createdAt: serverTimestamp(), platform: "android" };
    await assertSucceeds(setDoc(doc(db(A), `users/${A}/fcmTokens/tok1`), tok));
    await assertFails(setDoc(doc(db(B), `users/${A}/fcmTokens/tok2`), tok));
    await assertFails(getDoc(doc(db(B), `users/${A}/fcmTokens/tok1`)));
    await assertFails(setDoc(doc(db(A), `users/${A}/fcmTokens/tok3`), { ...tok, extra: 1 }));
  });
});
