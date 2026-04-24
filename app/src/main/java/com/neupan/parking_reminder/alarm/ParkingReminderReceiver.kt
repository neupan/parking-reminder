package com.neupan.parking_reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.neupan.parking_reminder.ParkingReminderApp
import com.neupan.parking_reminder.alarm.model.ReminderAlarmPayload
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason

class ParkingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive() action=${intent.action}")
        launchAsync(context) {
            val app = context.applicationContext as ParkingReminderApp
            val payload = ReminderAlarmPayload.from(intent)
            Log.d(TAG, "onReceive() payload=$payload")
            if (payload == null) {
                Log.w(TAG, "onReceive() payload is null → fallback resync")
                app.appContainer.reminderResyncService.resync(ReminderSyncReason.ALARM_FIRED)
            } else {
                Log.d(TAG, "onReceive() handling alarm: type=${payload.reminderType} " +
                    "session=${payload.sessionId} fee=${payload.targetFeeYuan}")
                app.appContainer.reminderResyncService.handleAlarm(payload)
            }
        }
    }

    companion object {
        private const val TAG = "ParkingReceiver"
    }
}
