package com.securemessage.app.data.crypto

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** An established secure channel with the other person in a chat. */
class SecureChannel(
    /** v1 AES-256 key for this chat (HKDF, bound to the chat id). Used to encrypt and to decrypt
     *  messages sent by current app versions. */
    val key: ByteArray,
    /** Same on both phones; people compare it to make sure nobody sits in the middle. */
    val safetyNumber: String,
    /** Legacy (v0) AES key: bare SHA-256 of the ECDH secret. Only used to decrypt messages sent by
     *  older app versions, so history isn't lost. Null when there is no legacy derivation. */
    val legacyKey: ByteArray? = null,
)

/** Opens the end-to-end encrypted channel for a chat. A seam so chat logic is testable. */
interface ChatCrypto {
    /** Null when the other person has no published key (yet) or it couldn't be read. */
    suspend fun openChannel(chatId: String, otherUid: String): SecureChannel?

    /**
     * Same, but only from what is already on the phone, never the network. Lets a chat decrypt
     * instantly on open while [openChannel] confirms the key in the background.
     */
    suspend fun openCachedChannel(chatId: String, otherUid: String): SecureChannel? = null
}

/** This device's long-term identity key pair. */
interface IdentityKeys {
    fun publicKey(): PublicKey
    fun privateKey(): PrivateKey
}

/** Where public keys are published and looked up. */
interface PublicKeyStore {
    suspend fun publish(uid: String, key: PublicKey)
    suspend fun fetch(uid: String): PublicKey?

    /** From the local cache only; null if it isn't cached. */
    suspend fun fetchCached(uid: String): PublicKey? = null
}

/** Identity keys held in the Android Keystore. */
class AndroidIdentityKeys(private val context: android.content.Context) : IdentityKeys {
    override fun publicKey(): PublicKey = KeyStoreManager.getIdentityPublicKey(context)
    override fun privateKey(): PrivateKey = KeyStoreManager.getIdentityPrivateKey(context)
}

/** Public keys in the Firestore `publicKeys/{uid}` documents. */
@OptIn(ExperimentalEncodingApi::class)
class FirestorePublicKeyStore(private val db: FirebaseFirestore) : PublicKeyStore {
    override suspend fun publish(uid: String, key: PublicKey) {
        db.collection("publicKeys").document(uid).set(
            mapOf(
                "publicKey" to Base64.Default.encode(key.encoded),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun fetch(uid: String): PublicKey? =
        parse(db.collection("publicKeys").document(uid).get().await())

    override suspend fun fetchCached(uid: String): PublicKey? = runCatching {
        parse(db.collection("publicKeys").document(uid).get(Source.CACHE).await())
    }.getOrNull()

    private fun parse(doc: com.google.firebase.firestore.DocumentSnapshot): PublicKey? {
        if (!doc.exists()) return null
        val encoded = doc.getString("publicKey") ?: return null
        val bytes = Base64.Default.decode(encoded)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }
}

/**
 * Builds per-chat encryption channels from our identity key and the other person's public key.
 *
 * A cached channel is only reused while the other person's key is unchanged. If they reinstall
 * or switch phones their key changes, and a cache keyed by chat alone would keep encrypting with
 * the old shared secret until the app restarted, producing messages they can never read.
 */
class ChatSessionManager(
    private val identity: IdentityKeys,
    private val keys: PublicKeyStore,
) : ChatCrypto {
    private class Cached(val peerKey: ByteArray, val channel: SecureChannel)

    private val cache = ConcurrentHashMap<String, Cached>()

    override suspend fun openChannel(chatId: String, otherUid: String): SecureChannel? {
        val peer = keys.fetch(otherUid) ?: return null
        return channelFor(chatId, peer)
    }

    override suspend fun openCachedChannel(chatId: String, otherUid: String): SecureChannel? {
        val peer = keys.fetchCached(otherUid) ?: return null
        return channelFor(chatId, peer)
    }

    internal fun channelFor(chatId: String, peer: PublicKey): SecureChannel {
        val peerBytes = peer.encoded
        cache[chatId]?.let { if (it.peerKey.contentEquals(peerBytes)) return it.channel }

        val secret = E2EEncryption.deriveSharedSecret(identity.privateKey(), peer)
        val channel = SecureChannel(
            key = E2EEncryption.deriveChatKey(secret, chatId),
            safetyNumber = E2EEncryption.computeSafetyNumber(identity.publicKey().encoded, peerBytes),
            legacyKey = E2EEncryption.deriveEncryptionKey(secret),
        )
        cache[chatId] = Cached(peerBytes, channel)
        return channel
    }

    /** Publishes this device's public key so the other side can derive the same channel. */
    suspend fun publishMyKey(uid: String) = keys.publish(uid, identity.publicKey())

    fun clearSession(chatId: String) {
        cache.remove(chatId)
    }
}
