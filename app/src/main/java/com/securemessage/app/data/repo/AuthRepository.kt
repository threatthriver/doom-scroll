package com.securemessage.app.data.repo

/** A Google sign-in hit an existing account for the same email that can't be linked automatically. */
class AccountCollisionException : Exception("An account with this email already exists")

/** Firebase throttled the request (e.g. too many verification emails). */
class TooManyRequestsException : Exception("Too many requests")

interface AuthRepository {
    val currentUserId: String?
    val currentEmail: String?
    /** Cached flag from the signed-in user; call [reloadUser] to refresh it. */
    val isEmailVerified: Boolean
    suspend fun signUp(email: String, password: String): Result<String>
    suspend fun signIn(email: String, password: String): Result<String>
    /** Signs in with a Google ID token. Fails with [AccountCollisionException] on an unlinkable email clash. */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<String>
    /** Fails with [TooManyRequestsException] when rate limited. */
    suspend fun sendEmailVerification(): Result<Unit>
    /** Reloads the user and refreshes the ID token; returns the up-to-date verified flag. */
    suspend fun reloadUser(): Result<Boolean>
    fun signOut()
}
