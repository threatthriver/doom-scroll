package com.securemessage.app.di

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.securemessage.app.data.crypto.ChatSessionManager
import com.securemessage.app.data.crypto.KeyStoreManager
import com.securemessage.app.data.firebase.FirebaseAuthRepository
import com.securemessage.app.data.firebase.FirestoreChatRepository
import com.securemessage.app.data.firebase.FirestoreUserRepository
import com.securemessage.app.data.repo.AuthRepository
import com.securemessage.app.data.repo.ChatRepository
import com.securemessage.app.data.repo.UserRepository

class AppContainer(private val appContext: Context) {
    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }
    val firestore: FirebaseFirestore by lazy { Firebase.firestore }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(firebaseAuth) }
    val userRepository: UserRepository by lazy { FirestoreUserRepository(firestore) }
    val chatRepository: ChatRepository by lazy { FirestoreChatRepository(firestore) }

    val chatSessionManager: ChatSessionManager by lazy {
        ChatSessionManager(appContext, firestore)
    }

    /**
     * Publishes this device's identity public key to Firestore so the other participant
     * in any chat can derive the shared ECDH secret and decrypt messages.
     *
     * Must be called once the user is authenticated (the key document is owned by the uid).
     * Safe to call repeatedly: the key is stable in the Android Keystore and the write is
     * idempotent. Failures are swallowed so a transient network error never blocks the UI;
     * chats simply fall back to showing "Not encrypted" until a key is available.
     */
    suspend fun publishMyPublicKey() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        runCatching {
            val publicKey = KeyStoreManager.getIdentityPublicKey(appContext)
            chatSessionManager.publishPublicKey(uid, publicKey)
        }
    }
}
