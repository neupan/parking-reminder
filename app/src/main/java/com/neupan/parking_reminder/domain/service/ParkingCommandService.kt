package com.neupan.parking_reminder.domain.service

import android.util.Log
import com.neupan.parking_reminder.alarm.ReminderResyncService
import com.neupan.parking_reminder.alarm.model.ReminderSyncReason
import com.neupan.parking_reminder.domain.repository.ParkingRepository
import com.neupan.parking_reminder.domain.time.AppClock
import java.time.Instant

class ParkingCommandService(
    private val parkingRepository: ParkingRepository,
    private val reminderResyncService: ReminderResyncService,
    private val clock: AppClock,
) {
    suspend fun startParking(entryAt: Instant = clock.now()) {
        Log.d(TAG, "startParking() entryAt=$entryAt")
        parkingRepository.startParking(
            entryAt = entryAt,
            now = clock.now(),
        )
        Log.d(TAG, "startParking() DB updated, triggering resync")
        reminderResyncService.resync(ReminderSyncReason.USER_STARTED_PARKING)
    }

    suspend fun updateEntryTime(entryAt: Instant) {
        Log.d(TAG, "updateEntryTime() entryAt=$entryAt")
        parkingRepository.updateEntryTime(
            entryAt = entryAt,
            now = clock.now(),
        )
        reminderResyncService.resync(ReminderSyncReason.USER_EDITED_ENTRY_TIME)
    }

    suspend fun checkout() {
        Log.d(TAG, "checkout()")
        parkingRepository.checkout(
            exitAt = clock.now(),
            now = clock.now(),
        )
        reminderResyncService.resync(ReminderSyncReason.USER_CHECKED_OUT)
    }

    suspend fun clearHistory() {
        Log.d(TAG, "clearHistory()")
        parkingRepository.clearHistory()
    }

    companion object {
        private const val TAG = "ParkCmd"
    }
}
