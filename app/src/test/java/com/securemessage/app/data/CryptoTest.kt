package com.securemessage.app.data

import com.securemessage.app.data.crypto.ChatSessionManager
import com.securemessage.app.data.crypto.E2EEncryption
import com.securemessage.app.data.crypto.IdentityKeys
import com.securemessage.app.data.crypto.PublicKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

private fun newKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

class E2EEncryptionTest {
    private val key = ByteArray(32) { it.toByte() }

    @Test fun roundTripsTextIncludingUnicode() {
        for (text in listOf("hi", "héllo wörld", "नमस्ते 👋🏽", "x".repeat(2000), "line1\nline2")) {
            assertEquals(text, E2EEncryption.decrypt(E2EEncryption.encrypt(text, key), key))
        }
    }

    @Test fun everyEncryptionUsesAFreshIv() {
        val a = E2EEncryption.encrypt("same", key)
        val b = E2EEncryption.encrypt("same", key)
        assertNotEquals(a, b)
    }

    @Test fun wrongKeyCannotDecrypt() {
        val c = E2EEncryption.encrypt("secret", key)
        try {
            E2EEncryption.decrypt(c, ByteArray(32) { 7 })
            fail("decrypting with the wrong key must fail")
        } catch (expected: Exception) {
        }
    }

    @Test fun tamperingIsDetected() {
        val c = E2EEncryption.encrypt("secret", key).toCharArray()
        val i = c.size / 2
        c[i] = if (c[i] == 'A') 'B' else 'A'
        try {
            E2EEncryption.decrypt(String(c), key)
            fail("altered ciphertext must be rejected")
        } catch (expected: Exception) {
        }
    }

    @Test fun garbageAndTruncatedInputFailCleanly() {
        for (bad in listOf("", "abc", "AAAAAAAAAAAAAAAA", "not base64 !!!")) {
            try {
                E2EEncryption.decrypt(bad, key)
                fail("should fail for '$bad'")
            } catch (expected: Exception) {
            }
        }
    }

    @Test fun recognisesCiphertextButNotOrdinaryText() {
        assertTrue(E2EEncryption.looksEncrypted(E2EEncryption.encrypt("x", key)))
        assertTrue(E2EEncryption.looksEncrypted(E2EEncryption.encrypt("x".repeat(500), key)))
        for (plain in listOf("", "hello", "hello world, how are you doing today my friend?", "https://example.com/some/long/path?x=1")) {
            assertFalse(plain, E2EEncryption.looksEncrypted(plain))
        }
    }

    @Test fun bothSidesDeriveTheSameKey() {
        val alice = newKeyPair()
        val bob = newKeyPair()
        val a = E2EEncryption.deriveEncryptionKey(E2EEncryption.deriveSharedSecret(alice.private, bob.public))
        val b = E2EEncryption.deriveEncryptionKey(E2EEncryption.deriveSharedSecret(bob.private, alice.public))
        assertArrayEquals(a, b)
        assertEquals(32, a.size)
        val carol = newKeyPair()
        val c = E2EEncryption.deriveEncryptionKey(E2EEncryption.deriveSharedSecret(alice.private, carol.public))
        assertFalse(a.contentEquals(c))
    }

    @Test fun v1FormatRoundTripsWithChatBoundKey() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val k1 = E2EEncryption.deriveChatKey(secret, "chatA")
        for (text in listOf("hi", "héllo 👋🏽", "x".repeat(2000), "line1\nline2")) {
            assertEquals(text, E2EEncryption.decrypt(E2EEncryption.encrypt(text, k1), k1))
        }
    }

    @Test fun deriveChatKeyIsBoundToTheChatContext() {
        val secret = ByteArray(32) { it.toByte() }
        val a = E2EEncryption.deriveChatKey(secret, "chatA")
        val b = E2EEncryption.deriveChatKey(secret, "chatB")
        assertFalse("different chats must get different keys", a.contentEquals(b))
        assertEquals(32, a.size)
        // Deterministic: same inputs -> same key, so both phones agree.
        assertArrayEquals(a, E2EEncryption.deriveChatKey(secret.copyOf(), "chatA"))
    }

    @Test fun legacyV0MessagesStillDecryptWithLegacyKey() {
        // A message produced by the OLD scheme: bare SHA-256 key, no v1 framing.
        val secret = ByteArray(32) { (it + 3).toByte() }
        val legacy = E2EEncryption.deriveEncryptionKey(secret)
        val legacyCipher = legacyEncryptV0("old message", legacy)
        val v1 = E2EEncryption.deriveChatKey(secret, "c")
        // decrypt() must fall back to the legacy key for an unframed payload.
        assertEquals("old message", E2EEncryption.decrypt(legacyCipher, v1, legacy))
    }

    @Test fun v1AndLegacyAreDistinguishedByTheHeader() {
        val secret = ByteArray(32) { (it + 1).toByte() }
        val legacy = E2EEncryption.deriveEncryptionKey(secret)
        val v1 = E2EEncryption.deriveChatKey(secret, "c")
        val newMsg = E2EEncryption.encrypt("new", v1)
        // A v1 payload decrypts with the v1 key even when a (wrong) legacy key is also supplied.
        assertEquals("new", E2EEncryption.decrypt(newMsg, v1, legacy))
        assertTrue(E2EEncryption.looksEncrypted(newMsg))
    }

    @Test fun hkdfMatchesRfc5869TestVector1() {
        // RFC 5869 Appendix A.1 (SHA-256), first 42 bytes of OKM.
        val ikm = ByteArray(22) { 0x0b }
        val salt = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val info = byteArrayOf(
            0xf0.toByte(), 0xf1.toByte(), 0xf2.toByte(), 0xf3.toByte(), 0xf4.toByte(),
            0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte(), 0xf8.toByte(), 0xf9.toByte(),
        )
        val okm = E2EEncryption.hkdfSha256(ikm, salt, info, 42)
        val expectedHex = "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        assertEquals(expectedHex, okm.joinToString("") { "%02x".format(it) })
    }

    // Mirrors the pre-v1 wire format: base64(iv[12] + ciphertext+tag), no header.
    private fun legacyEncryptV0(plaintext: String, key: ByteArray): String {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, iv),
        )
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return java.util.Base64.getEncoder().encodeToString(iv + ct)
    }

    @Test fun unsignedComparisonHandlesHighBytes() {
        // 0x80 is negative as a signed byte; it must still sort after 0x7F.
        assertTrue(E2EEncryption.compareUnsigned(byteArrayOf(0x80.toByte()), byteArrayOf(0x7F)) > 0)
        assertEquals(0, E2EEncryption.compareUnsigned(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertTrue(E2EEncryption.compareUnsigned(byteArrayOf(1), byteArrayOf(1, 0)) < 0)
    }
}

class ChatSessionManagerTest {
    private class Keys(private val pair: KeyPair) : IdentityKeys {
        override fun publicKey() = pair.public
        override fun privateKey() = pair.private
    }

    private class Store(var key: PublicKey?) : PublicKeyStore {
        var published: PublicKey? = null
        override suspend fun publish(uid: String, key: PublicKey) {
            published = key
        }
        override suspend fun fetch(uid: String): PublicKey? = key
    }

    @Test fun bothPhonesEndUpWithTheSameKeyAndSafetyNumber() = runTest {
        val a = newKeyPair()
        val b = newKeyPair()
        val onA = ChatSessionManager(Keys(a), Store(b.public)).openChannel("c", "bob")!!
        val onB = ChatSessionManager(Keys(b), Store(a.public)).openChannel("c", "alice")!!
        assertArrayEquals(onA.key, onB.key)
        assertEquals(onA.safetyNumber, onB.safetyNumber)
        assertEquals("message sent by one must open on the other", "hey",
            E2EEncryption.decrypt(E2EEncryption.encrypt("hey", onA.key), onB.key))
    }

    @Test fun noPublishedKeyMeansNoChannel() = runTest {
        assertNull(ChatSessionManager(Keys(newKeyPair()), Store(null)).openChannel("c", "bob"))
    }

    @Test fun reusesTheChannelWhileThePeerKeyIsUnchanged() = runTest {
        val peer = newKeyPair()
        val mgr = ChatSessionManager(Keys(newKeyPair()), Store(peer.public))
        assertSame(mgr.openChannel("c", "bob"), mgr.openChannel("c", "bob"))
    }

    /** Regression: a cache keyed by chat alone kept the old key after the peer reinstalled. */
    @Test fun buildsANewChannelWhenThePeerKeyChanges() = runTest {
        val store = Store(newKeyPair().public)
        val mgr = ChatSessionManager(Keys(newKeyPair()), store)
        val before = mgr.openChannel("c", "bob")!!
        store.key = newKeyPair().public
        val after = mgr.openChannel("c", "bob")!!
        assertFalse(before.key.contentEquals(after.key))
        assertNotEquals(before.safetyNumber, after.safetyNumber)
    }

    @Test fun publishesOurOwnPublicKey() = runTest {
        val mine = newKeyPair()
        val store = Store(null)
        ChatSessionManager(Keys(mine), store).publishMyKey("me")
        assertEquals(mine.public, store.published)
    }
}
