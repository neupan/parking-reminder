package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neupan.parking_reminder.ParkingReminderApp
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason

class TimezoneChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return

        launchAsync(context) {
            val app = context.applicationContext as ParkingReminderApp
            app.appContainer.reminderResyncService.resync(ReminderSyncReason.TIMEZONE_CHANGED)
        }
    }
}
