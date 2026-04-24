package com.neupan.parking_reminder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neupan.parking_reminder.data.entity.ActiveParkingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActiveParkingSessionDao {
    @Query("SELECT * FROM active_parking_session LIMIT 1")
    fun observeActiveSession(): Flow<ActiveParkingSessionEntity?>

    @Query("SELECT * FROM active_parking_session LIMIT 1")
    suspend fun getActiveSession(): ActiveParkingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ActiveParkingSessionEntity)

    @Query("DELETE FROM active_parking_session")
    suspend fun clear()
}
