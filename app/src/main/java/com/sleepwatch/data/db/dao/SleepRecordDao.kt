package com.sleepwatch.data.db.dao

import androidx.room.*
import com.sleepwatch.data.db.entity.SleepRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SleepRecord): Long

    @Update
    suspend fun update(record: SleepRecord)

    @Query("SELECT * FROM sleep_records WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): SleepRecord?

    @Query("SELECT * FROM sleep_records WHERE date = :date LIMIT 1")
    fun getByDateFlow(date: String): Flow<SleepRecord?>

    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<SleepRecord>>

    @Query("SELECT * FROM sleep_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getRecordsBetween(startDate: String, endDate: String): Flow<List<SleepRecord>>

    @Query("SELECT COUNT(*) FROM sleep_records WHERE sleepTime IS NOT NULL AND sleepTime <= :targetTimestamp AND date BETWEEN :startDate AND :endDate")
    suspend fun countEarlySleeps(targetTimestamp: Long, startDate: String, endDate: String): Int

    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT 1")
    fun getLatestRecord(): Flow<SleepRecord?>

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAll()
}
