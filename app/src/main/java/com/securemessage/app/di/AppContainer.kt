package com.securemessage.app.di

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.securemessage.app.data.crypto.ChatSessionManager
import com.securemessage.app.data.crypto.AndroidIdentityKeys
import com.securemessage.app.data.crypto.FirestorePublicKeyStore
import com.securemessage.app.data.crypto.SafetyNumberStore
import com.securemessage.app.data.crypto.SharedPrefsSafetyNumberStore
import com.securemessage.app.data.firebase.FcmTokenRepository
import com.securemessage.app.data.firebase.FirebaseAuthRepository
import com.securemessage.app.data.firebase.FirestoreChatRepository
import com.securemessage.app.data.firebase.FirestoreUserRepository
import com.securemessage.app.data.push.DurablePushNotifier
import com.securemessage.app.data.push.NoopPushNotifier
import com.securemessage.app.data.push.PushNotifier
import com.securemessage.app.data.push.RelayPushNotifier
import com.securemessage.app.data.push.firebaseIdToken
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.PushTokenRepository
import com.securemessage.app.data.repo.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(private val appContext: Context) {
    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }

    /** Process-wide scope for work that must outlive a screen (FCM token refresh, message watcher). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(firebaseAuth) }
    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }
    /** The raw relay client (null-object when no endpoint is configured). */
    val relayPushNotifier: PushNotifier by lazy {
        val endpoint = com.securemessage.app.BuildConfig.PUSH_ENDPOINT
        if (endpoint.isBlank()) NoopPushNotifier else RelayPushNotifier(endpoint, firebaseIdToken(firebaseAuth))
    }
    val pushNotifier: PushNotifier by lazy {
        if (com.securemessage.app.BuildConfig.PUSH_ENDPOINT.isBlank()) NoopPushNotifier
        else DurablePushNotifier(appContext, relayPushNotifier)
    }

    val chatRepository: ChatRepository by lazy { FirestoreChatRepository(firestore, pushNotifier, appScope) }

    val pushTokenRepository: PushTokenRepository by lazy {
        FcmTokenRepository(firebaseAuth, firestore) { FirebaseMessaging.getInstance() }
    }

    val chatSessionManager: ChatSessionManager by lazy {
        ChatSessionManager(AndroidIdentityKeys(appContext), FirestorePublicKeyStore(firestore))
    }

    val safetyNumbers: SafetyNumberStore by lazy { SharedPrefsSafetyNumberStore(appContext) }

    /**
     * Publishes this device's identity public key to Firestore so the other participant
     * in any chat can derive the shared ECDH secret and decrypt messages.
     *
     * Must be called once the user is authenticated (the key document is owned by the uid).
     * Safe to call repeatedly: the key is stable in the Android Keystore and the write is
     * idempotent. Failures are swallowed so a transient network error never blocks the UI;
     * chats stay locked ("can't send yet") until a key is available.
     */
    suspend fun publishMyPublicKey() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching { chatSessionManager.publishMyKey(uid) }
    }
}
