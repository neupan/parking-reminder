package com.neupan.parking_reminder.alarm.model

enum class ReminderSyncReason {
    USER_STARTED_PARKING,
    USER_EDITED_ENTRY_TIME,
    USER_CHECKED_OUT,
    ALARM_FIRED,
    APP_COLD_START,
    BOOT_COMPLETED,
    PACKAGE_REPLACED,
    TIME_CHANGED,
    TIMEZONE_CHANGED,
    EXACT_ALARM_PERMISSION_CHANGED,
}
