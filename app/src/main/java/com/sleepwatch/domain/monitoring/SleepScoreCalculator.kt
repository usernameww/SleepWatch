package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord

class SleepScoreCalculator {
    fun calculate(record: SleepRecord, consecutiveEarlyDays: Int): Float? {
        val sleepTime = record.sleepTime ?: return null
        val targetTime = record.targetBedtimeTime ?: return null
        if (record.status != MonitoringStatus.SLEEP_CONFIRMED.name) return null

        val minutesLate = ((sleepTime - targetTime) / 60_000L).toInt().coerceAtLeast(0)
        val latePenalty = minutesLate * 2f
        val alertPenalty = record.totalAlertCount * 3f
        val consecutiveBonus = consecutiveEarlyDays.coerceAtMost(20).toFloat()
        return (100f - latePenalty - alertPenalty + consecutiveBonus).coerceIn(0f, 100f)
    }
}
