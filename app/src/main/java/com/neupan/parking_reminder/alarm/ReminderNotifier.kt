package com.neupan.parking_reminder.alarm

import com.neupan.parking_reminder.domain.model.ParkingSnapshot
import com.neupan.parking_reminder.domain.model.ReminderPlan

interface ReminderNotifier {
    suspend fun showReminder(
        plan: ReminderPlan,
        snapshot: ParkingSnapshot,
    )
}
