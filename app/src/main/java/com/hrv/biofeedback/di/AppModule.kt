package com.hrv.biofeedback.di

import com.hrv.biofeedback.data.ble.polar.PolarHrSource
import com.hrv.biofeedback.domain.repository.HrDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindHrDataSource(polarHrSource: PolarHrSource): HrDataSource
}
