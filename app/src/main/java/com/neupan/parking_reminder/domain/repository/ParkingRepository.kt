package com.neupan.parking_reminder.domain.repository

import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingHistory
import com.neupan.parking_reminder.domain.model.ParkingSession
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface ParkingRepository {
    fun observeActiveSession(): Flow<ParkingSession?>

    fun observeHistory(limit: Int = DEFAULT_HISTORY_LIMIT): Flow<List<ParkingHistory>>

    suspend fun getActiveSession(): ParkingSession?

    suspend fun findCoveringWindow(entryAt: Instant): CoverageWindow?

    suspend fun getCoverageWindow(id: String): CoverageWindow?

    suspend fun startParking(
        entryAt: Instant,
        now: Instant,
    ): StartParkingResult

    suspend fun updateEntryTime(
        entryAt: Instant,
        now: Instant,
    ): UpdateEntryTimeResult

    suspend fun checkout(
        exitAt: Instant,
        now: Instant,
    ): CheckoutResult

    suspend fun clearHistory()

    suspend fun pruneHistory(keepLatest: Int = DEFAULT_HISTORY_LIMIT)

    companion object {
        const val DEFAULT_HISTORY_LIMIT = 50
    }
}

data class StartParkingResult(
    val session: ParkingSession,
    val matchedCoverageWindow: CoverageWindow?,
)

data class UpdateEntryTimeResult(
    val session: ParkingSession,
    val matchedCoverageWindow: CoverageWindow?,
)

data class CheckoutResult(
    val history: ParkingHistory,
    val createdCoverageWindow: CoverageWindow?,
)
