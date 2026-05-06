package com.neupan.parking_reminder.ui.home

data class HomeUiState(
    val isLoading: Boolean = true,
    val mode: HomeMode = HomeMode.Idle,
    val currentFeeYuan: Int = 0,
    val primaryText: String = "",
    val secondaryText: String = "",
    val reminderPolicyText: String = "下一次加费前 10 分钟提醒",
    val ruleModeText: String? = null,
    val ruleModeTitle: String = "正式规则",
    val ruleModeDescription: String = "1 小时免费，12 小时一轮，提前 10 分钟提醒",
    val ruleModeSwitchText: String = "切换到测试规则",
    val entryAtText: String? = null,
    val nextChangeAtText: String? = null,
    val countdownText: String? = null,
    val progressFraction: Float = 0f,
    val urgency: UrgencyLevel = UrgencyLevel.SAFE,
    val reminderHealth: ReminderHealthUiState = ReminderHealthUiState(),
    val canStartParking: Boolean = true,
    val canCheckout: Boolean = false,
    val canEditEntryTime: Boolean = false,
    val actionInProgress: Boolean = false,
    val errorMessage: String? = null,
)

enum class HomeMode {
    Idle,
    ParkingFree,
    ParkingCharged,
    ParkingCovered,
    PostCoverageCharged,
}

enum class UrgencyLevel {
    SAFE,
    WARNING,
    URGENT,
}

data class ReminderHealthUiState(
    val level: ReminderHealthLevel = ReminderHealthLevel.OK,
    val title: String = "提醒正常",
    val message: String? = null,
    val nextReminderText: String? = null,
    val showOpenExactAlarmSettingsAction: Boolean = false,
)

enum class ReminderHealthLevel {
    OK,
    WARNING,
    BLOCKED,
}
