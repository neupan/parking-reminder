package com.neupan.parking_reminder.data

import android.content.Context
import com.neupan.parking_reminder.domain.rule.ParkingRuleConfig
import com.neupan.parking_reminder.domain.rule.ParkingRuleConfigProvider
import com.neupan.parking_reminder.domain.rule.ParkingRuleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ParkingRuleConfigStore(
    context: Context,
    defaultMode: ParkingRuleMode,
) : ParkingRuleConfigProvider {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private val _modeFlow = MutableStateFlow(readMode(defaultMode))

    val modeFlow: StateFlow<ParkingRuleMode> = _modeFlow.asStateFlow()

    override val currentMode: ParkingRuleMode
        get() = _modeFlow.value

    override val current: ParkingRuleConfig
        get() = currentMode.config

    fun setMode(mode: ParkingRuleMode) {
        if (_modeFlow.value == mode) return
        preferences.edit()
            .putString(KEY_RULE_MODE, mode.name)
            .apply()
        _modeFlow.value = mode
    }

    private fun readMode(defaultMode: ParkingRuleMode): ParkingRuleMode {
        return ParkingRuleMode.fromStoredName(preferences.getString(KEY_RULE_MODE, null))
            ?: defaultMode
    }

    private companion object {
        const val PREFS_NAME = "parking_rule_config"
        const val KEY_RULE_MODE = "rule_mode"
    }
}
