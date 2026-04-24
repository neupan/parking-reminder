package com.neupan.parking_reminder.domain.model

import java.time.Instant

data class ParkingSession(
    val id: String,
    val entryAt: Instant,
    val matchedCoverageWindowId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
