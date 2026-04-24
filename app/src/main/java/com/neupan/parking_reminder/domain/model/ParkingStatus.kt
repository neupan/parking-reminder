package com.neupan.parking_reminder.domain.model

import java.time.Instant

sealed interface ParkingStatus {
    data object Idle : ParkingStatus

    data class ParkingFree(
        val freeEndsAt: Instant,
    ) : ParkingStatus

    data class ParkingCharged(
        val currentFeeYuan: Int,
        val nextChargeAt: Instant,
        val nextFeeYuan: Int,
    ) : ParkingStatus

    data class ParkingCovered(
        val coverageWindow: CoverageWindow,
    ) : ParkingStatus

    data class PostCoverageCharged(
        val coverageWindow: CoverageWindow,
        val currentFeeYuan: Int,
        val nextChargeAt: Instant,
        val nextFeeYuan: Int,
    ) : ParkingStatus
}
