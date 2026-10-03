package com.securemessage.app.data.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption using ECDH key agreement + AES-256-GCM.
 * 
 * This implements a simplified Signal Protocol approach:
 * - Each user has an identity key pair (stored in Android Keystore)
 * - For each chat, a shared secret is derived via ECDH
 * - Messages are encrypted with AES-256-GCM using the shared secret
 * - Each message uses a unique IV for security
 */
object E2EEncryption {
    private const val AES_GCM_TAG_LENGTH = 128
    private const val AES_IV_LENGTH = 12
    private const val AES_KEY_LENGTH = 32 // 256 bits

    /**
     * Derives a shared secret from two key pairs using ECDH.
     */
    fun deriveSharedSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    /**
     * Derives an AES-256 key from the shared secret using SHA-256.
     */
    fun deriveEncryptionKey(sharedSecret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(sharedSecret)
    }

    /**
     * Encrypts a plaintext message with AES-256-GCM.
     * Returns Base64-encoded encrypted data with IV prepended.
     */
    fun encrypt(plaintext: String, encryptionKey: ByteArray): String {
        val iv = ByteArray(AES_IV_LENGTH).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Prepend IV to ciphertext
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded encrypted message with AES-256-GCM.
     */
    fun decrypt(encryptedBase64: String, encryptionKey: ByteArray): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, AES_IV_LENGTH)
        val ciphertext = combined.copyOfRange(AES_IV_LENGTH, combined.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(encryptionKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(AES_GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    /**
     * Computes a fingerprint for key verification (like Signal's safety numbers).
     */
    fun computeFingerprint(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey)
        return hash.take(6).joinToString("") { "%02x".format(it) }.uppercase()
    }
}
