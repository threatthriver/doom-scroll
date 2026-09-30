package com.securemessage.app.ui.nav

object Routes {
    const val SIGN_IN = "signIn"
    const val SIGN_UP = "signUp"
    const val COMPLETE_PROFILE = "completeProfile?reason={reason}"
    const val CONVERSATIONS = "conversations"
    const val USERS = "users"
    const val CHAT = "chat/{chatId}"

    fun completeProfile(reason: String? = null) =
        if (reason == null) "completeProfile" else "completeProfile?reason=$reason"

    fun chat(chatId: String) = "chat/$chatId"
}
