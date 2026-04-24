package com.neupan.parking_reminder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neupan.parking_reminder.data.entity.ParkingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParkingHistoryDao {
    @Query("SELECT * FROM parking_history ORDER BY exitAtEpochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ParkingHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: ParkingHistoryEntity)

    @Query("DELETE FROM parking_history")
    suspend fun clearAll()

    @Query(
        """
        DELETE FROM parking_history
        WHERE id NOT IN (
            SELECT id FROM parking_history
            ORDER BY exitAtEpochMillis DESC
            LIMIT :keepLatest
        )
        """,
    )
    suspend fun pruneToLatest(keepLatest: Int)
}
