package com.neupan.parking_reminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.neupan.parking_reminder.MainActivity
import com.neupan.parking_reminder.R

class AlarmSoundService : Service() {

    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopAlarm() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() action=${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val uriString = intent?.getStringExtra(EXTRA_RINGTONE_URI)
        val text = intent?.getStringExtra(EXTRA_NOTIFICATION_TEXT) ?: "提醒铃声播放中"

        val notification = buildForegroundNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FG_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(FG_NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "startForeground() called")

        if (uriString != null) {
            playRingtone(Uri.parse(uriString))
        }

        handler.removeCallbacks(autoStopRunnable)
        handler.postDelayed(autoStopRunnable, ALARM_DURATION_MS)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        handler.removeCallbacks(autoStopRunnable)
        ringtone?.let { if (it.isPlaying) it.stop() }
        ringtone = null
        isRunning = false
        super.onDestroy()
    }

    private fun playRingtone(uri: Uri) {
        try {
            ringtone?.let { if (it.isPlaying) it.stop() }

            val r = RingtoneManager.getRingtone(this, uri)
            if (r == null) {
                Log.e(TAG, "getRingtone returned null for $uri")
                stopAlarm()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                r.isLooping = true
            }
            r.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            r.play()
            ringtone = r
            Log.d(TAG, "playRingtone() playing=${r.isPlaying} uri=$uri")
        } catch (e: Exception) {
            Log.e(TAG, "playRingtone() EXCEPTION", e)
            stopAlarm()
        }
    }

    private fun stopAlarm() {
        Log.d(TAG, "stopAlarm()")
        handler.removeCallbacks(autoStopRunnable)
        ringtone?.let {
            if (it.isPlaying) it.stop()
            Log.d(TAG, "stopAlarm() ringtone stopped")
        }
        ringtone = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildForegroundNotification(text: String): Notification {
        ensureServiceChannel()

        val fullScreenIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmSoundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("停车缴费提醒 · 铃声播放中")
            .setContentText(text)
            .setContentIntent(fullScreenIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .addAction(0, "停止铃声", stopIntent)
            .setSilent(true)
            .build()
    }

    private fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val existing = nm.getNotificationChannel(SERVICE_CHANNEL_ID)
        if (existing != null && existing.importance >= NotificationManager.IMPORTANCE_HIGH) return
        if (existing != null) nm.deleteNotificationChannel(SERVICE_CHANNEL_ID)
        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            "提醒铃声",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "提醒铃声播放时的通知，可在此停止铃声"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AlarmSoundSvc"
        private const val SERVICE_CHANNEL_ID = "alarm_sound_service"
        private const val FG_NOTIFICATION_ID = 3002
        private const val ALARM_DURATION_MS = 15_000L

        const val ACTION_STOP = "com.neupan.parking_reminder.action.STOP_ALARM_SOUND"
        const val EXTRA_RINGTONE_URI = "ringtone_uri"
        const val EXTRA_NOTIFICATION_TEXT = "notification_text"

        @Volatile
        var isRunning = false
            private set

        fun startAlarm(context: Context, ringtoneUri: Uri, notificationText: String) {
            Log.d(TAG, "startAlarm() uri=$ringtoneUri")
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                putExtra(EXTRA_RINGTONE_URI, ringtoneUri.toString())
                putExtra(EXTRA_NOTIFICATION_TEXT, notificationText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarm(context: Context) {
            Log.d(TAG, "stopAlarm() static")
            context.startService(
                Intent(context, AlarmSoundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
