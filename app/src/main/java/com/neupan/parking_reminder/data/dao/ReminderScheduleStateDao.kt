package com.neupan.parking_reminder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neupan.parking_reminder.data.entity.ReminderScheduleStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderScheduleStateDao {
    @Query("SELECT * FROM reminder_schedule_state WHERE id = 'current' LIMIT 1")
    fun observeCurrent(): Flow<ReminderScheduleStateEntity?>

    @Query("SELECT * FROM reminder_schedule_state WHERE id = 'current' LIMIT 1")
    suspend fun getCurrent(): ReminderScheduleStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReminderScheduleStateEntity)
}
