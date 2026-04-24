package com.neupan.parking_reminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neupan.parking_reminder.MainActivity
import com.neupan.parking_reminder.R
import com.neupan.parking_reminder.domain.model.ParkingSnapshot
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.model.ReminderType

class AndroidReminderNotifier(
    private val context: Context,
    private val ringtonePreferences: RingtonePreferences,
) : ReminderNotifier {

    private var activeRingtone: Ringtone? = null

    override val isAlarmPlaying: Boolean
        get() = activeRingtone?.isPlaying == true

    override suspend fun showReminder(
        plan: ReminderPlan,
        snapshot: ParkingSnapshot,
    ) {
        val canPost = canPostNotifications()
        Log.d(TAG, "showReminder() type=${plan.reminderType} fee=${plan.targetFeeYuan} canPost=$canPost")
        if (!canPost) {
            Log.e(TAG, "showReminder() ABORTED — POST_NOTIFICATIONS not granted")
            return
        }

        ensureChannel()
        logAudioState()
        val text = contentText(plan)
        Log.d(TAG, "showReminder() posting notification: $text")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("停车缴费提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "showReminder() notification posted (id=$NOTIFICATION_ID)")

        playAlarmSound()
    }

    private fun playAlarmSound() {
        try {
            stopAlarmSound()

            val alarmUri = ringtonePreferences.getAlarmUriResolved(context)
            Log.d(TAG, "playAlarmSound() uri=$alarmUri")

            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            if (ringtone == null) {
                Log.e(TAG, "playAlarmSound() getRingtone returned null!")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.isLooping = true
            }
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            activeRingtone = ringtone
            Log.d(TAG, "playAlarmSound() playing=${ringtone.isPlaying}")

            Handler(Looper.getMainLooper()).postDelayed({
                stopAlarmSound()
            }, ALARM_DURATION_MS)
        } catch (e: Exception) {
            Log.e(TAG, "playAlarmSound() EXCEPTION", e)
        }
    }

    override fun stopAlarmSound() {
        activeRingtone?.let {
            if (it.isPlaying) {
                it.stop()
                Log.d(TAG, "stopAlarmSound() stopped previous ringtone")
            }
        }
        activeRingtone = null
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "canPostNotifications() SDK < 33, auto-granted")
            return true
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "canPostNotifications() SDK=${Build.VERSION.SDK_INT} granted=$granted")
        return granted
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(NotificationManager::class.java)

        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            Log.d(TAG, "ensureChannel() deleting stale channel to force re-create " +
                "(importance=${existing.importance} sound=${existing.sound})")
            nm.deleteNotificationChannel(CHANNEL_ID)
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "停车提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "停车费用即将变化时提醒"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
        val after = nm.getNotificationChannel(CHANNEL_ID)
        Log.d(TAG, "ensureChannel() created: importance=${after?.importance} " +
            "sound=${after?.sound} vibration=${after?.shouldVibrate()}")
    }

    private fun logAudioState() {
        val am = context.getSystemService(AudioManager::class.java)
        val alarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val ringerMode = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
            else -> "UNKNOWN(${am.ringerMode})"
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        val dndFilter = nm.currentInterruptionFilter
        val dndText = when (dndFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "OFF"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "PRIORITY_ONLY"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "TOTAL_SILENCE"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "ALARMS_ONLY"
            else -> "UNKNOWN($dndFilter)"
        }
        Log.d(TAG, "audioState() alarmVolume=$alarmVol/$alarmMax ringer=$ringerMode dnd=$dndText")
    }

    private fun openAppPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentText(plan: ReminderPlan): String {
        return when (plan.reminderType) {
            ReminderType.FREE_ENDING -> "即将开始计费 ${plan.targetFeeYuan} 元，请尽快缴费离场"
            ReminderType.COVERAGE_ENDING -> "已缴费覆盖即将结束，将进入 ${plan.targetFeeYuan} 元计费"
            ReminderType.FEE_INCREASING -> "即将加费到 ${plan.targetFeeYuan} 元，请尽快缴费离场"
        }
    }

    companion object {
        private const val TAG = "Notifier"
        const val CHANNEL_ID = "parking_alarm"
        private const val NOTIFICATION_ID = 3001
        private const val OPEN_APP_REQUEST_CODE = 3002
        private const val ALARM_DURATION_MS = 15_000L
    }
}
