package com.neupan.parking_reminder.alarm

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
import com.neupan.parking_reminder.MainActivity
import com.neupan.parking_reminder.ParkingReminderApp
import com.neupan.parking_reminder.R
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ParkingMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_STATUS_TEXT) ?: "停车监控中"
        Log.d(TAG, "onStartCommand() text=$text")

        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FG_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FG_NOTIFICATION_ID, notification, 0)
        } else {
            startForeground(FG_NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved() → re-registering alarms")
        val app = applicationContext as? ParkingReminderApp ?: return
        serviceScope.launch {
            try {
                app.appContainer.reminderResyncService.resync(ReminderSyncReason.APP_COLD_START)
                Log.d(TAG, "onTaskRemoved() resync done")
            } catch (e: Exception) {
                Log.e(TAG, "onTaskRemoved() resync EXCEPTION", e)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        isRunning = false
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("停车提醒")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "停车监控",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "停车期间保持后台运行，确保提醒准时触发"
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ParkMonitorSvc"
        private const val CHANNEL_ID = "parking_monitor"
        private const val FG_NOTIFICATION_ID = 3003
        const val EXTRA_STATUS_TEXT = "status_text"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context, statusText: String = "停车监控中 · 提醒已安排") {
            if (isRunning) {
                Log.d(TAG, "start() already running, updating notification")
            }
            val intent = Intent(context, ParkingMonitorService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop()")
            context.stopService(Intent(context, ParkingMonitorService::class.java))
        }
    }
}
