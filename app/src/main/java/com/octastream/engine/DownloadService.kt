package com.octastream.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.octastream.data.StorageHelper
import com.octastream.logger.AppLogger
import com.octastream.model.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent Android Foreground Service (dataSync type) that prevents OS Doze Mode throttling
 * and displays live multi-segment transfer telemetry in the system notification shade.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val binder = LocalBinder()
    private var monitorJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        AppLogger.info(TAG, "DownloadService foreground lifecycle created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = buildProgressNotification(
            title = "OctaStream Parallel Engine Active",
            contentText = "Initializing multi-segment HTTP Range workers…",
            progressPercent = 0,
            indeterminate = true
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            AppLogger.warn(TAG, "Foreground promotion note: ${e.message}")
        }

        startObservingEngine()
        return START_STICKY
    }

    private fun startObservingEngine() {
        if (monitorJob?.isActive == true) return
        val engine = DownloadEngine.getInstance(applicationContext)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        monitorJob = serviceScope.launch {
            engine.tasks.collectLatest { tasks ->
                val activeTasks = tasks.filter {
                    it.state == DownloadState.DOWNLOADING ||
                        it.state == DownloadState.MUXING ||
                        it.state == DownloadState.EXTRACTING ||
                        it.state == DownloadState.QUEUED
                }

                if (activeTasks.isEmpty()) {
                    // Delay briefly in case another state transition is in flight
                    delay(1500L)
                    val stillActive = engine.tasks.value.any {
                        it.state == DownloadState.DOWNLOADING ||
                            it.state == DownloadState.MUXING ||
                            it.state == DownloadState.QUEUED
                    }
                    if (!stillActive) {
                        AppLogger.info(TAG, "All active transfers finished. Stopping foreground service.")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                } else {
                    val totalSpeed = activeTasks.sumOf { it.speedBytesPerSec }
                    val avgProgress = activeTasks.map { it.overallProgressPercent }.average().toInt()
                    val isMuxing = activeTasks.any { it.state == DownloadState.MUXING }

                    val title = if (isMuxing) {
                        "Muxing DASH Video + Audio Stream…"
                    } else if (activeTasks.size == 1) {
                        activeTasks.first().title
                    } else {
                        "Downloading ${activeTasks.size} Parallel Streams"
                    }

                    val speedFormatted = "${StorageHelper.formatBytes(totalSpeed)}/s"
                    val subtitle = if (isMuxing) {
                        "FFmpeg stream copy (-c copy) in progress"
                    } else {
                        "$avgProgress% • $speedFormatted • ${activeTasks.sumOf { it.segments.size }} Threads"
                    }

                    val notification = buildProgressNotification(
                        title = title,
                        contentText = subtitle,
                        progressPercent = avgProgress,
                        indeterminate = isMuxing
                    )
                    runCatching {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    }
                }
            }
        }
    }

    private fun buildProgressNotification(
        title: String,
        contentText: String,
        progressPercent: Int,
        indeterminate: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        AppLogger.info(TAG, "DownloadService stopped.")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "octastream_transfer_channel"
        private const val NOTIFICATION_ID = 8088

        fun startOrUpdateService(context: Context) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, DownloadService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }.onFailure { e ->
                AppLogger.warn(TAG, "Unable to start foreground service notification: ${e.message}")
            }
        }
    }
}
