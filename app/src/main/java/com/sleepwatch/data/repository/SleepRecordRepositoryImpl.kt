package com.sleepwatch.data.repository

import com.sleepwatch.data.db.dao.SleepRecordDao
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.repository.SleepRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRecordRepositoryImpl @Inject constructor(
    private val dao: SleepRecordDao
) : SleepRecordRepository {

    override suspend fun insert(record: SleepRecord): Long = dao.insert(record)

    override suspend fun update(record: SleepRecord) = dao.update(record)

    override suspend fun getByDate(date: String): SleepRecord? = dao.getByDate(date)
    override suspend fun getByMonitorStartTime(monitorStartTime: Long): SleepRecord? =
        dao.getByMonitorStartTime(monitorStartTime)

    override suspend fun getById(id: Long): SleepRecord? = dao.getById(id)

    override suspend fun getActiveRecord(): SleepRecord? = dao.getActiveRecord()

    override fun getByDateFlow(date: String): Flow<SleepRecord?> = dao.getByDateFlow(date)

    override fun getRecentRecords(limit: Int): Flow<List<SleepRecord>> = dao.getRecentRecords(limit)

    override fun getRecordsBetween(startDate: String, endDate: String): Flow<List<SleepRecord>> =
        dao.getRecordsBetween(startDate, endDate)

    override fun getLatestRecord(): Flow<SleepRecord?> = dao.getLatestRecord()

    override suspend fun deleteAll() = dao.deleteAll()
}
