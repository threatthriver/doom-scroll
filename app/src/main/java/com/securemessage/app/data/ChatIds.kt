package com.securemessage.app.data

/** Deterministic 1:1 chat id: both users always resolve to the same document. */
fun chatIdFor(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")
