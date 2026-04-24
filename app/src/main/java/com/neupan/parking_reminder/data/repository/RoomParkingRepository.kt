package com.neupan.parking_reminder.data.repository

import androidx.room.withTransaction
import com.neupan.parking_reminder.data.AppDatabase
import com.neupan.parking_reminder.data.mapper.toDomain
import com.neupan.parking_reminder.data.mapper.toEntity
import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingHistory
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.repository.CheckoutResult
import com.neupan.parking_reminder.domain.repository.ParkingRepository
import com.neupan.parking_reminder.domain.repository.StartParkingResult
import com.neupan.parking_reminder.domain.repository.UpdateEntryTimeResult
import com.neupan.parking_reminder.domain.rule.BillingCalculator
import com.neupan.parking_reminder.domain.rule.ParkingRuleConfig
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomParkingRepository(
    private val database: AppDatabase,
    private val ruleConfig: ParkingRuleConfig = ParkingRuleConfig.Production,
    private val billingCalculator: BillingCalculator = BillingCalculator(ruleConfig),
) : ParkingRepository {
    private val activeSessionDao = database.activeParkingSessionDao()
    private val coverageWindowDao = database.coverageWindowDao()
    private val parkingHistoryDao = database.parkingHistoryDao()

    override fun observeActiveSession(): Flow<ParkingSession?> {
        return activeSessionDao.observeActiveSession().map { it?.toDomain() }
    }

    override fun observeHistory(limit: Int): Flow<List<ParkingHistory>> {
        return parkingHistoryDao.observeLatest(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getActiveSession(): ParkingSession? {
        return activeSessionDao.getActiveSession()?.toDomain()
    }

    override suspend fun findCoveringWindow(entryAt: Instant): CoverageWindow? {
        return coverageWindowDao.findCoveringWindow(entryAt.toEpochMilli())?.toDomain()
    }

    override suspend fun getCoverageWindow(id: String): CoverageWindow? {
        return coverageWindowDao.getById(id)?.toDomain()
    }

    override suspend fun startParking(
        entryAt: Instant,
        now: Instant,
    ): StartParkingResult {
        require(!entryAt.isAfter(now)) { "Entry time cannot be in the future." }

        return database.withTransaction {
            val matchedCoverageWindow = findCoveringWindow(entryAt)
            val session = ParkingSession(
                id = newId(),
                entryAt = entryAt,
                matchedCoverageWindowId = matchedCoverageWindow?.id,
                createdAt = now,
                updatedAt = now,
            )

            activeSessionDao.clear()
            activeSessionDao.insert(session.toEntity())

            StartParkingResult(
                session = session,
                matchedCoverageWindow = matchedCoverageWindow,
            )
        }
    }

    override suspend fun updateEntryTime(
        entryAt: Instant,
        now: Instant,
    ): UpdateEntryTimeResult {
        require(!entryAt.isAfter(now)) { "Entry time cannot be in the future." }

        return database.withTransaction {
            val currentSession = activeSessionDao.getActiveSession()?.toDomain()
                ?: error("No active parking session to update.")
            val matchedCoverageWindow = findCoveringWindow(entryAt)
            val updatedSession = currentSession.copy(
                entryAt = entryAt,
                matchedCoverageWindowId = matchedCoverageWindow?.id,
                updatedAt = now,
            )

            activeSessionDao.clear()
            activeSessionDao.insert(updatedSession.toEntity())

            UpdateEntryTimeResult(
                session = updatedSession,
                matchedCoverageWindow = matchedCoverageWindow,
            )
        }
    }

    override suspend fun checkout(
        exitAt: Instant,
        now: Instant,
    ): CheckoutResult {
        require(!exitAt.isAfter(now)) { "Exit time cannot be in the future." }

        return database.withTransaction {
            val activeSession = activeSessionDao.getActiveSession()?.toDomain()
                ?: error("No active parking session to check out.")
            require(!exitAt.isBefore(activeSession.entryAt)) {
                "Exit time cannot be before entry time."
            }

            val matchedCoverageWindow = activeSession.matchedCoverageWindowId
                ?.let { coverageWindowDao.getById(it)?.toDomain() }
            val billingQuote = billingCalculator.calculate(
                session = activeSession,
                matchedCoverageWindow = matchedCoverageWindow,
                now = exitAt,
            )
            val history = ParkingHistory(
                id = newId(),
                entryAt = activeSession.entryAt,
                exitAt = exitAt,
                duration = Duration.between(activeSession.entryAt, exitAt),
                feeYuan = billingQuote.currentFeeYuan,
                wasCovered = matchedCoverageWindow != null,
                coverageWindowId = matchedCoverageWindow?.id,
                createdAt = now,
            )
            val createdCoverageWindow = if (history.feeYuan > 0) {
                CoverageWindow(
                    id = newId(),
                    startAt = exitAt,
                    endAt = exitAt.plus(ruleConfig.coverageDuration),
                    sourceHistoryId = history.id,
                    isActive = true,
                    createdAt = now,
                )
            } else {
                null
            }

            parkingHistoryDao.insert(history.toEntity())
            createdCoverageWindow?.let { coverageWindowDao.insert(it.toEntity()) }
            activeSessionDao.clear()
            parkingHistoryDao.pruneToLatest(ParkingRepository.DEFAULT_HISTORY_LIMIT)

            CheckoutResult(
                history = history,
                createdCoverageWindow = createdCoverageWindow,
            )
        }
    }

    override suspend fun clearHistory() {
        parkingHistoryDao.clearAll()
    }

    override suspend fun pruneHistory(keepLatest: Int) {
        parkingHistoryDao.pruneToLatest(keepLatest)
    }

    private fun newId(): String = UUID.randomUUID().toString()
}
