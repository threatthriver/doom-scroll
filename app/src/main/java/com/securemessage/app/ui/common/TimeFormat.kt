package com.securemessage.app.ui.common

import com.google.firebase.Timestamp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatTime(ts: Timestamp?): String =
    ts?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it.toDate()) } ?: ""


/** True when both timestamps fall on the same calendar day in the device time zone. */
fun isSameDay(a: Timestamp?, b: Timestamp?): Boolean {
    if (a == null || b == null) return a == b
    val first = Calendar.getInstance().apply { time = a.toDate() }
    val second = Calendar.getInstance().apply { time = b.toDate() }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

/**
 * Telegram-style day divider label: "Today", "Yesterday", "October 1" (current year)
 * or "October 1, 2025" (older years).
 */
fun formatDayLabel(ts: Timestamp?, now: Date = Date()): String {
    if (ts == null) return "Today"
    val target = Calendar.getInstance().apply { time = ts.toDate() }
    val today = Calendar.getInstance().apply { time = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun Calendar.sameDayAs(other: Calendar) =
        get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
            get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

    return when {
        target.sameDayAs(today) -> "Today"
        target.sameDayAs(yesterday) -> "Yesterday"
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(target.time)
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(target.time)
    }
}
