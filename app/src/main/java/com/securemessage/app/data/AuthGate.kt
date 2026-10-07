package com.securemessage.app.data

enum class AuthDestination { SIGN_IN, VERIFY_EMAIL, COMPLETE_PROFILE, CONVERSATIONS }

/** Single source of truth for where a user may go. Chats require a verified email. */
object AuthGate {
    fun route(signedIn: Boolean, emailVerified: Boolean, hasProfile: Boolean): AuthDestination = when {
        !signedIn -> AuthDestination.SIGN_IN
        !emailVerified -> AuthDestination.VERIFY_EMAIL
        !hasProfile -> AuthDestination.COMPLETE_PROFILE
        else -> AuthDestination.CONVERSATIONS
    }

    fun canAccessChats(signedIn: Boolean, emailVerified: Boolean): Boolean = signedIn && emailVerified
}
