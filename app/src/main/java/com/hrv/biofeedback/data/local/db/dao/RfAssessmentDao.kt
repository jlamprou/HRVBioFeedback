package com.hrv.biofeedback.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hrv.biofeedback.data.local.db.entity.RfAssessmentEntity

@Dao
interface RfAssessmentDao {

    @Insert
    suspend fun insertAll(results: List<RfAssessmentEntity>)

    @Query("SELECT * FROM rf_assessments WHERE sessionId = :sessionId ORDER BY breathingRate DESC")
    suspend fun getBySession(sessionId: Long): List<RfAssessmentEntity>
}
