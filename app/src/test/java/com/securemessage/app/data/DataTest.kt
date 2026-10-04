package com.securemessage.app.data

import com.securemessage.app.data.model.Chat
import com.securemessage.app.data.notify.MessageNotifier
import com.securemessage.app.ui.chat.OpenChatTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIdsTest {
    @Test fun symmetric() = assertEquals(chatIdFor("b", "a"), chatIdFor("a", "b"))
    @Test fun format() = assertEquals("a_b", chatIdFor("b", "a"))
}

class ValidationTest {
    @Test fun usernames() {
        assertTrue(USERNAME_REGEX.matches("ali_99"))
        assertFalse(USERNAME_REGEX.matches("al"))
        assertFalse(USERNAME_REGEX.matches("Ali"))
        assertFalse(USERNAME_REGEX.matches("a".repeat(21)))
        assertFalse(USERNAME_REGEX.matches("a-b"))
    }

    @Test fun normalization() = assertEquals("alice", normalizeUsername("  Alice "))

    @Test fun emails() {
        assertTrue(EMAIL_REGEX.matches("bob@x.com"))
        assertFalse(EMAIL_REGEX.matches("bob@x"))
        assertFalse(EMAIL_REGEX.matches("bob"))
    }
}

class ChatSenderFieldTest {
    @Test fun defaultsToEmptyForLegacyChats() {
        assertEquals("", Chat("c1").lastSenderId)
    }

    @Test fun carriesSender() {
        assertEquals("bob", Chat("c1", lastSenderId = "bob").lastSenderId)
    }
}

class MessageNotifierIdTest {
    @Test fun stablePerChat() {
        val a = MessageNotifier.notificationIdFor("chat_1")
        assertEquals(a, MessageNotifier.notificationIdFor("chat_1"))
    }

    @Test fun distinctAcrossChats() {
        assertNotEquals(
            MessageNotifier.notificationIdFor("chat_1"),
            MessageNotifier.notificationIdFor("chat_2")
        )
    }
}

class OpenChatTrackerTest {
    @Test fun enterAndExit() {
        OpenChatTracker.entered("c1")
        assertEquals("c1", OpenChatTracker.openChatId.value)
        // Exiting a different chat must not clear the current one.
        OpenChatTracker.exited("c2")
        assertEquals("c1", OpenChatTracker.openChatId.value)
        OpenChatTracker.exited("c1")
        assertNull(OpenChatTracker.openChatId.value)
    }
}
