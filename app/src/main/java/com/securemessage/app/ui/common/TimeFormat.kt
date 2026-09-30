package com.securemessage.app.ui.common

import com.google.firebase.Timestamp
import java.text.DateFormat

fun formatTime(ts: Timestamp?): String =
    ts?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it.toDate()) } ?: ""
