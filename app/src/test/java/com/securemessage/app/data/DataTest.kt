package com.securemessage.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIdsTest {
    @Test fun symmetric() = assertEquals(chatIdFor("b", "a"), chatIdFor("a", "b"))
    @Test fun format() = assertEquals("a_b", chatIdFor("b", "a"))
}

class ValidationTest {
    @Test fun usernames() {
        assertTrue(USERNAME_REGEX.matches("ali_99"))
        assertFalse(USERNAME_REGEX.matches("al"))
        assertFalse(USERNAME_REGEX.matches("Ali"))
        assertFalse(USERNAME_REGEX.matches("a".repeat(21)))
        assertFalse(USERNAME_REGEX.matches("a-b"))
    }

    @Test fun normalization() = assertEquals("alice", normalizeUsername("  Alice "))

    @Test fun emails() {
        assertTrue(EMAIL_REGEX.matches("bob@x.com"))
        assertFalse(EMAIL_REGEX.matches("bob@x"))
        assertFalse(EMAIL_REGEX.matches("bob"))
    }
}
