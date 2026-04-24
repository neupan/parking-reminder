package com.neupan.parking_reminder.data.repository

import com.neupan.parking_reminder.data.dao.ReminderScheduleStateDao
import com.neupan.parking_reminder.data.mapper.toDomain
import com.neupan.parking_reminder.data.mapper.toEntity
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.repository.ReminderScheduleState
import com.neupan.parking_reminder.domain.repository.ReminderScheduleStatus
import com.neupan.parking_reminder.domain.repository.ReminderStateRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReminderStateRepository(
    private val reminderScheduleStateDao: ReminderScheduleStateDao,
) : ReminderStateRepository {
    override fun observeScheduleState(): Flow<ReminderScheduleState?> {
        return reminderScheduleStateDao.observeCurrent().map { it?.toDomain() }
    }

    override suspend fun getScheduleState(): ReminderScheduleState? {
        return reminderScheduleStateDao.getCurrent()?.toDomain()
    }

    override suspend fun markScheduled(plan: ReminderPlan, scheduledAt: Instant) {
        upsert(
            plan = plan,
            scheduledAt = scheduledAt,
            status = ReminderScheduleStatus.SCHEDULED,
            failureReason = null,
            updatedAt = scheduledAt,
        )
    }

    override suspend fun markFired(plan: ReminderPlan, firedAt: Instant) {
        val existing = getScheduleState()
        upsert(
            plan = plan,
            scheduledAt = existing?.scheduledAt,
            status = ReminderScheduleStatus.FIRED,
            failureReason = null,
            updatedAt = firedAt,
        )
    }

    override suspend fun markCanceled(canceledAt: Instant) {
        reminderScheduleStateDao.upsert(
            ReminderScheduleState(
                sessionId = null,
                reminderType = null,
                triggerAt = null,
                targetFeeYuan = null,
                scheduledAt = null,
                status = ReminderScheduleStatus.CANCELED,
                failureReason = null,
                updatedAt = canceledAt,
            ).toEntity(),
        )
    }

    override suspend fun markFailed(
        plan: ReminderPlan?,
        failedAt: Instant,
        reason: String,
    ) {
        upsert(
            plan = plan,
            scheduledAt = null,
            status = ReminderScheduleStatus.FAILED,
            failureReason = reason,
            updatedAt = failedAt,
        )
    }

    private suspend fun upsert(
        plan: ReminderPlan?,
        scheduledAt: Instant?,
        status: ReminderScheduleStatus,
        failureReason: String?,
        updatedAt: Instant,
    ) {
        reminderScheduleStateDao.upsert(
            ReminderScheduleState(
                sessionId = plan?.sessionId,
                reminderType = plan?.reminderType,
                triggerAt = plan?.triggerAt,
                targetFeeYuan = plan?.targetFeeYuan,
                scheduledAt = scheduledAt,
                status = status,
                failureReason = failureReason,
                updatedAt = updatedAt,
            ).toEntity(),
        )
    }
}
