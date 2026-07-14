package com.sleepwatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sleepwatch.data.db.dao.AchievementDao
import com.sleepwatch.data.db.dao.AlertMessageDao
import com.sleepwatch.data.db.dao.SleepRecordDao
import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.data.db.entity.AlertMessage
import com.sleepwatch.data.db.entity.SleepRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SleepRecord::class, AlertMessage::class, Achievement::class],
    version = 1,
    exportSchema = false
)
abstract class SleepWatchDatabase : RoomDatabase() {
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun alertMessageDao(): AlertMessageDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        fun getCallback() = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DefaultData.alertMessages.forEach { msg ->
                    db.execSQL(
                        "INSERT INTO alert_messages (level, title, content, healthTip, isEnabled) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(msg.level, msg.title, msg.content, msg.healthTip, if (msg.isEnabled) 1 else 0)
                    )
                }
            }
        }
    }
}
