package com.neupan.parking_reminder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "active_parking_session")
data class ActiveParkingSessionEntity(
    @PrimaryKey val id: String,
    val entryAtEpochMillis: Long,
    val matchedCoverageWindowId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
