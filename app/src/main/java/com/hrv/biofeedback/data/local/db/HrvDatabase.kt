package com.hrv.biofeedback.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hrv.biofeedback.data.local.db.dao.MetricsSnapshotDao
import com.hrv.biofeedback.data.local.db.dao.RfAssessmentDao
import com.hrv.biofeedback.data.local.db.dao.RrIntervalDao
import com.hrv.biofeedback.data.local.db.dao.SessionDao
import com.hrv.biofeedback.data.local.db.entity.MetricsSnapshotEntity
import com.hrv.biofeedback.data.local.db.entity.RfAssessmentEntity
import com.hrv.biofeedback.data.local.db.entity.RrIntervalEntity
import com.hrv.biofeedback.data.local.db.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        RrIntervalEntity::class,
        MetricsSnapshotEntity::class,
        RfAssessmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HrvDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun rrIntervalDao(): RrIntervalDao
    abstract fun metricsSnapshotDao(): MetricsSnapshotDao
    abstract fun rfAssessmentDao(): RfAssessmentDao
}
