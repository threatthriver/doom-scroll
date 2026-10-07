package com.securemessage.app.data.crypto

import android.content.Context

/**
 * Remembers the safety number you last accepted for each chat (trust on first use), so the app
 * can warn you when it changes: that happens when the other person reinstalls or switches phones,
 * and also if someone were tampering with the key exchange.
 */
interface SafetyNumberStore {
    fun get(chatId: String): String?
    fun set(chatId: String, number: String)
}

class SharedPrefsSafetyNumberStore(context: Context) : SafetyNumberStore {
    private val prefs = context.applicationContext.getSharedPreferences("safety_numbers", Context.MODE_PRIVATE)

    override fun get(chatId: String): String? = prefs.getString(chatId, null)

    override fun set(chatId: String, number: String) {
        prefs.edit().putString(chatId, number).apply()
    }
}
