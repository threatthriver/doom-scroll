package com.securemessage.app.data

import com.securemessage.app.data.AuthDestination.COMPLETE_PROFILE
import com.securemessage.app.data.AuthDestination.CONVERSATIONS
import com.securemessage.app.data.AuthDestination.SIGN_IN
import com.securemessage.app.data.AuthDestination.VERIFY_EMAIL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGateTest {
    @Test fun allCombinations() {
        val expected = mapOf(
            Triple(false, false, false) to SIGN_IN,
            Triple(false, false, true) to SIGN_IN,
            Triple(false, true, false) to SIGN_IN,
            Triple(false, true, true) to SIGN_IN,
            Triple(true, false, false) to VERIFY_EMAIL,
            Triple(true, false, true) to VERIFY_EMAIL,
            Triple(true, true, false) to COMPLETE_PROFILE,
            Triple(true, true, true) to CONVERSATIONS,
        )
        expected.forEach { (k, v) -> assertEquals(k.toString(), v, AuthGate.route(k.first, k.second, k.third)) }
    }

    @Test fun chatsRequireSignedInAndVerified() {
        assertTrue(AuthGate.canAccessChats(signedIn = true, emailVerified = true))
        assertFalse(AuthGate.canAccessChats(signedIn = true, emailVerified = false))
        assertFalse(AuthGate.canAccessChats(signedIn = false, emailVerified = true))
        assertFalse(AuthGate.canAccessChats(signedIn = false, emailVerified = false))
    }
}
