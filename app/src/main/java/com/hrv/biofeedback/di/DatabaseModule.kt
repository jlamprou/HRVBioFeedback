package com.hrv.biofeedback.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hrv.biofeedback.data.local.db.HrvDatabase
import com.hrv.biofeedback.data.local.db.dao.MetricsSnapshotDao
import com.hrv.biofeedback.data.local.db.dao.RfAssessmentDao
import com.hrv.biofeedback.data.local.db.dao.RrIntervalDao
import com.hrv.biofeedback.data.local.db.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Additive migration: adds new metric columns with defaults.
     * NEVER use fallbackToDestructiveMigration — it deletes all user data.
     */
    /**
     * Safe additive migration: adds columns only if they don't already exist.
     * This handles the case where the destructive migration already created the full schema.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            fun addColumnIfNotExists(table: String, column: String, type: String, default: String) {
                try {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $column $type NOT NULL DEFAULT $default")
                } catch (_: Exception) {
                    // Column already exists — safe to ignore
                }
            }

            // Sessions table
            addColumnIfNotExists("sessions", "averagePnn50", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageLfHfRatio", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageSd1", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageSd2", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageDfaAlpha1", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageSampleEntropy", "REAL", "0.0")
            addColumnIfNotExists("sessions", "averageCardiorespCoherence", "REAL", "0.0")
            addColumnIfNotExists("sessions", "detectedBreathingRate", "REAL", "0.0")
            addColumnIfNotExists("sessions", "artifactRatePercent", "REAL", "0.0")

            // Metrics snapshots table
            addColumnIfNotExists("metrics_snapshots", "pnn50", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "lfHfRatio", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "sd1", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "sd2", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "dfaAlpha1", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "sampleEntropy", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "cardiorespCoherence", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "breathingRate", "REAL", "0.0")
            addColumnIfNotExists("metrics_snapshots", "cardiorespPhase", "REAL", "0.0")

            // RF assessments table
            addColumnIfNotExists("rf_assessments", "lfPeakCount", "INTEGER", "1")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HrvDatabase {
        return Room.databaseBuilder(
            context,
            HrvDatabase::class.java,
            "hrv_biofeedback.db"
        )
            .addMigrations(MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideSessionDao(db: HrvDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideRrIntervalDao(db: HrvDatabase): RrIntervalDao = db.rrIntervalDao()

    @Provides
    fun provideMetricsSnapshotDao(db: HrvDatabase): MetricsSnapshotDao = db.metricsSnapshotDao()

    @Provides
    fun provideRfAssessmentDao(db: HrvDatabase): RfAssessmentDao = db.rfAssessmentDao()
}
