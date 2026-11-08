package com.example.kanak.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.kanak.data.repository.KanakRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Background synchronization service that pushes completed sales records from Firestore
 * to the authoritative Google Sheets document.
 * Operates reliably in the background, handling network retries and reporting live sync metrics.
 */
class SalesSyncBackgroundService : Service() {

    companion object {
        private const val TAG = "SalesSyncService"
        private const val NOTIFICATION_CHANNEL_ID = "kanak_sales_sync_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START_SYNC = "com.example.kanak.ACTION_START_SYNC"

        private val _isServiceSyncing = MutableStateFlow(false)
        val isServiceSyncing: StateFlow<Boolean> = _isServiceSyncing.asStateFlow()

        private val _lastSyncResult = MutableStateFlow<String?>(null)
        val lastSyncResult: StateFlow<String?> = _lastSyncResult.asStateFlow()

        private val _pendingSyncCount = MutableStateFlow(0)
        val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()

        fun startSync(context: Context) {
            val intent = Intent(context, SalesSyncBackgroundService::class.java).apply {
                action = ACTION_START_SYNC
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start service directly: ${e.message}")
            }
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: KanakRepository

    override fun onCreate() {
        super.onCreate()
        repository = KanakRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Syncing sales records to Google Sheets..."))

        serviceScope.launch {
            _isServiceSyncing.value = true
            try {
                Log.i(TAG, "Starting background synchronization of sales records...")
                // Push pending completed sales from Firestore to Google Sheets
                val result = repository.pushFirestoreSalesToGoogleSheets()
                result.onSuccess { count ->
                    val msg = "Sync complete: $count sales records pushed to Google Sheets"
                    Log.i(TAG, msg)
                    _lastSyncResult.value = msg
                    _pendingSyncCount.value = 0
                }.onFailure { err ->
                    val errMsg = "Sync warning: ${err.message}"
                    Log.w(TAG, errMsg)
                    _lastSyncResult.value = errMsg
                }

                // Also process any locally queued offline Room database actions
                repository.flushPendingRoomSyncQueue()
            } catch (e: Exception) {
                Log.e(TAG, "Error during background sales sync", e)
                _lastSyncResult.value = "Sync error: ${e.message}"
            } finally {
                _isServiceSyncing.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Kanak Sales Sync Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization between Firestore and Google Sheets"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Kanak Van Sales")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
