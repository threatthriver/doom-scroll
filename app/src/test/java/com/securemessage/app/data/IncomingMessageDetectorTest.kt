package com.securemessage.app.data

import com.google.firebase.Timestamp
import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.notify.IncomingAlert
import com.securemessage.app.data.notify.IncomingMessageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class IncomingMessageDetectorTest {
    private val me = "me"
    private val bob = "bob"
    private val start = 1_800_000_000_000L

    private fun ts(ms: Long) = Timestamp(Date(ms))
    private fun chat(
        id: String = "c1",
        at: Long? = start,
        sender: String = bob,
        text: String = "cipher==",
        muted: Boolean = false,
    ) = Chat(
        id = id,
        participants = listOf(me, bob),
        participantNames = mapOf(me to "Me", bob to "Bob"),
        lastMessage = text,
        lastMessageAt = at?.let { ts(it) },
        lastSenderId = sender,
        muted = if (muted) mapOf(me to true) else emptyMap(),
    )

    private fun detector() = IncomingMessageDetector(me, startedAtMs = start)

    @Test fun firstSnapshotIsOnlyTheBaseline() {
        assertTrue(detector().onSnapshot(listOf(chat()), null).isEmpty())
    }

    @Test fun newMessageInKnownChatAlerts() {
        val d = detector()
        d.onSnapshot(listOf(chat(at = start)), null)
        assertEquals(listOf(IncomingAlert("c1", "Bob")), d.onSnapshot(listOf(chat(at = start + 1000)), null))
    }

    @Test fun sameSnapshotTwiceAlertsOnce() {
        val d = detector()
        d.onSnapshot(emptyList(), null)
        assertEquals(1, d.onSnapshot(listOf(chat(at = start + 1000)), null).size)
        assertTrue(d.onSnapshot(listOf(chat(at = start + 1000)), null).isEmpty())
    }

    /** The old watcher baselined every chat it had never seen, so a first message never alerted. */
    @Test fun firstMessageInABrandNewChatAlerts() {
        val d = detector()
        d.onSnapshot(emptyList(), null)
        assertEquals(listOf(IncomingAlert("new", "Bob")), d.onSnapshot(listOf(chat(id = "new", at = start + 5000)), null))
    }

    @Test fun oldChatsAppearingLaterDoNotAlert() {
        // First snapshot came from an empty cache; the server then sends existing old chats.
        val d = detector()
        d.onSnapshot(emptyList(), null)
        assertTrue(d.onSnapshot(listOf(chat(id = "old", at = start - 24 * 3600_000L)), null).isEmpty())
    }

    @Test fun skipsOwnMessagesMutedOpenAndEmpty() {
        val d = detector()
        d.onSnapshot(listOf(chat(id = "a"), chat(id = "b"), chat(id = "c"), chat(id = "e")), null)
        val later = start + 1000
        val out = d.onSnapshot(
            listOf(
                chat(id = "a", at = later, sender = me),
                chat(id = "b", at = later, muted = true),
                chat(id = "c", at = later),
                chat(id = "e", at = later, text = ""),
            ),
            openChatId = "c",
        )
        assertTrue(out.isEmpty())
    }

    @Test fun missingNameFallsBack() {
        val d = detector()
        d.onSnapshot(emptyList(), null)
        val c = chat(at = start + 1000).copy(participantNames = emptyMap())
        assertEquals("New message", d.onSnapshot(listOf(c), null).single().senderName)
    }
}
