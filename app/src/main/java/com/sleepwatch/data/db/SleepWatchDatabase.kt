package com.sleepwatch.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 2,
    exportSchema = true
)
abstract class SleepWatchDatabase : RoomDatabase() {
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun alertMessageDao(): AlertMessageDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sleep_records_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `date` TEXT NOT NULL,
                        `monitorStartTime` INTEGER NOT NULL,
                        `monitorEndTime` INTEGER,
                        `targetBedtimeTime` INTEGER,
                        `firstAlertTime` INTEGER,
                        `sleepTime` INTEGER,
                        `firstInactiveCheckTime` INTEGER,
                        `consecutiveInactiveChecks` INTEGER NOT NULL,
                        `totalCheckCount` INTEGER NOT NULL,
                        `activeCheckCount` INTEGER NOT NULL,
                        `lastCheckTime` INTEGER,
                        `nextCheckTime` INTEGER,
                        `checkIntervalMinutes` INTEGER NOT NULL,
                        `inactiveThreshold` INTEGER NOT NULL,
                        `totalAlertCount` INTEGER NOT NULL,
                        `sleepScore` REAL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `sleep_records_new` (
                        `id`, `date`, `monitorStartTime`, `monitorEndTime`, `targetBedtimeTime`,
                        `firstAlertTime`, `sleepTime`, `firstInactiveCheckTime`,
                        `consecutiveInactiveChecks`, `totalCheckCount`, `activeCheckCount`,
                        `lastCheckTime`, `nextCheckTime`, `checkIntervalMinutes`,
                        `inactiveThreshold`, `totalAlertCount`, `sleepScore`, `status`, `createdAt`
                    )
                    SELECT
                        `id`, `date`, `monitorStartTime`, NULL, NULL,
                        `firstAlertTime`, `sleepTime`, NULL,
                        0, `screenOnCheckCount`, `screenOnCheckCount`,
                        NULL, NULL, 10, 3, `totalAlertCount`, `sleepScore`,
                        CASE WHEN `sleepTime` IS NOT NULL THEN 'SLEEP_CONFIRMED' ELSE 'INCOMPLETE' END,
                        `createdAt`
                    FROM `sleep_records`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `sleep_records`")
                db.execSQL("ALTER TABLE `sleep_records_new` RENAME TO `sleep_records`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_records_monitorStartTime` ON `sleep_records` (`monitorStartTime`)"
                )
            }
        }

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
