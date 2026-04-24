package com.neupan.parking_reminder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_schedule_state")
data class ReminderScheduleStateEntity(
    @PrimaryKey val id: String = CURRENT_ID,
    val sessionId: String?,
    val reminderType: String?,
    val triggerAtEpochMillis: Long?,
    val targetFeeYuan: Int?,
    val scheduledAtEpochMillis: Long?,
    val status: String,
    val failureReason: String?,
    val updatedAtEpochMillis: Long,
) {
    companion object {
        const val CURRENT_ID = "current"
    }
}
