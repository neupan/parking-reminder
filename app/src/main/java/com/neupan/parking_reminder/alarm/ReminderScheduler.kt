package com.neupan.parking_reminder.alarm

import com.neupan.parking_reminder.alarm.model.ReminderScheduleResult
import com.neupan.parking_reminder.domain.model.ReminderPlan

interface ReminderScheduler {
    suspend fun schedule(plan: ReminderPlan): ReminderScheduleResult

    suspend fun cancelCurrent(): ReminderScheduleResult

    fun canScheduleExactAlarms(): Boolean
}
