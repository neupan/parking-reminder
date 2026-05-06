package com.neupan.parking_reminder.domain.rule

import com.neupan.parking_reminder.domain.model.BillingQuote
import com.neupan.parking_reminder.domain.model.CountdownTargetLabel
import com.neupan.parking_reminder.domain.model.CoverageWindow
import com.neupan.parking_reminder.domain.model.ParkingSession
import com.neupan.parking_reminder.domain.model.ParkingStatus
import java.time.Duration
import java.time.Instant

class BillingCalculator(
    private val ruleConfigProvider: ParkingRuleConfigProvider = FixedParkingRuleConfigProvider(),
    private val coverageMatcher: CoverageMatcher = CoverageMatcher(),
) {
    constructor(
        ruleConfig: ParkingRuleConfig,
        coverageMatcher: CoverageMatcher = CoverageMatcher(),
    ) : this(FixedParkingRuleConfigProvider(ruleConfig), coverageMatcher)

    fun calculate(
        session: ParkingSession,
        matchedCoverageWindow: CoverageWindow?,
        now: Instant,
    ): BillingQuote {
        val ruleConfig = ruleConfigProvider.current
        val coverageWindow = matchedCoverageWindow?.takeIf {
            coverageMatcher.isCovered(session.entryAt, it)
        }

        return if (coverageWindow != null) {
            calculateCoveredSession(coverageWindow, now, ruleConfig)
        } else {
            calculateFreshSession(session.entryAt, now, ruleConfig)
        }
    }

    private fun calculateFreshSession(
        entryAt: Instant,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): BillingQuote {
        val freeEndsAt = entryAt.plus(ruleConfig.freeDuration)
        val firstCycleEndsAt = entryAt.plus(ruleConfig.billingCycle)

        if (now.isBefore(freeEndsAt)) {
            return BillingQuote(
                status = ParkingStatus.ParkingFree(freeEndsAt),
                currentFeeYuan = 0,
                nextChargeAt = freeEndsAt,
                nextFeeYuan = BASE_FEE_YUAN,
                countdownTargetLabel = CountdownTargetLabel.FREE_ENDS,
            )
        }

        if (now.isBefore(firstCycleEndsAt)) {
            return BillingQuote(
                status = ParkingStatus.ParkingCharged(
                    currentFeeYuan = BASE_FEE_YUAN,
                    nextChargeAt = firstCycleEndsAt,
                    nextFeeYuan = BASE_FEE_YUAN * 2,
                ),
                currentFeeYuan = BASE_FEE_YUAN,
                nextChargeAt = firstCycleEndsAt,
                nextFeeYuan = BASE_FEE_YUAN * 2,
                countdownTargetLabel = CountdownTargetLabel.NEXT_FEE_INCREASE,
            )
        }

        val completedCyclesAfterFirst = elapsedCycleCount(firstCycleEndsAt, now, ruleConfig)
        val currentFeeYuan = BASE_FEE_YUAN * (2 + completedCyclesAfterFirst.toInt())
        val nextChargeAt = firstCycleEndsAt.plus(ruleConfig.billingCycle.multipliedBy(completedCyclesAfterFirst + 1))
        val nextFeeYuan = currentFeeYuan + BASE_FEE_YUAN

        return BillingQuote(
            status = ParkingStatus.ParkingCharged(
                currentFeeYuan = currentFeeYuan,
                nextChargeAt = nextChargeAt,
                nextFeeYuan = nextFeeYuan,
            ),
            currentFeeYuan = currentFeeYuan,
            nextChargeAt = nextChargeAt,
            nextFeeYuan = nextFeeYuan,
            countdownTargetLabel = CountdownTargetLabel.NEXT_FEE_INCREASE,
        )
    }

    private fun calculateCoveredSession(
        coverageWindow: CoverageWindow,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): BillingQuote {
        if (now.isBefore(coverageWindow.endAt)) {
            return BillingQuote(
                status = ParkingStatus.ParkingCovered(coverageWindow),
                currentFeeYuan = 0,
                nextChargeAt = coverageWindow.endAt,
                nextFeeYuan = BASE_FEE_YUAN,
                countdownTargetLabel = CountdownTargetLabel.COVERAGE_ENDS,
            )
        }

        val completedCycles = elapsedCycleCount(coverageWindow.endAt, now, ruleConfig)
        val currentFeeYuan = BASE_FEE_YUAN * (1 + completedCycles.toInt())
        val nextChargeAt = coverageWindow.endAt.plus(ruleConfig.billingCycle.multipliedBy(completedCycles + 1))
        val nextFeeYuan = currentFeeYuan + BASE_FEE_YUAN

        return BillingQuote(
            status = ParkingStatus.PostCoverageCharged(
                coverageWindow = coverageWindow,
                currentFeeYuan = currentFeeYuan,
                nextChargeAt = nextChargeAt,
                nextFeeYuan = nextFeeYuan,
            ),
            currentFeeYuan = currentFeeYuan,
            nextChargeAt = nextChargeAt,
            nextFeeYuan = nextFeeYuan,
            countdownTargetLabel = CountdownTargetLabel.NEXT_FEE_INCREASE,
        )
    }

    private fun elapsedCycleCount(
        anchor: Instant,
        now: Instant,
        ruleConfig: ParkingRuleConfig,
    ): Long {
        if (now.isBefore(anchor)) return 0
        return Duration.between(anchor, now).toMillis() / ruleConfig.billingCycle.toMillis()
    }

    private companion object {
        const val BASE_FEE_YUAN = 5
    }
}
