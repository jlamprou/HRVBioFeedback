package com.hrv.biofeedback.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hrv.biofeedback.data.local.db.entity.MetricsSnapshotEntity

@Dao
interface MetricsSnapshotDao {

    @Insert
    suspend fun insertAll(snapshots: List<MetricsSnapshotEntity>)

    @Query("SELECT * FROM metrics_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<MetricsSnapshotEntity>

    @Query("DELETE FROM metrics_snapshots WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)
}
