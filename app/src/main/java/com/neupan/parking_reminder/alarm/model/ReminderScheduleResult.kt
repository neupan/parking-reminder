package com.neupan.parking_reminder.alarm.model

import com.neupan.parking_reminder.domain.model.ReminderPlan

sealed interface ReminderScheduleResult {
    data class Success(val plan: ReminderPlan?) : ReminderScheduleResult

    data class Failure(val reason: String) : ReminderScheduleResult
}
