package com.neupan.parking_reminder.ui.home

data class HomeUiState(
    val isLoading: Boolean = true,
    val mode: HomeMode = HomeMode.Idle,
    val currentFeeYuan: Int = 0,
    val primaryText: String = "",
    val secondaryText: String = "",
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
)

enum class ReminderHealthLevel {
    OK,
    WARNING,
    BLOCKED,
}
