package com.neupan.parking_reminder.alarm

import com.neupan.parking_reminder.alarm.model.ReminderAlarmPayload
import com.neupan.parking_reminder.alarm.model.ReminderScheduleResult
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason
import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingSnapshot
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.repository.ParkingRepository
import com.neupan.parking_reminder.domain.repository.ReminderStateRepository
import com.neupan.parking_reminder.domain.rule.ParkingStateResolver
import com.neupan.parking_reminder.domain.time.AppClock
import java.time.Instant

class ReminderResyncService(
    private val parkingRepository: ParkingRepository,
    private val reminderStateRepository: ReminderStateRepository,
    private val parkingStateResolver: ParkingStateResolver,
    private val reminderScheduler: ReminderScheduler,
    private val reminderNotifier: ReminderNotifier,
    private val clock: AppClock,
) {
    suspend fun resync(reason: ReminderSyncReason) {
        val now = clock.now()
        val activeSession = parkingRepository.getActiveSession()

        if (activeSession == null) {
            cancelAndMark(now)
            return
        }

        val snapshot = resolveSnapshot(
            session = activeSession,
            now = now,
        )
        val nextPlan = snapshot.nextReminderPlan

        if (nextPlan == null) {
            cancelAndMark(now)
            return
        }

        when (val result = reminderScheduler.schedule(nextPlan)) {
            is ReminderScheduleResult.Success -> {
                reminderStateRepository.markScheduled(
                    plan = nextPlan,
                    scheduledAt = now,
                )
            }
            is ReminderScheduleResult.Failure -> {
                reminderStateRepository.markFailed(
                    plan = nextPlan,
                    failedAt = now,
                    reason = "${reason.name}: ${result.reason}",
                )
            }
        }
    }

    suspend fun handleAlarm(payload: ReminderAlarmPayload) {
        val now = clock.now()
        val firedPlan = payload.toReminderPlan()
        reminderStateRepository.markFired(
            plan = firedPlan,
            firedAt = now,
        )

        val activeSession = parkingRepository.getActiveSession()
        if (activeSession == null) {
            cancelAndMark(now)
            return
        }

        if (activeSession.id != payload.sessionId) {
            resync(ReminderSyncReason.ALARM_FIRED)
            return
        }

        val snapshot = resolveSnapshot(
            session = activeSession,
            now = now,
        )
        reminderNotifier.showReminder(
            plan = firedPlan,
            snapshot = snapshot,
        )
        resync(ReminderSyncReason.ALARM_FIRED)
    }

    private suspend fun resolveSnapshot(
        session: ParkingSession,
        now: Instant,
    ): ParkingSnapshot {
        val matchedCoverageWindow = findMatchedCoverageWindow(session)
        return parkingStateResolver.resolve(
            now = now,
            activeSession = session,
            matchedCoverageWindow = matchedCoverageWindow,
        )
    }

    private suspend fun findMatchedCoverageWindow(session: ParkingSession): CoverageWindow? {
        return session.matchedCoverageWindowId
            ?.let { parkingRepository.getCoverageWindow(it) }
            ?: parkingRepository.findCoveringWindow(session.entryAt)
    }

    private suspend fun cancelAndMark(now: Instant) {
        when (val result = reminderScheduler.cancelCurrent()) {
            is ReminderScheduleResult.Success -> reminderStateRepository.markCanceled(now)
            is ReminderScheduleResult.Failure -> {
                reminderStateRepository.markFailed(
                    plan = null,
                    failedAt = now,
                    reason = result.reason,
                )
            }
        }
    }
}
