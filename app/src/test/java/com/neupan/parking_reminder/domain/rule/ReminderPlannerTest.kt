package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ReminderType
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPlannerTest {
    private val calculator = BillingCalculator()
    private val planner = ReminderPlanner()
    private val entryAt: Instant = Instant.parse("2026-04-24T13:00:00Z")

    @Test
    fun `fresh parking schedules free ending reminder at fifty minutes`() {
        val session = session()
        val now = entryAt

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, null, now),
            now = now,
        )

        assertEquals(ReminderType.FREE_ENDING, plan?.reminderType)
        assertEquals(entryAt.plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(5, plan?.targetFeeYuan)
    }

    @Test
    fun `fresh parking skips missed fifty minute reminder`() {
        val session = session()
        val now = entryAt.plus(Duration.ofMinutes(55))

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, null, now),
            now = now,
        )

        assertEquals(ReminderType.FEE_INCREASING, plan?.reminderType)
        assertEquals(entryAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(10, plan?.targetFeeYuan)
    }

    @Test
    fun `fresh parking skips reminder at exact trigger moment`() {
        val session = session()
        val now = entryAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(50))

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, null, now),
            now = now,
        )

        assertEquals(entryAt.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(15, plan?.targetFeeYuan)
    }

    @Test
    fun `covered parking schedules coverage ending reminder`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )
        val session = session(matchedCoverageWindowId = coverage.id)
        val now = entryAt

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, coverage, now),
            now = now,
        )

        assertEquals(ReminderType.COVERAGE_ENDING, plan?.reminderType)
        assertEquals(coverage.endAt.minus(Duration.ofMinutes(10)), plan?.triggerAt)
        assertEquals(5, plan?.targetFeeYuan)
    }

    @Test
    fun `covered parking skips coverage ending reminder if it is already missed`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )
        val session = session(matchedCoverageWindowId = coverage.id)
        val now = coverage.endAt.minus(Duration.ofMinutes(5))

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, coverage, now),
            now = now,
        )

        assertEquals(ReminderType.FEE_INCREASING, plan?.reminderType)
        assertEquals(coverage.endAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(10, plan?.targetFeeYuan)
    }

    @Test
    fun `post coverage parking schedules next twelve hour boundary reminder`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )
        val session = session(matchedCoverageWindowId = coverage.id)
        val now = coverage.endAt

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, coverage, now),
            now = now,
        )

        assertEquals(ReminderType.FEE_INCREASING, plan?.reminderType)
        assertEquals(coverage.endAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(10, plan?.targetFeeYuan)
    }

    @Test
    fun `post coverage parking skips exact trigger moment`() {
        val coverage = coverageWindow(
            startAt = entryAt.minus(Duration.ofHours(2)),
            endAt = entryAt.plus(Duration.ofHours(10)),
        )
        val session = session(matchedCoverageWindowId = coverage.id)
        val now = coverage.endAt.plus(Duration.ofHours(11)).plus(Duration.ofMinutes(50))

        val plan = planner.planNextReminder(
            session = session,
            quote = calculator.calculate(session, coverage, now),
            now = now,
        )

        assertEquals(coverage.endAt.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(50)), plan?.triggerAt)
        assertEquals(15, plan?.targetFeeYuan)
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
    ): CoverageWindow {
        return CoverageWindow(
            id = "coverage-1",
            startAt = startAt,
            endAt = endAt,
            sourceHistoryId = "history-1",
            isActive = true,
            createdAt = startAt,
        )
    }
}
