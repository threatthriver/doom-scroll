import { initializeApp } from "firebase-admin/app";
import { DocumentReference, getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { buildData, isInvalidTokenError, recipients, senderName, unmuted, validateMessage } from "./notify";

initializeApp();

const MAX_TOKENS_PER_USER = 20;

/**
 * Sends a push to the other participant(s) when a message is created.
 * Logs only ids and counts: never message text or tokens.
 */
export const notifyOnMessage = onDocumentCreated(
  { document: "chats/{chatId}/messages/{messageId}", region: "us-central1", memory: "256MiB", maxInstances: 10 },
  async (event) => {
    const chatId = event.params.chatId;
    const msg = validateMessage(chatId, event.data?.data());
    if (!msg) {
      logger.warn("Skipping malformed message", { chatId });
      return;
    }

    const db = getFirestore();
    const chat = await db.doc(`chats/${chatId}`).get();
    const participants = chat.get("participants");
    if (!chat.exists || !Array.isArray(participants) || !participants.includes(msg.senderId)) {
      logger.warn("Sender not a participant or chat missing", { chatId });
      return;
    }

    const to = unmuted(recipients(participants, msg.senderId), chat.get("muted"));
    if (to.length === 0) return;
    const name = senderName(chat.get("participantNames"), msg.senderId);

    let sent = 0;
    let pruned = 0;
    for (const uid of to) {
      const snap = await db.collection(`users/${uid}/fcmTokens`).limit(MAX_TOKENS_PER_USER).get();
      const tokens = snap.docs.map((d) => d.id);
      if (tokens.length === 0) continue;
      const data = buildData(name, chatId, uid);

      const res = await getMessaging().sendEachForMulticast({
        tokens,
        data,
        android: { priority: "high" },
      });
      sent += res.successCount;

      const stale = res.responses
        .map((r, i) => (!r.success && isInvalidTokenError(r.error?.code) ? snap.docs[i].ref : null))
        .filter((ref): ref is DocumentReference => ref !== null);
      await Promise.all(stale.map((ref) => ref.delete()));
      pruned += stale.length;
    }
    logger.info("Message push done", { chatId, recipients: to.length, sent, pruned });
  },
);
