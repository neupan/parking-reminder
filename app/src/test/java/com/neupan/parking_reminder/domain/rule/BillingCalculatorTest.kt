package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingCalculatorTest {
    private val calculator = BillingCalculator()
    private val entryAt: Instant = Instant.parse("2026-04-24T13:00:00Z")

    @Test
    fun `fresh parking before one hour is free`() {
        val quote = calculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofMinutes(59)).plusSeconds(59),
        )

        assertEquals(0, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofHours(1)), quote.nextChargeAt)
        assertEquals(5, quote.nextFeeYuan)
        assertTrue(quote.status is ParkingStatus.ParkingFree)
    }

    @Test
    fun `fresh parking charges five yuan exactly at one hour`() {
        val quote = calculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofHours(1)),
        )

        assertEquals(5, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofHours(12)), quote.nextChargeAt)
        assertEquals(10, quote.nextFeeYuan)
        assertTrue(quote.status is ParkingStatus.ParkingCharged)
    }

    @Test
    fun `debug fast rules charge after two minutes`() {
        val debugCalculator = BillingCalculator(ParkingRuleConfig.DebugFast)

        val quote = debugCalculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofMinutes(2)),
        )

        assertEquals(5, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofMinutes(5)), quote.nextChargeAt)
        assertEquals(10, quote.nextFeeYuan)
        assertTrue(quote.status is ParkingStatus.ParkingCharged)
    }

    @Test
    fun `fresh parking remains five yuan before twelve hours`() {
        val quote = calculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(59)).plusSeconds(59),
        )

        assertEquals(5, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofHours(12)), quote.nextChargeAt)
        assertEquals(10, quote.nextFeeYuan)
    }

    @Test
    fun `fresh parking charges ten yuan exactly at twelve hours`() {
        val quote = calculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofHours(12)),
        )

        assertEquals(10, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofHours(24)), quote.nextChargeAt)
        assertEquals(15, quote.nextFeeYuan)
    }

    @Test
    fun `fresh parking charges fifteen yuan exactly at twenty four hours`() {
        val quote = calculator.calculate(
            session = session(),
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofHours(24)),
        )

        assertEquals(15, quote.currentFeeYuan)
        assertEquals(entryAt.plus(Duration.ofHours(36)), quote.nextChargeAt)
        assertEquals(20, quote.nextFeeYuan)
    }

    @Test
    fun `covered parking remains free until coverage end`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )

        val quote = calculator.calculate(
            session = session(matchedCoverageWindowId = coverage.id),
            matchedCoverageWindow = coverage,
            now = coverage.endAt.minusMillis(1),
        )

        assertEquals(0, quote.currentFeeYuan)
        assertEquals(coverage.endAt, quote.nextChargeAt)
        assertEquals(5, quote.nextFeeYuan)
        assertTrue(quote.status is ParkingStatus.ParkingCovered)
    }

    @Test
    fun `covered parking enters new charge immediately at coverage end`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )

        val quote = calculator.calculate(
            session = session(matchedCoverageWindowId = coverage.id),
            matchedCoverageWindow = coverage,
            now = coverage.endAt,
        )

        assertEquals(5, quote.currentFeeYuan)
        assertEquals(coverage.endAt.plus(Duration.ofHours(12)), quote.nextChargeAt)
        assertEquals(10, quote.nextFeeYuan)
        assertTrue(quote.status is ParkingStatus.PostCoverageCharged)
    }

    @Test
    fun `covered parking charges ten yuan twelve hours after coverage end`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )

        val quote = calculator.calculate(
            session = session(matchedCoverageWindowId = coverage.id),
            matchedCoverageWindow = coverage,
            now = coverage.endAt.plus(Duration.ofHours(12)),
        )

        assertEquals(10, quote.currentFeeYuan)
        assertEquals(coverage.endAt.plus(Duration.ofHours(24)), quote.nextChargeAt)
        assertEquals(15, quote.nextFeeYuan)
    }

    @Test
    fun `inactive coverage window is ignored`() {
        val inactiveCoverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
            isActive = false,
        )

        val quote = calculator.calculate(
            session = session(matchedCoverageWindowId = inactiveCoverage.id),
            matchedCoverageWindow = inactiveCoverage,
            now = entryAt.plus(Duration.ofHours(1)),
        )

        assertEquals(5, quote.currentFeeYuan)
        assertTrue(quote.status is ParkingStatus.ParkingCharged)
    }

    private fun session(
        matchedCoverageWindowId: String? = null,
    ): ParkingSession {
        return ParkingSession(
            id = "session-1",
            entryAt = entryAt,
            matchedCoverageWindowId = matchedCoverageWindowId,
            createdAt = entryAt,
            updatedAt = entryAt,
        )
    }

    private fun coverageWindow(
        startAt: Instant,
        endAt: Instant,
        isActive: Boolean = true,
    ): CoverageWindow {
        return CoverageWindow(
            id = "coverage-1",
            startAt = startAt,
            endAt = endAt,
            sourceHistoryId = "history-1",
            isActive = isActive,
            createdAt = startAt,
        )
    }
}
