package com.neupan.parking_reminder.domain.rule

import java.time.Duration

data class ParkingRuleConfig(
    val freeDuration: Duration,
    val billingCycle: Duration,
    val reminderLeadTime: Duration,
    val coverageDuration: Duration,
    val isTestMode: Boolean = false,
) {
    init {
        require(freeDuration.isPositive()) { "Free duration must be positive." }
        require(billingCycle.isPositive()) { "Billing cycle must be positive." }
        require(reminderLeadTime.isPositive()) { "Reminder lead time must be positive." }
        require(coverageDuration.isPositive()) { "Coverage duration must be positive." }
        require(billingCycle > freeDuration) { "Billing cycle must be longer than free duration." }
        require(freeDuration > reminderLeadTime) { "Free duration must be longer than reminder lead time." }
    }

    private fun Duration.isPositive(): Boolean = !isZero && !isNegative

    companion object {
        val Production = ParkingRuleConfig(
            freeDuration = Duration.ofHours(1),
            billingCycle = Duration.ofHours(12),
            reminderLeadTime = Duration.ofMinutes(10),
            coverageDuration = Duration.ofHours(12),
        )

        val DebugFast = ParkingRuleConfig(
            freeDuration = Duration.ofSeconds(30),
            billingCycle = Duration.ofMinutes(1),
            reminderLeadTime = Duration.ofSeconds(20),
            coverageDuration = Duration.ofMinutes(1),
            isTestMode = true,
        )
    }
}

enum class ParkingRuleMode {
    PRODUCTION,
    DEBUG_FAST,
    ;

    val config: ParkingRuleConfig
        get() = when (this) {
            PRODUCTION -> ParkingRuleConfig.Production
            DEBUG_FAST -> ParkingRuleConfig.DebugFast
        }

    val displayName: String
        get() = when (this) {
            PRODUCTION -> "正式规则"
            DEBUG_FAST -> "测试规则"
        }

    companion object {
        fun fromStoredName(value: String?): ParkingRuleMode? {
            return entries.firstOrNull { it.name == value }
        }
    }
}

interface ParkingRuleConfigProvider {
    val currentMode: ParkingRuleMode

    val current: ParkingRuleConfig
}

class FixedParkingRuleConfigProvider(
    override val current: ParkingRuleConfig = ParkingRuleConfig.Production,
) : ParkingRuleConfigProvider {
    override val currentMode: ParkingRuleMode
        get() = if (current.isTestMode) ParkingRuleMode.DEBUG_FAST else ParkingRuleMode.PRODUCTION
}
