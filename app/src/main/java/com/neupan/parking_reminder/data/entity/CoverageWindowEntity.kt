package com.neupan.parking_reminder.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coverage_window",
    indices = [
        Index(value = ["startAtEpochMillis", "endAtEpochMillis"]),
        Index(value = ["isActive"]),
        Index(value = ["sourceHistoryId"]),
    ],
)
data class CoverageWindowEntity(
    @PrimaryKey val id: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    val sourceHistoryId: String,
    val isActive: Boolean,
    val createdAtEpochMillis: Long,
)
