package com.hrv.biofeedback.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hrv.biofeedback.data.local.db.entity.RrIntervalEntity

@Dao
interface RrIntervalDao {

    @Insert
    suspend fun insertAll(intervals: List<RrIntervalEntity>)

    @Query("SELECT * FROM rr_intervals WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<RrIntervalEntity>

    @Query("DELETE FROM rr_intervals WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
