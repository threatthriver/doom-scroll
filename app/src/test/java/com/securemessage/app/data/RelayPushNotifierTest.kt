package com.securemessage.app.data

import com.securemessage.app.data.push.RelayPushNotifier
import com.securemessage.app.data.push.RelayTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RelayPushNotifierTest {
    private class FakeTransport(private val results: MutableList<Any>) : RelayTransport {
        val calls = mutableListOf<Triple<String, String, String>>()
        override suspend fun post(url: String, idToken: String, jsonBody: String): Int {
            calls += Triple(url, idToken, jsonBody)
            val r = results.removeAt(0)
            if (r is IOException) throw r
            return r as Int
        }
    }

    private val pauses = mutableListOf<Long>()
    private fun notifier(
        transport: RelayTransport,
        token: suspend (Boolean) -> String? = { force -> if (force) "fresh" else "cached" },
    ) = RelayPushNotifier("https://relay/v1/notify", token, transport, { pauses += it })

    @Test fun sendsChatAndMessageIdWithBearerToken() = runTest {
        val t = FakeTransport(mutableListOf(200))
        notifier(t).messageSent("c1", "m1")
        assertEquals(1, t.calls.size)
        assertEquals("https://relay/v1/notify", t.calls[0].first)
        assertEquals("cached", t.calls[0].second)
        assertTrue(t.calls[0].third.contains("\"chatId\":\"c1\"") && t.calls[0].third.contains("\"messageId\":\"m1\""))
        assertTrue("the body must not carry message text", !t.calls[0].third.contains("text"))
    }

    @Test fun retriesTemporaryFailuresWithBackoff() = runTest {
        val t = FakeTransport(mutableListOf(503, IOException("net"), 200))
        notifier(t).messageSent("c1", "m1")
        assertEquals(3, t.calls.size)
        assertEquals(listOf(1_000L, 3_000L), pauses)
    }

    @Test fun givesUpAfterTheBackoffBudget() = runTest {
        val t = FakeTransport(mutableListOf(500, 500, 500, 500))
        notifier(t).messageSent("c1", "m1")
        assertEquals(3, t.calls.size) // first try + two retries
    }

    @Test fun refreshesTheIdTokenOnceOn401() = runTest {
        val t = FakeTransport(mutableListOf(401, 200))
        notifier(t).messageSent("c1", "m1")
        assertEquals(listOf("cached", "fresh"), t.calls.map { it.second })
    }

    @Test fun doesNotLoopOnPersistent401() = runTest {
        val t = FakeTransport(mutableListOf(401, 401, 401))
        notifier(t).messageSent("c1", "m1")
        assertEquals(2, t.calls.size)
    }

    @Test fun doesNotRetryPermanentAnswers() = runTest {
        for (code in listOf(400, 403, 404, 409, 413)) {
            val t = FakeTransport(mutableListOf(code, 200))
            notifier(t).messageSent("c1", "m1")
            assertEquals("status $code", 1, t.calls.size)
        }
    }

    @Test fun retriesRateLimiting() = runTest {
        val t = FakeTransport(mutableListOf(429, 200))
        notifier(t).messageSent("c1", "m1")
        assertEquals(2, t.calls.size)
    }

    @Test fun skipsQuietlyWhenSignedOut() = runTest {
        val t = FakeTransport(mutableListOf(200))
        notifier(t, token = { null }).messageSent("c1", "m1")
        assertEquals(0, t.calls.size)
    }

    @Test fun neverThrowsIntoTheSendPath() = runTest {
        val t = FakeTransport(mutableListOf(IOException("a"), IOException("b"), IOException("c")))
        notifier(t, token = { throw IllegalStateException("boom") }).messageSent("c1", "m1")
        notifier(t).messageSent("c1", "m1")
    }
}
