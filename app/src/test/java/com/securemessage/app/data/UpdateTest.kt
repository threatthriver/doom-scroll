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
}
