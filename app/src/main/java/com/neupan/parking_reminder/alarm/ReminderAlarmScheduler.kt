package com.neupan.parking_reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.neupan.parking_reminder.MainActivity
import com.neupan.parking_reminder.alarm.model.ReminderAlarmPayload
import com.neupan.parking_reminder.alarm.model.ReminderScheduleResult
import com.neupan.parking_reminder.domain.model.ReminderPlan

class ReminderAlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
) : ReminderScheduler {
    override suspend fun schedule(plan: ReminderPlan): ReminderScheduleResult {
        val canSchedule = canScheduleExactAlarms()
        Log.d(TAG, "schedule() canScheduleExactAlarms=$canSchedule " +
            "type=${plan.reminderType} triggerAt=${plan.triggerAt} epochMs=${plan.triggerAt.toEpochMilli()}")
        if (!canSchedule) {
            Log.e(TAG, "schedule() REJECTED — exact alarm not available")
            return ReminderScheduleResult.Failure("Exact alarm capability is not available.")
        }

        return runCatching {
            val operation = reminderPendingIntent(plan)
            val showIntent = PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, MainActivity::class.java),
                pendingIntentFlags(),
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(plan.triggerAt.toEpochMilli(), showIntent),
                operation,
            )
            Log.d(TAG, "schedule() setAlarmClock called OK")
        }.fold(
            onSuccess = { ReminderScheduleResult.Success(plan) },
            onFailure = {
                Log.e(TAG, "schedule() setAlarmClock EXCEPTION", it)
                ReminderScheduleResult.Failure(it.message ?: "Unable to schedule alarm.")
            },
        )
    }

    override suspend fun cancelCurrent(): ReminderScheduleResult {
        Log.d(TAG, "cancelCurrent()")
        return runCatching {
            alarmManager.cancel(reminderPendingIntent(null))
        }.fold(
            onSuccess = {
                Log.d(TAG, "cancelCurrent() OK")
                ReminderScheduleResult.Success(null)
            },
            onFailure = {
                Log.e(TAG, "cancelCurrent() EXCEPTION", it)
                ReminderScheduleResult.Failure(it.message ?: "Unable to cancel alarm.")
            },
        )
    }

    override fun canScheduleExactAlarms(): Boolean {
        val result = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        Log.d(TAG, "canScheduleExactAlarms() SDK=${Build.VERSION.SDK_INT} result=$result")
        return result
    }

    private fun reminderPendingIntent(plan: ReminderPlan?): PendingIntent {
        val intent = Intent(context, ParkingReminderReceiver::class.java)
            .setAction(ACTION_PARKING_REMINDER)
        plan?.let { ReminderAlarmPayload.from(it).putInto(intent) }

        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        const val ACTION_PARKING_REMINDER = "com.neupan.parking_reminder.action.PARKING_REMINDER"
        private const val REMINDER_REQUEST_CODE = 2001
        private const val OPEN_APP_REQUEST_CODE = 2002
    }
}
