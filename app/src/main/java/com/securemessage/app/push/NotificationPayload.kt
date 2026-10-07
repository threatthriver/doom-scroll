package com.securemessage.app.push

/**
 * Validated contents of a data-only FCM "message" push. It never carries message text, only who
 * wrote in which chat and which account the push is meant for.
 */
data class NotificationPayload(val chatId: String, val senderName: String, val recipientUid: String) {
    companion object {
        val CHAT_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,128}$")
        val UID_REGEX = Regex("^[A-Za-z0-9]{1,128}$")
        const val MAX_NAME = 60
        const val FALLBACK_TITLE = "New message"

        fun isValidChatId(id: String?): Boolean = id != null && CHAT_ID_REGEX.matches(id)

        /** Returns null for anything that isn't a well-formed message push. */
        fun parse(data: Map<String, String>): NotificationPayload? {
            if (data["type"] != "message") return null
            val chatId = data["chatId"]?.takeIf(::isValidChatId) ?: return null
            val recipient = data["recipientUid"]?.takeIf { UID_REGEX.matches(it) } ?: return null
            val name = data["senderName"]?.trim().orEmpty().ifEmpty { FALLBACK_TITLE }
            return NotificationPayload(chatId = chatId, senderName = truncate(name, MAX_NAME), recipientUid = recipient)
        }

        fun truncate(s: String, max: Int): String = if (s.length <= max) s else s.take(max - 1) + "…"

        /**
         * Show only when the push is for the signed-in account (a device that switched accounts
         * must not surface the previous user's pushes) and that chat isn't on screen.
         */
        fun shouldShow(payload: NotificationPayload, currentUid: String?, activeChatId: String?): Boolean =
            currentUid != null && payload.recipientUid == currentUid && payload.chatId != activeChatId
    }
}
