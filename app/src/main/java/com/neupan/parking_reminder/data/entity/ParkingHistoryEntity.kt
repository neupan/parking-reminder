package com.neupan.parking_reminder.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parking_history",
    indices = [
        Index(value = ["exitAtEpochMillis"]),
        Index(value = ["coverageWindowId"]),
    ],
)
data class ParkingHistoryEntity(
    @PrimaryKey val id: String,
    val entryAtEpochMillis: Long,
    val exitAtEpochMillis: Long,
    val durationMinutes: Long,
    val feeYuan: Int,
    val wasCovered: Boolean,
    val coverageWindowId: String?,
    val createdAtEpochMillis: Long,
)
