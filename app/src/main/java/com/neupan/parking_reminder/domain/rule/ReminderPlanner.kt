package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.BillingQuote
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.model.ReminderType
import java.time.Duration
import java.time.Instant

class ReminderPlanner {
    fun planNextReminder(
        session: ParkingSession,
        quote: BillingQuote,
        now: Instant,
    ): ReminderPlan? {
        return when (val status = quote.status) {
            ParkingStatus.Idle -> null
            is ParkingStatus.ParkingFree,
            is ParkingStatus.ParkingCharged -> planFreshSessionReminder(session, now)
            is ParkingStatus.ParkingCovered -> planCoveredSessionReminder(
                session = session,
                coverageEndAt = status.coverageWindow.endAt,
                now = now,
            )
            is ParkingStatus.PostCoverageCharged -> planPostCoverageReminder(
                session = session,
                coverageEndAt = status.coverageWindow.endAt,
                now = now,
            )
        }
    }

    private fun planFreshSessionReminder(
        session: ParkingSession,
        now: Instant,
    ): ReminderPlan {
        val freeEndingReminderAt = session.entryAt.plus(FREE_DURATION).minus(REMINDER_LEAD_TIME)
        if (freeEndingReminderAt.isAfter(now)) {
            return ReminderPlan(
                sessionId = session.id,
                reminderType = ReminderType.FREE_ENDING,
                triggerAt = freeEndingReminderAt,
                targetFeeYuan = BASE_FEE_YUAN,
            )
        }

        return planCycleReminder(
            sessionId = session.id,
            firstBoundaryAt = session.entryAt.plus(BILLING_CYCLE),
            firstTargetFeeYuan = BASE_FEE_YUAN * 2,
            now = now,
        )
    }

    private fun planCoveredSessionReminder(
        session: ParkingSession,
        coverageEndAt: Instant,
        now: Instant,
    ): ReminderPlan {
        val coverageEndingReminderAt = coverageEndAt.minus(REMINDER_LEAD_TIME)
        if (coverageEndingReminderAt.isAfter(now)) {
            return ReminderPlan(
                sessionId = session.id,
                reminderType = ReminderType.COVERAGE_ENDING,
                triggerAt = coverageEndingReminderAt,
                targetFeeYuan = BASE_FEE_YUAN,
            )
        }

        return planPostCoverageReminder(session, coverageEndAt, now)
    }

    private fun planPostCoverageReminder(
        session: ParkingSession,
        coverageEndAt: Instant,
        now: Instant,
    ): ReminderPlan {
        return planCycleReminder(
            sessionId = session.id,
            firstBoundaryAt = coverageEndAt.plus(BILLING_CYCLE),
            firstTargetFeeYuan = BASE_FEE_YUAN * 2,
            now = now,
        )
    }

    private fun planCycleReminder(
        sessionId: String,
        firstBoundaryAt: Instant,
        firstTargetFeeYuan: Int,
        now: Instant,
    ): ReminderPlan {
        val firstReminderAt = firstBoundaryAt.minus(REMINDER_LEAD_TIME)
        val cycleOffset = if (firstReminderAt.isAfter(now)) {
            0
        } else {
            Duration.between(firstReminderAt, now).toMillis() / BILLING_CYCLE.toMillis() + 1
        }
        val triggerAt = firstReminderAt.plus(BILLING_CYCLE.multipliedBy(cycleOffset))
        val targetFeeYuan = firstTargetFeeYuan + (BASE_FEE_YUAN * cycleOffset.toInt())

        return ReminderPlan(
            sessionId = sessionId,
            reminderType = ReminderType.FEE_INCREASING,
            triggerAt = triggerAt,
            targetFeeYuan = targetFeeYuan,
        )
    }

    private companion object {
        const val BASE_FEE_YUAN = 5
        val FREE_DURATION: Duration = Duration.ofHours(1)
        val BILLING_CYCLE: Duration = Duration.ofHours(12)
        val REMINDER_LEAD_TIME: Duration = Duration.ofMinutes(10)
    }
}
