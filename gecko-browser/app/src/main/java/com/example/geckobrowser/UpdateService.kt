package com.example.geckobrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that downloads the pending OTA update in the background.
 * The notification stays silent (low importance, no sound) and reports only
 * progress; the completion notice asks the user to install, and tap-to-install
 * launches the system package installer.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val apkUrl = intent.getStringExtra(EXTRA_APK_URL) ?: return START_NOT_STICKY
                val runNumber = intent.getIntExtra(EXTRA_RUN_NUMBER, -1)
                val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
                val apkSize = intent.getLongExtra(EXTRA_APK_SIZE, 0L)
                startForeground(NOTIFICATION_ID, notification(displayName, 0f, indeterminate = true))
                scope.launch { download(apkUrl, runNumber, displayName, apkSize) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun download(apkUrl: String, runNumber: Int, displayName: String, apkSize: Long) {
        val info = UpdateInfo(
            displayName = displayName,
            runNumber = runNumber,
            apkUrl = apkUrl,
            releaseUrl = "",
            apkSize = apkSize,
        )
        try {
            AppUpdater.progress.value = 0f
            val file = AppUpdater.download(this, info) { fraction ->
                AppUpdater.progress.value = fraction
                notifySafe(notificationFor(displayName, fraction))
            }
            AppUpdater.progress.value = -1f
            if (file != null) {
                notifySafe(doneNotification(displayName, file))
            } else {
                notifySafe(failedNotification(displayName))
            }
        } catch (_: Throwable) {
            AppUpdater.progress.value = -1f
            notifySafe(failedNotification(displayName))
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun doneNotification(displayName: String, file: File): Notification {
        val install = AppUpdater.installIntent(this, file)
        val pending = PendingIntent.getActivity(
            this,
            (System.nanoTime() and 0xffff).toInt(),
            install,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("horiZen update ready")
            .setContentText("$displayName is downloaded — tap to install")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setSilent(true)
            .build()
    }

    private fun failedNotification(displayName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("horiZen update failed")
            .setContentText("$displayName could not be downloaded — tap to retry")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setSilent(true)
            .build()

    private fun notificationFor(displayName: String, fraction: Float): Notification {
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        return notification(displayName, fraction, indeterminate = false)
    }

    private fun notification(displayName: String, fraction: Float, indeterminate: Boolean): Notification {
        val percent = if (indeterminate) 0 else (fraction * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("horiZen update")
            .setContentText(
                if (indeterminate) "Downloading in the background…"
                else "Downloading $displayName — $percent%"
            )
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun notifySafe(notification: Notification) {
        try {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Notifications are best-effort; the update completes regardless.
        }
    }

    private fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Silent, ongoing progress while a new build downloads in the background"
                setShowBadge(false)
            }
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.geckobrowser.UPDATE_START"
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_RUN_NUMBER = "run_number"
        const val EXTRA_DISPLAY_NAME = "display_name"
        const val EXTRA_APK_SIZE = "apk_size"

        private const val CHANNEL_ID = "horiZen-updates"
        private const val NOTIFICATION_ID = 4083

        /** Starts the background download; returns immediately. */
        fun start(context: Context, info: UpdateInfo) {
            val intent = Intent(context, UpdateService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_APK_URL, info.apkUrl)
                putExtra(EXTRA_RUN_NUMBER, info.runNumber)
                putExtra(EXTRA_DISPLAY_NAME, info.displayName)
                putExtra(EXTRA_APK_SIZE, info.apkSize)
            }
            context.startForegroundService(intent)
        }
    }
}
