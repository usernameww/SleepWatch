package com.sleepwatch.di

import android.content.Context
import androidx.room.Room
import com.sleepwatch.data.db.SleepWatchDatabase
import com.sleepwatch.data.db.dao.AchievementDao
import com.sleepwatch.data.db.dao.AlertMessageDao
import com.sleepwatch.data.db.dao.SleepRecordDao
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
    fun provideDatabase(@ApplicationContext context: Context): SleepWatchDatabase {
        return Room.databaseBuilder(
            context,
            SleepWatchDatabase::class.java,
            "sleepwatch.db"
        ).build()
    }

    @Provides
    fun provideSleepRecordDao(db: SleepWatchDatabase): SleepRecordDao = db.sleepRecordDao()

    @Provides
    fun provideAlertMessageDao(db: SleepWatchDatabase): AlertMessageDao = db.alertMessageDao()

    @Provides
    fun provideAchievementDao(db: SleepWatchDatabase): AchievementDao = db.achievementDao()
}
