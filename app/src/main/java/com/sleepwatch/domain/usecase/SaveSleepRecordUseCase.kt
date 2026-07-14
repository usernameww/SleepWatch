package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.repository.SleepRecordRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class SaveSleepRecordUseCase @Inject constructor(
    private val repository: SleepRecordRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun getOrCreateTodayRecord(monitorStartHour: Int, monitorStartMinute: Int): SleepRecord {
        val today = dateFormat.format(Date())
        return repository.getByDate(today) ?: run {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, monitorStartHour)
                set(Calendar.MINUTE, monitorStartMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val record = SleepRecord(
                date = today,
                monitorStartTime = cal.timeInMillis
            )
            val id = repository.insert(record)
            record.copy(id = id)
        }
    }

    suspend fun recordAlert(record: SleepRecord): SleepRecord {
        val updated = record.copy(
            totalAlertCount = record.totalAlertCount + 1,
            firstAlertTime = record.firstAlertTime ?: System.currentTimeMillis()
        )
        repository.update(updated)
        return updated
    }

    suspend fun recordScreenOnCheck(record: SleepRecord): SleepRecord {
        val updated = record.copy(screenOnCheckCount = record.screenOnCheckCount + 1)
        repository.update(updated)
        return updated
    }

    suspend fun recordSleepTime(record: SleepRecord): SleepRecord {
        val updated = record.copy(sleepTime = System.currentTimeMillis())
        repository.update(updated)
        return updated
    }

    suspend fun recordEmergency(record: SleepRecord): SleepRecord {
        val updated = record.copy(hasEmergency = true)
        repository.update(updated)
        return updated
    }

    suspend fun calculateAndSaveScore(
        record: SleepRecord,
        targetHour: Int,
        targetMinute: Int,
        consecutiveEarlyDays: Int
    ): SleepRecord {
        if (record.sleepTime == null) return record

        val baseScore = 100f
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = record.sleepTime
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
        }

        val minutesLate = ((record.sleepTime - targetCal.timeInMillis) / 60000).toInt().coerceAtLeast(0)
        val latePenalty = minutesLate * 2f
        val alertPenalty = record.totalAlertCount * 3f
        val consecutiveBonus = (consecutiveEarlyDays * 1).coerceAtMost(20).toFloat()

        val score = (baseScore - latePenalty - alertPenalty + consecutiveBonus).coerceIn(0f, 100f)
        val updated = record.copy(sleepScore = score)
        repository.update(updated)
        return updated
    }
}
