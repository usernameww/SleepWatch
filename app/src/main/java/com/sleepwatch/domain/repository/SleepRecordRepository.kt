package com.sleepwatch.domain.repository

import com.sleepwatch.data.db.entity.SleepRecord
import kotlinx.coroutines.flow.Flow

interface SleepRecordRepository {
    suspend fun insert(record: SleepRecord): Long
    suspend fun update(record: SleepRecord)
    suspend fun getByDate(date: String): SleepRecord?
    suspend fun getByMonitorStartTime(monitorStartTime: Long): SleepRecord?
    suspend fun getById(id: Long): SleepRecord?
    suspend fun getActiveRecord(): SleepRecord?
    fun getByDateFlow(date: String): Flow<SleepRecord?>
    fun getRecentRecords(limit: Int): Flow<List<SleepRecord>>
    fun getRecordsBetween(startDate: String, endDate: String): Flow<List<SleepRecord>>
    fun getLatestRecord(): Flow<SleepRecord?>
    suspend fun deleteAll()
}
