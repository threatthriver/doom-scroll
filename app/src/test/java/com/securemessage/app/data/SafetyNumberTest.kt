package com.securemessage.app.data

import com.securemessage.app.data.crypto.E2EEncryption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SafetyNumberTest {
    private val a = ByteArray(32) { it.toByte() }
    private val b = ByteArray(32) { (it * 3 + 1).toByte() }
    private val c = ByteArray(32) { (it * 7 + 5).toByte() }

    @Test fun sameOnBothPhonesRegardlessOfOrder() {
        assertEquals(E2EEncryption.computeSafetyNumber(a, b), E2EEncryption.computeSafetyNumber(b, a))
    }

    @Test fun differsWhenEitherKeyChanges() {
        val base = E2EEncryption.computeSafetyNumber(a, b)
        assertNotEquals(base, E2EEncryption.computeSafetyNumber(a, c))
        assertNotEquals(base, E2EEncryption.computeSafetyNumber(c, b))
    }

    @Test fun is24UppercaseHexChars() {
        val n = E2EEncryption.computeSafetyNumber(a, b)
        assertEquals(24, n.length)
        assertEquals(n.uppercase(), n)
    }
}
