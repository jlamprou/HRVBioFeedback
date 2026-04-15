package com.hrv.biofeedback.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hrv.biofeedback.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getById(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE type = :type ORDER BY startTime DESC")
    fun getByType(type: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatest(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE type = 'ASSESSMENT' ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestAssessment(): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun delete(sessionId: Long)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getCount(): Int
}
