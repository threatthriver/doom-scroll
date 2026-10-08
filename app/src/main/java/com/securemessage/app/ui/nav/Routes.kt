package com.securemessage.app.ui.nav

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val SIGN_IN = "signIn"
    const val SIGN_UP = "signUp"
    const val COMPLETE_PROFILE = "completeProfile?reason={reason}"
    const val VERIFY_EMAIL = "verifyEmail?sent={sent}"
    const val CONVERSATIONS = "conversations"
    const val USERS = "users"
    const val CHAT = "chat/{chatId}"
    const val MOMENT = "moment/{momentId}"
    const val SESSION = "session/{sessionId}"
    const val NEARBY = "nearby"
    const val CIRCLES = "circles"
    const val CREATE_EVENT = "createEvent"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"

    fun completeProfile(reason: String? = null) =
        if (reason == null) "completeProfile" else "completeProfile?reason=$reason"

    fun verifyEmail(sent: Boolean = false) = "verifyEmail?sent=$sent"

    fun chat(chatId: String) = "chat/$chatId"
    fun moment(id: String) = "moment/$id"
    fun session(id: String) = "session/$id"
}
