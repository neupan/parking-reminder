package com.neupan.parking_reminder.alarm

import android.content.Context
import android.util.Log
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
    private val context: Context,
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
        Log.d(TAG, "resync() reason=$reason now=$now activeSession=${activeSession?.id}")

        if (activeSession == null) {
            Log.d(TAG, "resync() no active session → cancel")
            ParkingMonitorService.stop(context)
            cancelAndMark(now)
            return
        }

        val snapshot = resolveSnapshot(
            session = activeSession,
            now = now,
        )
        val nextPlan = snapshot.nextReminderPlan
        Log.d(TAG, "resync() status=${snapshot.billingQuote?.status} nextPlan=$nextPlan")

        if (nextPlan == null) {
            Log.d(TAG, "resync() no next plan → cancel")
            ParkingMonitorService.stop(context)
            cancelAndMark(now)
            return
        }

        Log.d(TAG, "resync() scheduling alarm: type=${nextPlan.reminderType} triggerAt=${nextPlan.triggerAt} " +
            "inMs=${nextPlan.triggerAt.toEpochMilli() - now.toEpochMilli()}ms fee=${nextPlan.targetFeeYuan}")
        when (val result = reminderScheduler.schedule(nextPlan)) {
            is ReminderScheduleResult.Success -> {
                Log.d(TAG, "resync() alarm scheduled OK")
                ParkingMonitorService.start(context, "停车监控中 · 提醒已安排")
                reminderStateRepository.markScheduled(
                    plan = nextPlan,
                    scheduledAt = now,
                )
            }
            is ReminderScheduleResult.Failure -> {
                Log.e(TAG, "resync() alarm schedule FAILED: ${result.reason}")
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
        Log.d(TAG, "handleAlarm() type=${firedPlan.reminderType} triggerAt=${firedPlan.triggerAt} " +
            "now=$now delay=${now.toEpochMilli() - firedPlan.triggerAt.toEpochMilli()}ms")
        reminderStateRepository.markFired(
            plan = firedPlan,
            firedAt = now,
        )

        val activeSession = parkingRepository.getActiveSession()
        if (activeSession == null) {
            Log.d(TAG, "handleAlarm() no active session → cancel")
            cancelAndMark(now)
            return
        }

        if (activeSession.id != payload.sessionId) {
            Log.w(TAG, "handleAlarm() session mismatch: active=${activeSession.id} payload=${payload.sessionId}")
            resync(ReminderSyncReason.ALARM_FIRED)
            return
        }

        val snapshot = resolveSnapshot(
            session = activeSession,
            now = now,
        )
        Log.d(TAG, "handleAlarm() showing notification for ${firedPlan.reminderType}")
        reminderNotifier.showReminder(
            plan = firedPlan,
            snapshot = snapshot,
        )
        Log.d(TAG, "handleAlarm() notification shown, resyncing for next alarm")
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
            is ReminderScheduleResult.Success -> {
                Log.d(TAG, "cancelAndMark() alarm canceled OK")
                reminderStateRepository.markCanceled(now)
            }
            is ReminderScheduleResult.Failure -> {
                Log.e(TAG, "cancelAndMark() cancel FAILED: ${result.reason}")
                reminderStateRepository.markFailed(
                    plan = null,
                    failedAt = now,
                    reason = result.reason,
                )
            }
        }
    }

    companion object {
        private const val TAG = "ReminderSync"
    }
}
