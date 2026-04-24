package com.neupan.parking_reminder

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import com.neupan.parking_reminder.alarm.AndroidReminderNotifier
import com.neupan.parking_reminder.alarm.ReminderAlarmScheduler
import com.neupan.parking_reminder.alarm.ReminderResyncService
import com.neupan.parking_reminder.data.AppDatabase
import com.neupan.parking_reminder.data.repository.RoomParkingRepository
import com.neupan.parking_reminder.data.repository.RoomReminderStateRepository
import com.neupan.parking_reminder.domain.repository.ParkingRepository
import com.neupan.parking_reminder.domain.repository.ReminderStateRepository
import com.neupan.parking_reminder.domain.rule.ParkingStateResolver
import com.neupan.parking_reminder.domain.service.ParkingCommandService
import com.neupan.parking_reminder.domain.time.AppClock
import com.neupan.parking_reminder.domain.time.SystemAppClock

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    ).build()

    val clock: AppClock = SystemAppClock()

    val parkingRepository: ParkingRepository = RoomParkingRepository(database)

    val reminderStateRepository: ReminderStateRepository = RoomReminderStateRepository(
        database.reminderScheduleStateDao(),
    )

    val parkingStateResolver = ParkingStateResolver()

    private val reminderScheduler = ReminderAlarmScheduler(
        context = appContext,
        alarmManager = appContext.getSystemService(AlarmManager::class.java),
    )

    private val reminderNotifier = AndroidReminderNotifier(appContext)

    val reminderResyncService = ReminderResyncService(
        parkingRepository = parkingRepository,
        reminderStateRepository = reminderStateRepository,
        parkingStateResolver = parkingStateResolver,
        reminderScheduler = reminderScheduler,
        reminderNotifier = reminderNotifier,
        clock = clock,
    )

    val parkingCommandService = ParkingCommandService(
        parkingRepository = parkingRepository,
        reminderResyncService = reminderResyncService,
        clock = clock,
    )
}
