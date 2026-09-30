package com.securemessage.app.data

val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")

fun normalizeUsername(s: String): String = s.trim().lowercase()
