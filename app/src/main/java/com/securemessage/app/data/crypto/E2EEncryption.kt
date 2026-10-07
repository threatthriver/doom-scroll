package com.securemessage.app.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
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
 * Base64 comes from Kotlin's stdlib (same standard, unwrapped alphabet as before, so the wire
 * format is unchanged) rather than android.util.Base64, so all of this runs in plain JVM tests.
 */
@OptIn(ExperimentalEncodingApi::class)
object E2EEncryption {
    private const val AES_GCM_TAG_LENGTH = 128
    private const val AES_IV_LENGTH = 12
    private val random = SecureRandom()

    /** Derives a shared secret from our private key and their public key using ECDH. */
    fun deriveSharedSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /** Derives an AES-256 key from the shared secret using SHA-256. */
    fun deriveEncryptionKey(sharedSecret: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(sharedSecret)

    /** Encrypts with AES-256-GCM. Returns Base64 of IV followed by ciphertext and tag. */
    fun encrypt(plaintext: String, encryptionKey: ByteArray): String {
        val iv = ByteArray(AES_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.Default.encode(iv + ciphertext)
    }

    /** Decrypts what [encrypt] produced. Throws if the key is wrong or the data was altered. */
    fun decrypt(encryptedBase64: String, encryptionKey: ByteArray): String {
        val combined = Base64.Default.decode(encryptedBase64)
        require(combined.size > AES_IV_LENGTH) { "Ciphertext too short" }
        val iv = combined.copyOfRange(0, AES_IV_LENGTH)
        val ciphertext = combined.copyOfRange(AES_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * True if [text] has the shape of our encrypted payload (Base64 of IV + ciphertext + tag, so at
     * least 28 bytes). Used to tell "a message we couldn't decrypt" from an old plaintext message,
     * so scrambled text is never shown as if it were the message.
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
}
