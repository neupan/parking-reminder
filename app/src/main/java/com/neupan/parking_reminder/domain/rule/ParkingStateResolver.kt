package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingSnapshot
import java.time.Instant

class ParkingStateResolver(
    private val billingCalculator: BillingCalculator = BillingCalculator(),
    private val reminderPlanner: ReminderPlanner = ReminderPlanner(),
) {
    fun resolve(
        now: Instant,
        activeSession: ParkingSession?,
        matchedCoverageWindow: CoverageWindow?,
    ): ParkingSnapshot {
        if (activeSession == null) {
            return ParkingSnapshot(
                now = now,
                activeSession = null,
                matchedCoverageWindow = null,
                billingQuote = null,
                nextReminderPlan = null,
            )
        }

        val quote = billingCalculator.calculate(
            session = activeSession,
            matchedCoverageWindow = matchedCoverageWindow,
            now = now,
        )
        val reminderPlan = reminderPlanner.planNextReminder(
            session = activeSession,
            quote = quote,
            now = now,
        )

        return ParkingSnapshot(
            now = now,
            activeSession = activeSession,
            matchedCoverageWindow = matchedCoverageWindow,
            billingQuote = quote,
            nextReminderPlan = reminderPlan,
        )
    }
}
