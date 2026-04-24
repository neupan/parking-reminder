package com.neupan.parking_reminder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.neupan.parking_reminder.data.entity.CoverageWindowEntity

@Dao
interface CoverageWindowDao {
    @Query(
        """
        SELECT * FROM coverage_window
        WHERE isActive = 1
          AND startAtEpochMillis <= :atEpochMillis
          AND :atEpochMillis < endAtEpochMillis
        ORDER BY endAtEpochMillis DESC
        LIMIT 1
        """,
    )
    suspend fun findCoveringWindow(atEpochMillis: Long): CoverageWindowEntity?

    @Query("SELECT * FROM coverage_window WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CoverageWindowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: CoverageWindowEntity)
}
