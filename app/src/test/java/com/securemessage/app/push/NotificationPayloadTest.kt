package com.securemessage.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPayloadTest {
    private val valid = mapOf("type" to "message", "chatId" to "a_b-1", "senderName" to " Bob ", "recipientUid" to "me1")

    @Test fun parsesValid() {
        assertEquals(NotificationPayload("a_b-1", "Bob", "me1"), NotificationPayload.parse(valid))
    }

    @Test fun ignoresAnyTextField() {
        // Even if an old/rogue sender includes text, the parsed payload has nowhere to carry it.
        val p = NotificationPayload.parse(valid + ("preview" to "secret") + ("text" to "secret"))!!
        assertFalse(p.toString().contains("secret"))
    }

    @Test fun rejectsWrongTypeOrBadChatId() {
        assertNull(NotificationPayload.parse(valid + ("type" to "other")))
        assertNull(NotificationPayload.parse(valid - "chatId"))
        assertNull(NotificationPayload.parse(valid + ("chatId" to "../x")))
        assertNull(NotificationPayload.parse(valid + ("chatId" to "a".repeat(129))))
        assertNull(NotificationPayload.parse(valid + ("chatId" to "")))
    }

    @Test fun rejectsMissingOrMalformedRecipient() {
        assertNull(NotificationPayload.parse(valid - "recipientUid"))
        assertNull(NotificationPayload.parse(valid + ("recipientUid" to "")))
        assertNull(NotificationPayload.parse(valid + ("recipientUid" to "a/b")))
    }

    @Test fun fallsBackAndTruncatesName() {
        val p = NotificationPayload.parse(valid + ("senderName" to "  "))!!
        assertEquals(NotificationPayload.FALLBACK_TITLE, p.senderName)
        assertEquals(NotificationPayload.MAX_NAME, NotificationPayload.parse(valid + ("senderName" to "n".repeat(80)))!!.senderName.length)
    }

    @Test fun shownOnlyForMatchingRecipientAndInactiveChat() {
        val p = NotificationPayload("c1", "Bob", "me1")
        assertTrue(NotificationPayload.shouldShow(p, "me1", null))
        assertTrue(NotificationPayload.shouldShow(p, "me1", "c2"))
        assertFalse(NotificationPayload.shouldShow(p, "me1", "c1"))
        assertFalse(NotificationPayload.shouldShow(p, "other", null))
        assertFalse(NotificationPayload.shouldShow(p, null, null))
    }
}
