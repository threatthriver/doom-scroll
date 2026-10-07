package com.securemessage.app.ui.chat

import com.google.firebase.Timestamp
import com.securemessage.app.data.model.Message
import com.securemessage.app.ui.common.formatDayLabel
import java.util.Calendar
import java.util.Date

/** One line of the chat list: a message, plus the day divider that belongs above it (if any). */
data class ChatRow(
    val message: Message,
    /** Non-null when this is the first message of its calendar day. */
    val dayLabel: String?,
)

/**
 * Turns the messages (oldest first) into rows for a list that is drawn bottom-up, so the result is
 * NEWEST FIRST. Doing this once per change of the message list, instead of on every recomposition
 * of every bubble, matters: comparing days needs Calendar objects, which are not cheap.
 */
fun buildChatRows(oldestFirst: List<Message>, now: Date = Date()): List<ChatRow> {
    val cal = Calendar.getInstance()
    var previousDay = Int.MIN_VALUE
    val rows = ArrayList<ChatRow>(oldestFirst.size)
    for (m in oldestFirst) {
        val day = dayKey(m.timestamp, cal)
        val label = if (day != previousDay) formatDayLabel(m.timestamp, now) else null
        rows += ChatRow(m, label)
        previousDay = day
    }
    rows.reverse()
    return rows
}

/** A number that is equal for all timestamps on the same calendar day (device time zone). */
internal fun dayKey(ts: Timestamp?, cal: Calendar): Int {
    if (ts == null) return Int.MAX_VALUE // messages still being written count as "now"
    cal.time = ts.toDate()
    return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
}
