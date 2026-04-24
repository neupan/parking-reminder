package com.neupan.parking_reminder.domain.model

import java.time.Instant

data class BillingQuote(
    val status: ParkingStatus,
    val currentFeeYuan: Int,
    val nextChargeAt: Instant?,
    val nextFeeYuan: Int?,
    val countdownTargetLabel: CountdownTargetLabel,
)

enum class CountdownTargetLabel {
    FREE_ENDS,
    NEXT_FEE_INCREASE,
    COVERAGE_ENDS,
}
