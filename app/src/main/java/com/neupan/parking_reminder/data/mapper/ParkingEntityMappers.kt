package com.neupan.parking_reminder.data.mapper

import com.neupan.parking_reminder.data.entity.ActiveParkingSessionEntity
import com.neupan.parking_reminder.data.entity.CoverageWindowEntity
import com.neupan.parking_reminder.data.entity.ParkingHistoryEntity
import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingHistory
import com.neupan.parking_reminder.domain.model.ParkingSession
import java.time.Duration
import java.time.Instant

fun ActiveParkingSessionEntity.toDomain(): ParkingSession {
    return ParkingSession(
        id = id,
        entryAt = Instant.ofEpochMilli(entryAtEpochMillis),
        matchedCoverageWindowId = matchedCoverageWindowId,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
        updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    )
}

fun ParkingSession.toEntity(): ActiveParkingSessionEntity {
    return ActiveParkingSessionEntity(
        id = id,
        entryAtEpochMillis = entryAt.toEpochMilli(),
        matchedCoverageWindowId = matchedCoverageWindowId,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        updatedAtEpochMillis = updatedAt.toEpochMilli(),
    )
}

fun CoverageWindowEntity.toDomain(): CoverageWindow {
    return CoverageWindow(
        id = id,
        startAt = Instant.ofEpochMilli(startAtEpochMillis),
        endAt = Instant.ofEpochMilli(endAtEpochMillis),
        sourceHistoryId = sourceHistoryId,
        isActive = isActive,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )
}

fun CoverageWindow.toEntity(): CoverageWindowEntity {
    return CoverageWindowEntity(
        id = id,
        startAtEpochMillis = startAt.toEpochMilli(),
        endAtEpochMillis = endAt.toEpochMilli(),
        sourceHistoryId = sourceHistoryId,
        isActive = isActive,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}

fun ParkingHistoryEntity.toDomain(): ParkingHistory {
    return ParkingHistory(
        id = id,
        entryAt = Instant.ofEpochMilli(entryAtEpochMillis),
        exitAt = Instant.ofEpochMilli(exitAtEpochMillis),
        duration = Duration.ofMinutes(durationMinutes),
        feeYuan = feeYuan,
        wasCovered = wasCovered,
        coverageWindowId = coverageWindowId,
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )
}

fun ParkingHistory.toEntity(): ParkingHistoryEntity {
    return ParkingHistoryEntity(
        id = id,
        entryAtEpochMillis = entryAt.toEpochMilli(),
        exitAtEpochMillis = exitAt.toEpochMilli(),
        durationMinutes = duration.toMinutes(),
        feeYuan = feeYuan,
        wasCovered = wasCovered,
        coverageWindowId = coverageWindowId,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}
