package com.sleepwatch.di

import com.sleepwatch.data.repository.AchievementRepositoryImpl
import com.sleepwatch.data.repository.AlertMessageRepositoryImpl
import com.sleepwatch.data.repository.SleepRecordRepositoryImpl
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.AlertMessageRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSleepRecordRepository(impl: SleepRecordRepositoryImpl): SleepRecordRepository

    @Binds
    @Singleton
    abstract fun bindAlertMessageRepository(impl: AlertMessageRepositoryImpl): AlertMessageRepository

    @Binds
    @Singleton
    abstract fun bindAchievementRepository(impl: AchievementRepositoryImpl): AchievementRepository
}
