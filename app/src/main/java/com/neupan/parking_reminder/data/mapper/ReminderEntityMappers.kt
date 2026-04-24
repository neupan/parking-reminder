package com.neupan.parking_reminder.data.mapper

import com.neupan.parking_reminder.data.entity.ReminderScheduleStateEntity
import com.neupan.parking_reminder.domain.model.ReminderType
import com.neupan.parking_reminder.domain.repository.ReminderScheduleState
import com.neupan.parking_reminder.domain.repository.ReminderScheduleStatus
import java.time.Instant

fun ReminderScheduleStateEntity.toDomain(): ReminderScheduleState {
    return ReminderScheduleState(
        sessionId = sessionId,
        reminderType = reminderType?.let(ReminderType::valueOf),
        triggerAt = triggerAtEpochMillis?.let(Instant::ofEpochMilli),
        targetFeeYuan = targetFeeYuan,
        scheduledAt = scheduledAtEpochMillis?.let(Instant::ofEpochMilli),
        status = ReminderScheduleStatus.valueOf(status),
        failureReason = failureReason,
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

fun ReminderScheduleState.toEntity(): ReminderScheduleStateEntity {
    return ReminderScheduleStateEntity(
        sessionId = sessionId,
        reminderType = reminderType?.name,
        triggerAtEpochMillis = triggerAt?.toEpochMilli(),
        targetFeeYuan = targetFeeYuan,
        scheduledAtEpochMillis = scheduledAt?.toEpochMilli(),
        status = status.name,
        failureReason = failureReason,
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )
}
