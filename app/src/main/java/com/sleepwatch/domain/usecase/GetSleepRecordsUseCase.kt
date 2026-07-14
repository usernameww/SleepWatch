package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.repository.SleepRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSleepRecordsUseCase @Inject constructor(
    private val repository: SleepRecordRepository
) {
    fun getRecentRecords(limit: Int = 7): Flow<List<SleepRecord>> =
        repository.getRecentRecords(limit)

    fun getByDate(date: String): Flow<SleepRecord?> =
        repository.getByDateFlow(date)

    fun getLatestRecord(): Flow<SleepRecord?> =
        repository.getLatestRecord()

    fun getRecordsBetween(startDate: String, endDate: String): Flow<List<SleepRecord>> =
        repository.getRecordsBetween(startDate, endDate)
}
