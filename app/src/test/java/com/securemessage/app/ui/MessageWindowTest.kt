package com.securemessage.app.ui

import com.google.firebase.Timestamp
import com.securemessage.app.data.model.Message
import com.securemessage.app.ui.chat.MessageWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class MessageWindowTest {
    private fun m(id: String, atSec: Long?) =
        Message(id = id, text = id, senderId = "x", timestamp = atSec?.let { Timestamp(Date(it * 1000)) })

    private fun ids(l: List<Message>) = l.map { it.id }

    @Test fun keepsOlderPagesWhenANewMessageArrives() {
        val loadedOlder = listOf(m("a", 1), m("b", 2))
        val existing = loadedOlder + listOf(m("c", 10), m("d", 11))
        val snapshot = listOf(m("c", 10), m("d", 11), m("e", 12))
        assertEquals(listOf("a", "b", "c", "d", "e"), ids(MessageWindow.merge(existing, snapshot)))
    }

    @Test fun dropsMessagesDeletedFromTheLiveWindow() {
        val existing = listOf(m("c", 10), m("d", 11))
        assertEquals(listOf("c"), ids(MessageWindow.merge(existing, listOf(m("c", 10)))))
    }

    @Test fun emptySnapshotClearsEverything() {
        assertEquals(emptyList<String>(), ids(MessageWindow.merge(listOf(m("a", 1)), emptyList())))
    }

    @Test fun snapshotWithoutATimestampIsTakenAsIs() {
        val snap = listOf(m("p", null), m("q", 5))
        assertEquals(listOf("p", "q"), ids(MessageWindow.merge(listOf(m("a", 1)), snap)))
    }

    @Test fun prependSkipsDuplicatesAndKeepsOrder() {
        val existing = listOf(m("c", 10), m("d", 11))
        val page = listOf(m("a", 1), m("b", 2), m("c", 10))
        assertEquals(listOf("a", "b", "c", "d"), ids(MessageWindow.prepend(existing, page)))
    }
}
