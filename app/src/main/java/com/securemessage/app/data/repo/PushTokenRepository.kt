package com.securemessage.app.data.repo

/** Stores this device's FCM token under the owner-only path users/{uid}/fcmTokens/{token}. */
interface PushTokenRepository {
    /** Fetches the current FCM token and stores it for the signed-in user. */
    suspend fun registerCurrentToken(): Result<Unit>
    /** Stores [token] for the signed-in user (no-op when signed out). */
    suspend fun register(token: String): Result<Unit>
    /** Deletes this device's token doc and the local token. Must run while still signed in. */
    suspend fun unregister(): Result<Unit>
}

/** Removes the push token first (rules need auth), then signs out. Sign-out happens even if cleanup fails. */
suspend fun signOutEverywhere(auth: AuthRepository, push: PushTokenRepository) {
    // Bounded: an offline device must still sign out instead of hanging on token cleanup.
    kotlinx.coroutines.withTimeoutOrNull(SIGN_OUT_CLEANUP_TIMEOUT_MS) { push.unregister() }
    auth.signOut()
}

private const val SIGN_OUT_CLEANUP_TIMEOUT_MS = 4_000L
