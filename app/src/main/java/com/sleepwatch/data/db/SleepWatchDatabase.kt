package com.sleepwatch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sleepwatch.data.db.dao.AchievementDao
import com.sleepwatch.data.db.dao.AlertMessageDao
import com.sleepwatch.data.db.dao.SleepRecordDao
import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.data.db.entity.AlertMessage
import com.sleepwatch.data.db.entity.SleepRecord

@Database(
    entities = [SleepRecord::class, AlertMessage::class, Achievement::class],
    version = 1,
    exportSchema = true
)
abstract class SleepWatchDatabase : RoomDatabase() {
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun alertMessageDao(): AlertMessageDao
    abstract fun achievementDao(): AchievementDao
}
