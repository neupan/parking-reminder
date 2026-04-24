package com.neupan.parking_reminder.domain.model

import java.time.Instant

data class ReminderPlan(
    val sessionId: String,
    val reminderType: ReminderType,
    val triggerAt: Instant,
    val targetFeeYuan: Int,
)
