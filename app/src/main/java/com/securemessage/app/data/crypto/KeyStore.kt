package com.securemessage.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

/**
 * Manages the identity key pair in the Android Keystore (hardware-backed when available).
 * The private key never leaves the keystore.
 */
object KeyStoreManager {
    private const val TAG = "KeyStoreManager"
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
            val pair = KeyPair(entry.certificate.publicKey, entry.privateKey)
            // Older builds (before the C1 fix) created this key with SIGN/VERIFY purposes and no
            // key-agreement purpose, so ECDH throws "Incompatible purpose" and no chat can ever
            // encrypt. If the existing key can't do agreement, drop it and generate a correct one.
            // This changes the device's public key (a new safety number, and messages previously
            // received under the old key become unreadable), but that history was already
            // undecryptable on these devices — a working key is strictly better than a dead one.
            if (canAgree(pair.private, pair.public)) return pair
            Log.w(TAG, "Existing identity key can't do ECDH; regenerating with PURPOSE_AGREE_KEY")
            keyStore.deleteEntry(KEYSTORE_ALIAS)
        }

        return generateIdentityKeyPair()
    }

    /** Builds a fresh P-256 key pair for ECDH key agreement under [KEYSTORE_ALIAS]. */
    private fun generateIdentityKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        // This key is used ONLY for ECDH key agreement, so it must carry PURPOSE_AGREE_KEY.
        //
        // The AndroidKeyStore enforces key purposes, and KeyAgreement.init(privateKey) fails with
        // an "Incompatible purpose" error unless the key was created for agreement. The public
        // KeyProperties.PURPOSE_AGREE_KEY constant only appears in the SDK at API 31, but the
        // underlying purpose has been accepted by the keystore since API 23 — so on older devices
        // we pass the same numeric value (1 << 5) directly. The previous code only added the
        // purpose on API 31+ and otherwise asked for SIGN/VERIFY, which meant agreement threw on
        // every chat for Android 7–11 and no channel could ever be established there.
        val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, PURPOSE_AGREE_KEY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /** True if [priv] can actually perform ECDH (i.e. the key carries the agreement purpose). */
    private fun canAgree(priv: PrivateKey, pub: PublicKey): Boolean = try {
        KeyAgreement.getInstance("ECDH").apply {
            init(priv)
            doPhase(pub, true)
            generateSecret()
        }
        true
    } catch (e: Exception) {
        false
    }

    // KeyProperties.PURPOSE_AGREE_KEY (added in API 31) is value 1 shl 5. Declared here so the
    // same agreement purpose is requested on every supported API, not just API 31+.
    private const val PURPOSE_AGREE_KEY = 1 shl 5

    fun getIdentityPublicKey(context: Context): PublicKey = getOrCreateIdentityKeyPair(context).public

    fun getIdentityPrivateKey(context: Context): PrivateKey = getOrCreateIdentityKeyPair(context).private
}
