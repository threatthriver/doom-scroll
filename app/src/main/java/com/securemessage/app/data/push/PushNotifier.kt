package com.securemessage.app.data.push

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tells the push relay that a message was just sent, so the recipient's phone gets an alert even
 * when their app is closed. Fire-and-forget: a failure here must never affect sending the message.
 */
interface PushNotifier {
    suspend fun messageSent(chatId: String, messageId: String)

    /**
     * Like [messageSent] but reports whether the work is finished: true when the relay accepted
     * the request or gave a permanent answer, false when a retry later could still succeed
     * (no network, 429/5xx, no ID token yet).
     */
    suspend fun deliver(chatId: String, messageId: String): Boolean {
        messageSent(chatId, messageId)
        return true
    }
}

/** Used when no relay is configured (e.g. local builds): does nothing. */
object NoopPushNotifier : PushNotifier {
    override suspend fun messageSent(chatId: String, messageId: String) = Unit
}

/** One HTTP call to the relay; returns the status code or throws [IOException]. */
fun interface RelayTransport {
    suspend fun post(url: String, idToken: String, jsonBody: String): Int
}

/**
 * Calls the relay with the signed-in user's Firebase ID token. The relay re-checks everything
 * itself (token, chat membership, that the message is yours and new), so this carries no trust.
 *
 * Retries temporary failures (network, 429, 5xx) with a short backoff and refreshes the ID token
 * once on 401. Permanent answers (403, 404, 409, 400) are not retried.
 */
class RelayPushNotifier(
    private val endpoint: String,
    /** Returns the current ID token; `forceRefresh` asks Firebase for a brand new one. */
    private val idToken: suspend (forceRefresh: Boolean) -> String?,
    private val transport: RelayTransport = HttpUrlConnectionTransport,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val backoffMs: List<Long> = listOf(1_000L, 3_000L),
) : PushNotifier {

    override suspend fun messageSent(chatId: String, messageId: String) {
        deliver(chatId, messageId)
    }

    override suspend fun deliver(chatId: String, messageId: String): Boolean {
        val body = """{"chatId":"${jsonEscape(chatId)}","messageId":"${jsonEscape(messageId)}"}"""
        var refreshed = false
        var attempt = 0
        while (true) {
            val token = runCatching { idToken(refreshed) }.getOrNull()
            if (token == null) {
                Log.w(TAG, "No ID token; will retry later")
                return false
            }
            val status = try {
                transport.post(endpoint, token, body)
            } catch (e: IOException) {
                -1
            }
            when {
                status in 200..299 -> return true
                status == 401 && !refreshed -> {
                    refreshed = true
                    continue
                }
                isTransient(status) && attempt < backoffMs.size -> {
                    pause(backoffMs[attempt])
                    attempt++
                }
                else -> {
                    Log.w(TAG, "Push relay gave up (status $status)")
                    // Temporary failures that outlasted the quick retries are worth a later retry.
                    return !isTransient(status)
                }
            }
        }
    }

    /** Ids are plain [A-Za-z0-9_-], but escape anyway so a stray character can't break the JSON. */
    private fun jsonEscape(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun isTransient(status: Int) = status == -1 || status == 429 || status in 500..599

    private companion object {
        const val TAG = "PushRelay"
    }
}

internal object HttpUrlConnectionTransport : RelayTransport {
    override suspend fun post(url: String, idToken: String, jsonBody: String): Int =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection)
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $idToken")
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                // Drain so the connection can be reused; the body is small.
                (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() }
                code
            } finally {
                conn.disconnect()
            }
        }
}

/** The signed-in user's Firebase ID token, or null when signed out. */
fun firebaseIdToken(auth: FirebaseAuth): suspend (Boolean) -> String? = { forceRefresh ->
    auth.currentUser?.getIdToken(forceRefresh)?.await()?.token
}
