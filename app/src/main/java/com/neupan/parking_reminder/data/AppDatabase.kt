package com.neupan.parking_reminder.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.neupan.parking_reminder.data.dao.ActiveParkingSessionDao
import com.neupan.parking_reminder.data.dao.CoverageWindowDao
import com.neupan.parking_reminder.data.dao.ParkingHistoryDao
import com.neupan.parking_reminder.data.dao.ReminderScheduleStateDao
import com.neupan.parking_reminder.data.entity.ActiveParkingSessionEntity
import com.neupan.parking_reminder.data.entity.CoverageWindowEntity
import com.neupan.parking_reminder.data.entity.ParkingHistoryEntity
import com.neupan.parking_reminder.data.entity.ReminderScheduleStateEntity

@Database(
    entities = [
        ActiveParkingSessionEntity::class,
        CoverageWindowEntity::class,
        ParkingHistoryEntity::class,
        ReminderScheduleStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activeParkingSessionDao(): ActiveParkingSessionDao

    abstract fun coverageWindowDao(): CoverageWindowDao

    abstract fun parkingHistoryDao(): ParkingHistoryDao

    abstract fun reminderScheduleStateDao(): ReminderScheduleStateDao

    companion object {
        const val DATABASE_NAME = "parking_reminder.db"
    }
}
