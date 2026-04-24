package com.neupan.parking_reminder.domain.model

import java.time.Instant

data class ParkingSnapshot(
    val now: Instant,
    val activeSession: ParkingSession?,
    val matchedCoverageWindow: CoverageWindow?,
    val billingQuote: BillingQuote?,
    val nextReminderPlan: ReminderPlan?,
)
