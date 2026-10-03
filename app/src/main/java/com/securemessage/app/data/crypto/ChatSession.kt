package com.securemessage.app.data.crypto

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Manages per-chat encryption sessions.
 * Each chat has a unique shared secret derived from both users' identity keys.
 */
class ChatSessionManager(
    private val context: Context,
    private val db: FirebaseFirestore,
) {
    private val sessionCache = mutableMapOf<String, ByteArray>()

    /**
     * Gets or creates a shared encryption key for a chat.
     * The key is derived from both users' public keys.
     */
    suspend fun getOrCreateSessionKey(chatId: String, otherUserPublicKey: java.security.PublicKey): ByteArray {
        sessionCache[chatId]?.let { return it }

        val myPrivateKey = KeyStoreManager.getIdentityPrivateKey(context)
        val sharedSecret = E2EEncryption.deriveSharedSecret(myPrivateKey, otherUserPublicKey)
        val encryptionKey = E2EEncryption.deriveEncryptionKey(sharedSecret)
        
        sessionCache[chatId] = encryptionKey
        return encryptionKey
    }

    /**
     * Stores the user's public key in Firestore for other users to derive shared secrets.
     */
    suspend fun publishPublicKey(uid: String, publicKey: java.security.PublicKey) {
        val publicKeyBase64 = android.util.Base64.encodeToString(
            publicKey.encoded,
            android.util.Base64.NO_WRAP
        )
        db.collection("publicKeys").document(uid).set(
            mapOf(
                "publicKey" to publicKeyBase64,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /**
     * Fetches another user's public key from Firestore.
     */
    suspend fun getPublicKey(uid: String): java.security.PublicKey? {
        val doc = db.collection("publicKeys").document(uid).get().await()
        if (!doc.exists()) return null
        
        val publicKeyBase64 = doc.getString("publicKey") ?: return null
        val publicKeyBytes = android.util.Base64.decode(publicKeyBase64, android.util.Base64.NO_WRAP)
        
        val keyFactory = java.security.KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
    }

    fun clearSession(chatId: String) {
        sessionCache.remove(chatId)
    }
}
