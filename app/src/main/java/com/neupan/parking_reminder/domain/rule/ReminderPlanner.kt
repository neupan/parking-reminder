package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.BillingQuote
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import com.neupan.parking_reminder.domain.model.ReminderPlan
import com.neupan.parking_reminder.domain.model.ReminderType
import java.time.Duration
import java.time.Instant

class ReminderPlanner(
    private val ruleConfigProvider: ParkingRuleConfigProvider = FixedParkingRuleConfigProvider(),
) {
    constructor(ruleConfig: ParkingRuleConfig) : this(FixedParkingRuleConfigProvider(ruleConfig))

    fun planNextReminder(
        session: ParkingSession,
        quote: BillingQuote,
        now: Instant,
    ): ReminderPlan? {
        val ruleConfig = ruleConfigProvider.current
        return when (val status = quote.status) {
            ParkingStatus.Idle -> null
            is ParkingStatus.ParkingFree,
            is ParkingStatus.ParkingCharged -> planFreshSessionReminder(session, now, ruleConfig)
            is ParkingStatus.ParkingCovered -> planCoveredSessionReminder(
                session = session,
                coverageEndAt = status.coverageWindow.endAt,
                now = now,
                ruleConfig = ruleConfig,
            )
            is ParkingStatus.PostCoverageCharged -> planPostCoverageReminder(
                session = session,
                coverageEndAt = status.coverageWindow.endAt,
                now = now,
                ruleConfig = ruleConfig,
            )
        }
    }

    private fun planFreshSessionReminder(
        session: ParkingSession,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): ReminderPlan {
        val freeEndingReminderAt = session.entryAt.plus(ruleConfig.freeDuration).minus(ruleConfig.reminderLeadTime)
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
            firstBoundaryAt = session.entryAt.plus(ruleConfig.billingCycle),
            firstTargetFeeYuan = BASE_FEE_YUAN * 2,
            now = now,
            ruleConfig = ruleConfig,
        )
    }

    private fun planCoveredSessionReminder(
        session: ParkingSession,
        coverageEndAt: Instant,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): ReminderPlan {
        val coverageEndingReminderAt = coverageEndAt.minus(ruleConfig.reminderLeadTime)
        if (coverageEndingReminderAt.isAfter(now)) {
            return ReminderPlan(
                sessionId = session.id,
                reminderType = ReminderType.COVERAGE_ENDING,
                triggerAt = coverageEndingReminderAt,
                targetFeeYuan = BASE_FEE_YUAN,
            )
        }

        return planPostCoverageReminder(session, coverageEndAt, now, ruleConfig)
    }

    private fun planPostCoverageReminder(
        session: ParkingSession,
        coverageEndAt: Instant,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): ReminderPlan {
        return planCycleReminder(
            sessionId = session.id,
            firstBoundaryAt = coverageEndAt.plus(ruleConfig.billingCycle),
            firstTargetFeeYuan = BASE_FEE_YUAN * 2,
            now = now,
            ruleConfig = ruleConfig,
        )
    }

    private fun planCycleReminder(
        sessionId: String,
        firstBoundaryAt: Instant,
        firstTargetFeeYuan: Int,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): ReminderPlan {
        val firstReminderAt = firstBoundaryAt.minus(ruleConfig.reminderLeadTime)
        val cycleOffset = if (firstReminderAt.isAfter(now)) {
            0
        } else {
            Duration.between(firstReminderAt, now).toMillis() / ruleConfig.billingCycle.toMillis() + 1
        }
        val triggerAt = firstReminderAt.plus(ruleConfig.billingCycle.multipliedBy(cycleOffset))
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
    }
}
