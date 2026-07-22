package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.repository.SleepRecordRepository
import com.sleepwatch.domain.monitoring.SleepScoreCalculator
import javax.inject.Inject

class SaveSleepRecordUseCase @Inject constructor(
    private val repository: SleepRecordRepository
) {
    suspend fun calculateAndSaveScore(record: SleepRecord, consecutiveEarlyDays: Int): SleepRecord {
        val score = SleepScoreCalculator().calculate(record, consecutiveEarlyDays) ?: return record
        val updated = record.copy(sleepScore = score)
        repository.update(updated)
        return updated
    }
}
