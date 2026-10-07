package com.securemessage.app.data

import android.content.Context

/** Local (per-device) privacy preferences. */
object PrivacySettings {
    private const val PREFS = "privacy_settings"
    private const val KEY_SCREEN_SECURITY = "screen_security"
    private const val KEY_NOTIFICATION_PREVIEW = "notification_preview"
    private const val KEY_APP_LOCK = "app_lock"

    /** When on, screenshots, screen recording and the recent-apps preview are blocked. */
    fun isScreenSecurityEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCREEN_SECURITY, false)

    fun setScreenSecurityEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCREEN_SECURITY, enabled).apply()
    }

    /** When on, notifications show the message text (decrypted on this phone). Default on. */
    fun isNotificationPreviewEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATION_PREVIEW, true)

    fun setNotificationPreviewEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFICATION_PREVIEW, enabled).apply()
    }

    /** When on, the app asks for fingerprint / face / screen lock each time it is reopened. */
    fun isAppLockEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_APP_LOCK, enabled).apply()
    }
}
