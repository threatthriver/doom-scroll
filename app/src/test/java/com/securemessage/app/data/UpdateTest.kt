package com.securemessage.app.data

import com.securemessage.app.data.update.GitHubUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun newerPatchVersion() {
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", "v1.0.1"))
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.0.0", "v1.0.1"))
    }

    @Test
    fun newerMinorVersion() {
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(GitHubUpdateManager.isNewerVersion("1.0.0", "v1.1.0"))
    }

    @Test
    fun newerMajorVersion() {
        assertTrue(GitHubUpdateManager.isNewerVersion("1.9.9", "2.0.0"))
        assertTrue(GitHubUpdateManager.isNewerVersion("v1.9.9", "v2.0.0"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0.0", "v1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(GitHubUpdateManager.isNewerVersion("1.1.0", "1.0.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("2.0.0", "v1.9.9"))
        assertFalse(GitHubUpdateManager.isNewerVersion("v1.0.5", "1.0.4"))
    }

    @Test
    fun garbageTagsNeverTriggerUpdate() {
        // Old fallback treated ANY different string as "newer" — a downgrade tag or
        // junk would prompt an update. Unknown tags must be ignored instead.
        assertFalse(GitHubUpdateManager.isNewerVersion("1.2.0", "latest"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.2.0", ""))
        assertFalse(GitHubUpdateManager.isNewerVersion("", "v1.2.0"))
        assertFalse(GitHubUpdateManager.isNewerVersion("abc", "def"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.2.0", "0.9.0-beta"))
    }

    @Test
    fun preReleaseSuffixDoesNotCountAsNewerThanRelease() {
        assertFalse(GitHubUpdateManager.isNewerVersion("1.2.0", "1.2.0-beta"))
        assertFalse(GitHubUpdateManager.isNewerVersion("1.2.0-beta", "1.2.0-beta"))
    }

    @Test
    fun whitespaceAndCapitalVHandled() {
        assertTrue(GitHubUpdateManager.isNewerVersion("  v1.2.0  ", "V1.2.1"))
        assertFalse(GitHubUpdateManager.isNewerVersion("V1.2.1", "v1.2.1 "))
    }

    @Test
    fun corruptApkFailsValidation() {
        val tmp = kotlin.io.path.createTempFile(suffix = ".apk").toFile()
        try {
            tmp.writeText("not a zip")
            assertFalse(GitHubUpdateManager.isValidApk(tmp))
            tmp.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            // Correct magic but far below the 1MB sanity floor — still rejected.
            assertFalse(GitHubUpdateManager.isValidApk(tmp))
        } finally {
            tmp.delete()
        }
    }
}
