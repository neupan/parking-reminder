package com.neupan.parking_reminder.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
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
) : ReminderNotifier {
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
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "showReminder() notification posted (id=$NOTIFICATION_ID)")
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
        Log.d(TAG, "ensureChannel() existing=${existing != null} " +
            "importance=${existing?.importance} sound=${existing?.sound}")

        val channel = NotificationChannel(
            CHANNEL_ID,
            "停车提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "停车费用即将变化时提醒"
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            enableVibration(true)
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
        val after = nm.getNotificationChannel(CHANNEL_ID)
        Log.d(TAG, "ensureChannel() after create: importance=${after?.importance} " +
            "sound=${after?.sound} vibration=${after?.shouldVibrate()}")
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
    }
}
