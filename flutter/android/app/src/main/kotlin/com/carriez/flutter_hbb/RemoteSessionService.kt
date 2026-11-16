package com.carriez.flutter_hbb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Runs only while this device controls a remote one. Android 15+ cuts network access for an
 * app a few seconds after it leaves the screen (blocked reason APP_BACKGROUND), which drops the
 * remote session when the user switches apps; a foreground service keeps the process allowed.
 */
class RemoteSessionService : Service() {
    companion object {
        private const val TAG = "RemoteSessionService"
        private const val CHANNEL_ID = "RustDeskRemoteSession"
        private const val NOTIFY_ID = 2
        private const val EXTRA_PEER = "peer"

        fun start(context: Context, peer: String?) {
            val intent = Intent(context, RemoteSessionService::class.java)
                .putExtra(EXTRA_PEER, peer)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Refused when the app is no longer in the foreground; the session still works
                // while the app stays visible.
                Log.w(TAG, "Failed to start remote session service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteSessionService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(intent?.getStringExtra(EXTRA_PEER))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFY_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    private fun buildNotification(peer: String?): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Remote session",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, pendingFlags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_stat_logo)
            .setContentTitle("RustDesk")
            .setContentText(peer ?: "")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
