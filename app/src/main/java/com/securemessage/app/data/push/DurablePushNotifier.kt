package com.securemessage.app.data.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.securemessage.app.SecureMessageApp
import java.util.concurrent.TimeUnit

/**
 * Sends the push request right away; if that can't finish (offline, relay hiccup, app killed
 * mid-flight), hands it to WorkManager, which retries once a network is available and survives
 * process death. Without this a message sent on a bad connection never produced an alert.
 */
class DurablePushNotifier(
    private val context: Context,
    private val relay: PushNotifier,
) : PushNotifier {

    override suspend fun messageSent(chatId: String, messageId: String) {
        val done = runCatching { relay.deliver(chatId, messageId) }.getOrDefault(false)
        if (!done) enqueueRetry(context, chatId, messageId)
    }

    companion object {
        internal const val KEY_CHAT = "chatId"
        internal const val KEY_MESSAGE = "messageId"
        private const val MAX_ATTEMPTS = 6

        internal fun maxAttempts() = MAX_ATTEMPTS

        fun enqueueRetry(context: Context, chatId: String, messageId: String) {
            val request = OneTimeWorkRequestBuilder<PushRetryWorker>()
                .setInputData(workDataOf(KEY_CHAT to chatId, KEY_MESSAGE to messageId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("push-$messageId", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** The relay only alerts for messages under 2 minutes old, so retries stop after a few tries. */
class PushRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val chatId = inputData.getString(DurablePushNotifier.KEY_CHAT) ?: return Result.failure()
        val messageId = inputData.getString(DurablePushNotifier.KEY_MESSAGE) ?: return Result.failure()
        if (runAttemptCount >= DurablePushNotifier.maxAttempts()) return Result.failure()
        val container = (applicationContext as SecureMessageApp).container
        // Use the relay directly: going through the durable wrapper would enqueue again.
        val done = runCatching { container.relayPushNotifier.deliver(chatId, messageId) }.getOrDefault(false)
        return if (done) Result.success() else Result.retry()
    }
}
