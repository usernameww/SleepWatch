package com.sleepwatch.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import android.database.sqlite.SQLiteConstraintException
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class SleepWatchDatabaseMigrationTest {
    private val dbName = "sleepwatch-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SleepWatchDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migration1To2_preservesRecordsAndRemovesEmergency() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """INSERT INTO sleep_records
                    (id, date, monitorStartTime, firstAlertTime, sleepTime,
                     totalAlertCount, screenOnCheckCount, sleepScore, hasEmergency, createdAt)
                    VALUES (1, '2026-07-20', 1000, 1100, 1200, 2, 4, 90, 1, 900)
                """.trimIndent()
            )
            execSQL(
                """INSERT INTO sleep_records
                    (id, date, monitorStartTime, firstAlertTime, sleepTime,
                     totalAlertCount, screenOnCheckCount, sleepScore, hasEmergency, createdAt)
                    VALUES (2, '2026-07-21', 2000, NULL, NULL, 0, 0, NULL, 0, 1900)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName,
            2,
            true,
            SleepWatchDatabase.MIGRATION_1_2
        )

        db.query("PRAGMA table_info(sleep_records)").use { cursor ->
            val names = buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
            assertFalse(names.contains("hasEmergency"))
            assertFalse(names.contains("screenOnCheckCount"))
            assertEquals(true, names.contains("firstInactiveCheckTime"))
            assertEquals(true, names.contains("activeCheckCount"))
        }

        db.query("SELECT id, status, sleepTime, activeCheckCount, checkIntervalMinutes, inactiveThreshold, targetBedtimeTime FROM sleep_records ORDER BY id").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals("SLEEP_CONFIRMED", cursor.getString(1))
            assertEquals(1200L, cursor.getLong(2))
            assertEquals(4, cursor.getInt(3))
            assertEquals(10, cursor.getInt(4))
            assertEquals(3, cursor.getInt(5))
            assertEquals(true, cursor.isNull(6))

            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertEquals("INCOMPLETE", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
        }

        db.execSQL(
            """INSERT INTO sleep_records
                (date, monitorStartTime, consecutiveInactiveChecks, totalCheckCount,
                 activeCheckCount, checkIntervalMinutes, inactiveThreshold, totalAlertCount,
                 status, createdAt)
                VALUES ('2026-07-20', 3000, 0, 0, 0, 10, 3, 0, 'INCOMPLETE', 3000)
            """.trimIndent()
        )
        try {
            db.execSQL(
                """INSERT INTO sleep_records
                    (date, monitorStartTime, consecutiveInactiveChecks, totalCheckCount,
                     activeCheckCount, checkIntervalMinutes, inactiveThreshold, totalAlertCount,
                     status, createdAt)
                    VALUES ('2026-07-22', 1000, 0, 0, 0, 10, 3, 0, 'INCOMPLETE', 3000)
                """.trimIndent()
            )
            fail("monitoring window unique constraint should be preserved")
        } catch (_: SQLiteConstraintException) {
            // Expected.
        }
        db.close()
    }
}
