package com.securemessage.app.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.securemessage.app.data.repo.PushTokenRepository
import kotlinx.coroutines.tasks.await

class FcmTokenRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: () -> FirebaseMessaging,
) : PushTokenRepository {

    private fun tokenDoc(uid: String, token: String) =
        db.collection("users").document(uid).collection("fcmTokens").document(token)

    override suspend fun registerCurrentToken(): Result<Unit> = runCatching {
        register(messaging().token.await()).getOrThrow()
    }.onFailure { Log.w(TAG, "registerCurrentToken failed: ${it.javaClass.simpleName}") }

    override suspend fun register(token: String): Result<Unit> = runCatching {
        // No signed-in user means there is nothing to register against. Fail explicitly so callers
        // don't treat a no-op as a successful registration (which previously hid the fact that no
        // token was ever written).
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        require(token.isNotBlank() && '/' !in token) { "Invalid token" }
        tokenDoc(uid, token).set(
            mapOf("createdAt" to FieldValue.serverTimestamp(), "platform" to "android"),
        ).await()
        Unit
    }.onFailure { Log.w(TAG, "register failed: ${it.javaClass.simpleName}") }

    override suspend fun unregister(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid
        val fcm = messaging()
        // Delete the server-side token FIRST (this needs auth), so a signed-out device stops
        // receiving pushes. Only then rotate the device token locally. If there's no uid we can't
        // touch Firestore — log it rather than silently leaving a stale token that keeps alerting.
        if (uid != null) {
            val token = runCatching { fcm.token.await() }.getOrNull()
            if (token != null) tokenDoc(uid, token).delete().await()
        } else {
            Log.w(TAG, "unregister with no signed-in user: server token may remain until overwritten")
        }
        fcm.deleteToken().await()
        Unit
    }.onFailure { Log.w(TAG, "unregister failed: ${it.javaClass.simpleName}") }

    private companion object {
        const val TAG = "PushTokens"
    }
}
