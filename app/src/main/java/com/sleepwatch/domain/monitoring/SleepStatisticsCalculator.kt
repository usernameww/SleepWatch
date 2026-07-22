package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class SleepStatisticsCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun confirmed(records: List<SleepRecord>): List<SleepRecord> = records.filter {
        it.status == MonitoringStatus.SLEEP_CONFIRMED.name && it.sleepTime != null
    }

    fun averageSleepTime(records: List<SleepRecord>): LocalTime? {
        val minutesOfDay = confirmed(records).map { record ->
            val time = Instant.ofEpochMilli(requireNotNull(record.sleepTime))
                .atZone(zoneId)
                .toLocalTime()
            time.hour * MINUTES_PER_HOUR + time.minute
        }
        if (minutesOfDay.isEmpty()) return null
        val angles = minutesOfDay.map { it.toDouble() / MINUTES_PER_DAY * TWO_PI }
        val meanSin = angles.sumOf(::sin) / angles.size
        val meanCos = angles.sumOf(::cos) / angles.size
        val average = if (hypot(meanSin, meanCos) < 1e-9) {
            minutesOfDay.average().roundToInt().mod(MINUTES_PER_DAY)
        } else {
            (atan2(meanSin, meanCos).mod(TWO_PI) / TWO_PI * MINUTES_PER_DAY)
                .roundToInt()
                .mod(MINUTES_PER_DAY)
        }
        return LocalTime.of(average / MINUTES_PER_HOUR, average % MINUTES_PER_HOUR)
    }

    fun averageScore(records: List<SleepRecord>): Double? =
        confirmed(records).mapNotNull { it.sleepScore?.toDouble() }
            .takeIf { it.isNotEmpty() }
            ?.average()

    fun isGoalAchieved(record: SleepRecord): Boolean =
        record.status == MonitoringStatus.SLEEP_CONFIRMED.name &&
            record.sleepTime != null &&
            record.targetBedtimeTime != null &&
            record.sleepTime <= record.targetBedtimeTime

    fun goalAchievedCount(records: List<SleepRecord>): Int = records.count(::isGoalAchieved)

    fun goalAchievementRate(records: List<SleepRecord>): Double? {
        val eligible = confirmed(records).filter { it.targetBedtimeTime != null }
        if (eligible.isEmpty()) return null
        return eligible.count(::isGoalAchieved).toDouble() / eligible.size
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        private const val TWO_PI = 2 * PI
    }
}
