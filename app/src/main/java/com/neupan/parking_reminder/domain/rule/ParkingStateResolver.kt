package com.neupan.parking_reminder.domain.rule

import android.util.Log
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
            Log.d(TAG, "resolve() no active session → Idle")
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
        Log.d(TAG, "resolve() status=${quote.status} fee=${quote.currentFeeYuan} nextCharge=${quote.nextChargeAt}")
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

    companion object {
        private const val TAG = "StateResolver"
    }
}
