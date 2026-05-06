package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeParkingRuleConfigTest {
    private val entryAt = Instant.parse("2026-05-06T08:00:00Z")

    @Test
    fun `billing calculator reads latest rule config without being recreated`() {
        val provider = MutableTestRuleConfigProvider(ParkingRuleMode.PRODUCTION)
        val calculator = BillingCalculator(provider)
        val session = session()

        val productionQuote = calculator.calculate(
            session = session,
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofMinutes(2)),
        )

        provider.setMode(ParkingRuleMode.DEBUG_FAST)

        val debugQuote = calculator.calculate(
            session = session,
            matchedCoverageWindow = null,
            now = entryAt.plus(Duration.ofMinutes(2)),
        )

        assertEquals(0, productionQuote.currentFeeYuan)
        assertTrue(productionQuote.status is ParkingStatus.ParkingFree)
        assertEquals(15, debugQuote.currentFeeYuan)
        assertTrue(debugQuote.status is ParkingStatus.ParkingCharged)
    }

    private fun session(): ParkingSession {
        return ParkingSession(
            id = "session-1",
            entryAt = entryAt,
            matchedCoverageWindowId = null,
            createdAt = entryAt,
            updatedAt = entryAt,
        )
    }

    private class MutableTestRuleConfigProvider(
        initialMode: ParkingRuleMode,
    ) : ParkingRuleConfigProvider {
        private var mode = initialMode

        override val currentMode: ParkingRuleMode
            get() = mode

        override val current: ParkingRuleConfig
            get() = mode.config

        fun setMode(mode: ParkingRuleMode) {
            this.mode = mode
        }
    }
}
