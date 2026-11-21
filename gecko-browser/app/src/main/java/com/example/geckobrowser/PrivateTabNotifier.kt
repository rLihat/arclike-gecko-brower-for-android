package com.example.geckobrowser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Private tabs surface as a real system notification, never as in-app chrome.
 * Tapping the notification (or its action) asks to close them; swiping it away
 * closes them directly.
 */
object PrivateTabNotifier {
    private const val CHANNEL = "private_tabs"
    private const val ID = 7701
    const val ACTION_CLOSE = "com.example.geckobrowser.CLOSE_PRIVATE"
    const val ACTION_DISMISS = "com.example.geckobrowser.DISMISS_PRIVATE"

    fun sync(context: Context, count: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (count <= 0) {
            nm.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL, "Private tabs", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Private browsing status" }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val close = PendingIntent.getBroadcast(
            context, 1, Intent(ACTION_CLOSE).setPackage(context.packageName), flags,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 2, Intent(ACTION_DISMISS).setPackage(context.packageName), flags,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Private tabs open")
            .setContentText(
                if (count == 1) "1 private tab is open" else "$count private tabs are open",
            )
            .setContentIntent(close)
            .setDeleteIntent(dismiss)
            .addAction(0, "Close private", close)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, notification) }
    }
}

class PrivateTabReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vm = (context.applicationContext as? ArcApplication)?.browserVm ?: return
        when (intent.action) {
            PrivateTabNotifier.ACTION_CLOSE -> vm.requestClosePrivateTabs()
            PrivateTabNotifier.ACTION_DISMISS -> vm.dismissPrivateNotice()
        }
    }
}
