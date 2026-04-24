package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neupan.parking_reminder.ParkingReminderApp
import com.neupan.parking_reminder.alarm.model.ReminderAlarmPayload
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason

class ParkingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        launchAsync(context) {
            val app = context.applicationContext as ParkingReminderApp
            val payload = ReminderAlarmPayload.from(intent)
            if (payload == null) {
                app.appContainer.reminderResyncService.resync(ReminderSyncReason.ALARM_FIRED)
            } else {
                app.appContainer.reminderResyncService.handleAlarm(payload)
            }
        }
    }
}
