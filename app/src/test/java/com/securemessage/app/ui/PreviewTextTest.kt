package com.securemessage.app.ui

import com.securemessage.app.ui.conversations.previewText
import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTextTest {
    @Test fun emptyUsesFallback() = assertEquals("No messages yet", previewText("", "No messages yet"))
    @Test fun plainTextIsKept() = assertEquals("see you at 5, ok?", previewText("see you at 5, ok?", "x"))
    @Test fun urlIsKept() = assertEquals("https://example.com/a", previewText("https://example.com/a", "x"))
    @Test fun ciphertextIsHidden() =
        assertEquals("Encrypted message", previewText("qK3v9ZpL0a1b2c3d4e5f6g7h8i9j0kLmNoPqRsTuVw==", "x"))
}
