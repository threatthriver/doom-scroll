package com.securemessage.app.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Manages the identity key pair in the Android Keystore (hardware-backed when available).
 * The private key never leaves the keystore.
 */
object KeyStoreManager {
    private const val KEYSTORE_ALIAS = "doomscroll_identity_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /**
     * Gets or generates the identity key pair (P-256, used for ECDH).
     *
     * Synchronized: the app start-up key publisher and the chat screen can both ask for the key
     * at the same moment on first run. Without the lock each could generate a pair under the same
     * alias, the second overwriting the first, so the published public key would no longer match
     * the private key in the keystore and every message would be undecryptable.
     */
    @Synchronized
    fun getOrCreateIdentityKeyPair(@Suppress("UNUSED_PARAMETER") context: Context): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { entry ->
            return KeyPair(entry.certificate.publicKey, entry.privateKey)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        // PURPOSE_AGREE_KEY (key agreement) only exists from Android 12. Older versions don't know
        // the flag and must not be given it; there, EC keys can be used for ECDH without it.
        // (Not yet verified on a real Android 7-11 phone; see the release notes.)
        var purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) purposes = purposes or KeyProperties.PURPOSE_AGREE_KEY
        val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, purposes)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    fun getIdentityPublicKey(context: Context): PublicKey = getOrCreateIdentityKeyPair(context).public

    fun getIdentityPrivateKey(context: Context): PrivateKey = getOrCreateIdentityKeyPair(context).private
}
