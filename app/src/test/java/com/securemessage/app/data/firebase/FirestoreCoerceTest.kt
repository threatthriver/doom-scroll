package com.securemessage.app.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the startup crash:
 * `ClassCastException: java.lang.Long cannot be cast to java.lang.Integer` thrown from
 * FirestoreChatRepository.toChat when a chat document was read.
 */
class FirestoreCoerceTest {

    @Test fun `intMap reads Long values as Firestore actually returns them`() {
        val raw = mapOf("me" to 3L, "other" to 0L)
        assertEquals(mapOf("me" to 3, "other" to 0), FirestoreCoerce.intMap(raw))
    }

    @Test fun `intMap accepts any numeric width`() {
        val raw = mapOf("a" to 7, "b" to 12L, "c" to 9.0)
        assertEquals(mapOf("a" to 7, "b" to 12, "c" to 9), FirestoreCoerce.intMap(raw))
    }

    @Test fun `intMap tolerates missing and malformed entries`() {
        assertEquals(emptyMap<String, Int>(), FirestoreCoerce.intMap(null))
        assertEquals(emptyMap<String, Int>(), FirestoreCoerce.intMap("nope"))
        assertEquals(mapOf("ok" to 2), FirestoreCoerce.intMap(mapOf(1L to 2L, "ok" to 2L, "bad" to "x")))
    }

    @Test fun `booleanMap reads archive flags`() {
        val raw = mapOf("me" to true, "other" to false)
        assertEquals(raw, FirestoreCoerce.booleanMap(raw))
        assertEquals(emptyMap<String, Boolean>(), FirestoreCoerce.booleanMap(null))
    }

    @Test fun `stringList skips non-string members`() {
        assertEquals(listOf("a", "b"), FirestoreCoerce.stringList(listOf("a", 5, null, "b")))
        assertTrue(FirestoreCoerce.stringList(null).isEmpty())
    }

    @Test fun `stringMap skips non-string keys and values`() {
        assertEquals(mapOf("me" to "hi"), FirestoreCoerce.stringMap(mapOf(7 to "x", "me" to "hi", "bad" to 3)))
        assertTrue(FirestoreCoerce.stringMap(null).isEmpty())
    }

    @Test fun `bool defaults to false rather than throwing`() {
        assertTrue(FirestoreCoerce.bool(true))
        assertEquals(false, FirestoreCoerce.bool(null))
        assertEquals(false, FirestoreCoerce.bool("true"))
    }
}