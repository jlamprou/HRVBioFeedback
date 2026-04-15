package com.hrv.biofeedback.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HrvDatabase {
        return Room.databaseBuilder(
            context,
            HrvDatabase::class.java,
            "hrv_biofeedback.db"
        ).build()
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
