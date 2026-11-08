package com.example.kanak.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.kanak.data.repository.KanakRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Android WorkManager Worker that reliably flushes the offline sync queue
 * and executes bi-directional Google Sheets synchronization whenever network connectivity is available.
 */
class GoogleSheetsSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "GoogleSheetsSyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting WorkManager background offline queue sync...")
        val repository = KanakRepository.getInstance(applicationContext)

        try {
            // 1. Flush offline Room queue mutations to remote sync gateway
            val queueFlushResult = repository.flushPendingRoomSyncQueue()
            val flushedCount = queueFlushResult.getOrDefault(0)

            // 2. Push sales records to Google Sheets
            val salesPushResult = repository.pushFirestoreSalesToGoogleSheets()
            val salesPushed = salesPushResult.getOrDefault(0)

            Log.i(TAG, "Background sync worker completed successfully. Flushed: $flushedCount, Sales Pushed: $salesPushed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager sync execution failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "kanak_google_sheets_sync_work"

        /**
         * Schedules periodic background sync every 15 minutes with NetworkType.CONNECTED constraint.
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncWorkRequest = PeriodicWorkRequestBuilder<GoogleSheetsSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )
            Log.i("GoogleSheetsSyncWorker", "Periodic WorkManager sync scheduled (15 min interval).")
        }

        /**
         * Enqueues an immediate one-time outbox push when a new offline sale is created.
         */
        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<GoogleSheetsSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeRequest)
        }
    }
}
