package com.securemessage.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Manages cryptographic keys using Android Keystore.
 * Keys are generated and stored in hardware-backed keystore when available.
 */
object KeyStoreManager {
    private const val KEYSTORE_ALIAS = "doomscroll_identity_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Gets or generates the user's identity key pair (X25519 for ECDH).
     */
    fun getOrCreateIdentityKeyPair(context: Context): java.security.KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // Return existing key if available
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            val privateKey = (entry as KeyStore.PrivateKeyEntry).privateKey
            val publicKey = entry.certificate.publicKey
            return java.security.KeyPair(publicKey, privateKey)
        }

        // Generate new key pair
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY or KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    fun getIdentityPublicKey(context: Context): PublicKey {
        return getOrCreateIdentityKeyPair(context).public
    }

    fun getIdentityPrivateKey(context: Context): PrivateKey {
        return getOrCreateIdentityKeyPair(context).private
    }
}
