package com.neupan.parking_reminder.domain.model

import java.time.Instant

data class CoverageWindow(
    val id: String,
    val startAt: Instant,
    val endAt: Instant,
    val sourceHistoryId: String,
    val isActive: Boolean,
    val createdAt: Instant,
)
