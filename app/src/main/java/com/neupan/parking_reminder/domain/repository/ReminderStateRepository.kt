package com.neupan.parking_reminder.domain.repository

import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.model.ReminderType
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ReminderStateRepository {
    fun observeScheduleState(): Flow<ReminderScheduleState?>

    suspend fun getScheduleState(): ReminderScheduleState?

    suspend fun markScheduled(plan: ReminderPlan, scheduledAt: Instant)

    suspend fun markFired(plan: ReminderPlan, firedAt: Instant)

    suspend fun markCanceled(canceledAt: Instant)

    suspend fun markFailed(
        plan: ReminderPlan?,
        failedAt: Instant,
        reason: String,
    )
}

data class ReminderScheduleState(
    val sessionId: String?,
    val reminderType: ReminderType?,
    val triggerAt: Instant?,
    val targetFeeYuan: Int?,
    val scheduledAt: Instant?,
    val status: ReminderScheduleStatus,
    val failureReason: String?,
    val updatedAt: Instant,
)

enum class ReminderScheduleStatus {
    NONE,
    SCHEDULED,
    FIRED,
    CANCELED,
    FAILED,
}
