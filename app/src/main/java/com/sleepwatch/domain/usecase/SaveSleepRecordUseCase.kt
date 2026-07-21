package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.repository.SleepRecordRepository
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SaveSleepRecordUseCase @Inject constructor(
    private val repository: SleepRecordRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val mutex = Mutex()

    /**
     * 根据监测开始时间计算监测周期的日期。
     * 如果当前时间在监测开始时间之前，说明还在昨晚的监测周期，返回昨天的日期。
     */
    fun getMonitoringDate(monitorStartHour: Int, monitorStartMinute: Int): String {
        val now = Calendar.getInstance()
        val monitorStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, monitorStartHour)
            set(Calendar.MINUTE, monitorStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // 如果当前时间在监测开始时间之前，说明还在昨晚的监测周期
        if (now.before(monitorStart)) {
            now.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dateFormat.format(now.time)
    }

    suspend fun getOrCreateTodayRecord(monitorStartHour: Int, monitorStartMinute: Int): SleepRecord = mutex.withLock {
        val today = getMonitoringDate(monitorStartHour, monitorStartMinute)
        repository.getByDate(today) ?: run {
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
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
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
