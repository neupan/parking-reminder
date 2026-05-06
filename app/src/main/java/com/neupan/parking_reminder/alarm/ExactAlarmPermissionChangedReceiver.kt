package com.neupan.parking_reminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neupan.parking_reminder.ParkingReminderApp
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason

class ExactAlarmPermissionChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        launchAsync(context) {
            val app = context.applicationContext as ParkingReminderApp
            app.appContainer.reminderResyncService.resync(
                ReminderSyncReason.EXACT_ALARM_PERMISSION_CHANGED,
            )
        }
    }
}
