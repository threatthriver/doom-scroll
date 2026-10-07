package com.securemessage.app.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * End-to-end encryption using ECDH key agreement + AES-256-GCM.
 *
 * - Each user has an identity key pair (stored in Android Keystore).
 * - For each chat, a shared secret is derived via ECDH.
 * - Messages are encrypted with AES-256-GCM, each with its own random IV.
 *
 * Key schedule (v1): the AES key is derived with HKDF-SHA256 (RFC 5869) from the raw ECDH
 * secret, salted and bound to a per-chat `info` context, instead of a bare SHA-256 of the secret.
 * This gives proper key separation (different chats get different keys even with the same identity
 * pair) and avoids using the ECDH output directly as a key. Legacy (v0) messages — a bare SHA-256
 * key and no framing — are still decryptable, so no message history is lost; see [decrypt].
 *
 * Base64 comes from Kotlin's stdlib (standard, unwrapped alphabet), so all of this runs in plain
 * JVM tests.
 */
@OptIn(ExperimentalEncodingApi::class)
object E2EEncryption {
    private const val AES_GCM_TAG_LENGTH = 128
    private const val AES_IV_LENGTH = 12
    private val random = SecureRandom()

    // v1 framing: a 2-byte magic ("HU") + 1 version byte, prepended before the IV. Legacy v0
    // payloads begin directly with the random 12-byte IV and carry no magic, so the header lets
    // decrypt tell the two apart unambiguously (a v0 payload starting with these exact bytes in
    // the right positions is astronomically unlikely, and even then GCM auth would reject it).
    private val V1_MAGIC = byteArrayOf('H'.code.toByte(), 'U'.code.toByte())
    private const val V1_VERSION: Byte = 1
    private const val V1_HEADER_LEN = 3 // magic(2) + version(1)

    // Fixed, non-secret application salt for HKDF. A constant salt is fine here because the key
    // separation we need comes from the per-chat `info`; the salt just domain-separates Hush from
    // any other HKDF use of the same secret.
    private val HKDF_SALT = "hush.e2ee.v1".toByteArray(Charsets.UTF_8)

    /** Derives a shared secret from our private key and their public key using ECDH. */
    fun deriveSharedSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * LEGACY (v0) key derivation: a bare SHA-256 of the shared secret. Retained only so messages
     * sent by older app versions remain decryptable. New messages use [deriveChatKey].
     */
    fun deriveEncryptionKey(sharedSecret: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(sharedSecret)

    /**
     * v1 key derivation: HKDF-SHA256 of the ECDH secret, bound to a per-chat [context] (the chat
     * id). Two different chats between the same two identities get independent keys.
     */
    fun deriveChatKey(sharedSecret: ByteArray, context: String): ByteArray =
        hkdfSha256(ikm = sharedSecret, salt = HKDF_SALT, info = "chat:$context".toByteArray(Charsets.UTF_8), outLen = 32)

    /**
     * Encrypts with AES-256-GCM using the v1 framing (magic + version + IV + ciphertext/tag),
     * Base64-encoded. [key] must be a v1 chat key from [deriveChatKey].
     */
    fun encrypt(plaintext: String, key: ByteArray): String {
        val iv = ByteArray(AES_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.Default.encode(V1_MAGIC + V1_VERSION + iv + ciphertext)
    }

    /**
     * Decrypts a payload produced by any app version.
     *
     * - v1 payloads (magic header present) are decrypted with [key].
     * - v0 payloads (no header) are decrypted with [legacyKey] when supplied, else [key].
     *
     * Throws if the key is wrong or the data was altered.
     */
    fun decrypt(encryptedBase64: String, key: ByteArray, legacyKey: ByteArray? = null): String {
        val combined = Base64.Default.decode(encryptedBase64)
        return if (hasV1Header(combined)) {
            val body = combined.copyOfRange(V1_HEADER_LEN, combined.size)
            gcmDecrypt(body, key)
        } else {
            // No header → legacy v0 message. Prefer the legacy key when we have one.
            gcmDecrypt(combined, legacyKey ?: key)
        }
    }

    private fun gcmDecrypt(ivAndCiphertext: ByteArray, key: ByteArray): String {
        require(ivAndCiphertext.size > AES_IV_LENGTH) { "Ciphertext too short" }
        val iv = ivAndCiphertext.copyOfRange(0, AES_IV_LENGTH)
        val ciphertext = ivAndCiphertext.copyOfRange(AES_IV_LENGTH, ivAndCiphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun hasV1Header(bytes: ByteArray): Boolean =
        bytes.size > V1_HEADER_LEN &&
            bytes[0] == V1_MAGIC[0] && bytes[1] == V1_MAGIC[1] && bytes[2] == V1_VERSION

    /**
     * True if [text] has the shape of an encrypted payload (either v1 framed or legacy Base64 of
     * IV + ciphertext + tag, so at least ~28 bytes). Used to tell "a message we couldn't decrypt"
     * from an old plaintext message, so scrambled text is never shown as if it were the message.
     */
    fun looksEncrypted(text: String): Boolean = CIPHERTEXT_SHAPE.matches(text)

    private val CIPHERTEXT_SHAPE = Regex("^[A-Za-z0-9+/]{38,}={0,2}$")

    /** Short fingerprint of one public key (shown on the Profile screen). */
    fun computeFingerprint(publicKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return hash.take(6).joinToString("") { "%02x".format(it) }.uppercase()
    }

    /**
     * Safety number for a chat: the same on both phones, so people can compare it out loud.
     * The two public keys are ordered first, so neither side's view differs from the other's.
     */
    fun computeSafetyNumber(keyA: ByteArray, keyB: ByteArray): String {
        val (first, second) = if (compareUnsigned(keyA, keyB) <= 0) keyA to keyB else keyB to keyA
        val hash = MessageDigest.getInstance("SHA-256").digest(first + second)
        return hash.take(12).joinToString("") { "%02x".format(it) }.uppercase()
    }

    /**
     * Lexicographic unsigned byte comparison. java.util.Arrays.compareUnsigned only exists from
     * Android 13, and this app supports Android 7+, so it is written out here.
     */
    internal fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    /**
     * HKDF (RFC 5869) with HMAC-SHA256. Extract-then-expand. Implemented directly because the
     * JDK/Android have no public HKDF API on the supported range.
     */
    internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // Extract: PRK = HMAC(salt, ikm)
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // Expand: T(n) = HMAC(PRK, T(n-1) | info | n)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(outLen)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < outLen) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, outLen - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }
}
