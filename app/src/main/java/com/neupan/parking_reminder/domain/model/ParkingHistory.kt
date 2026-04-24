package com.neupan.parking_reminder.domain.model

import java.time.Duration
import java.time.Instant

data class ParkingHistory(
    val id: String,
    val entryAt: Instant,
    val exitAt: Instant,
    val duration: Duration,
    val feeYuan: Int,
    val wasCovered: Boolean,
    val coverageWindowId: String?,
    val createdAt: Instant,
)
