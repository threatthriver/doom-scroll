package com.securemessage.app.ui

import com.google.firebase.Timestamp
import com.securemessage.app.data.model.Message
import com.securemessage.app.ui.chat.buildChatRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Date

class ChatRowsTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Timestamp {
        val c = Calendar.getInstance().apply { clear(); set(year, month, day, hour, 0, 0) }
        return Timestamp(c.time)
    }

    private fun m(id: String, ts: Timestamp?) = Message(id = id, text = id, senderId = "x", timestamp = ts)

    private val now: Date = Calendar.getInstance().apply { clear(); set(2026, Calendar.OCTOBER, 4, 15, 0, 0) }.time

    @Test fun isNewestFirstForABottomUpList() {
        val rows = buildChatRows(listOf(m("a", at(2026, Calendar.OCTOBER, 4, 9)), m("b", at(2026, Calendar.OCTOBER, 4, 10))), now)
        assertEquals(listOf("b", "a"), rows.map { it.message.id })
    }

    @Test fun onlyTheFirstMessageOfEachDayGetsADivider() {
        val rows = buildChatRows(
            listOf(
                m("a", at(2026, Calendar.OCTOBER, 2, 9)),
                m("b", at(2026, Calendar.OCTOBER, 2, 18)),
                m("c", at(2026, Calendar.OCTOBER, 3, 8)),
                m("d", at(2026, Calendar.OCTOBER, 4, 8)),
                m("e", at(2026, Calendar.OCTOBER, 4, 14)),
            ),
            now,
        )
        val labels = rows.associate { it.message.id to it.dayLabel }
        assertEquals("October 2", labels["a"])
        assertNull(labels["b"])
        assertEquals("Yesterday", labels["c"])
        assertEquals("Today", labels["d"])
        assertNull(labels["e"])
    }

    @Test fun aMessageStillBeingWrittenJoinsTodayWithoutItsOwnDivider() {
        val rows = buildChatRows(listOf(m("a", at(2026, Calendar.OCTOBER, 4, 9)), m("pending", null)), now)
        assertEquals(listOf("pending", "a"), rows.map { it.message.id })
        // pending has no timestamp yet, so it is treated as a different "day" and gets a label.
        assertEquals("Today", rows.first { it.message.id == "pending" }.dayLabel)
    }

    @Test fun emptyListGivesNoRows() {
        assertEquals(0, buildChatRows(emptyList(), now).size)
    }
}
