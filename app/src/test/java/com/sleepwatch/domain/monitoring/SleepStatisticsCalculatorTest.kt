package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

class SleepStatisticsCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val calculator = SleepStatisticsCalculator(zone)

    @Test
    fun `statistics include only confirmed records`() {
        val confirmed = record(
            date = "2026-07-22",
            sleep = "2026-07-22T16:10:00Z",
            target = "2026-07-22T16:20:00Z",
            status = MonitoringStatus.SLEEP_CONFIRMED,
            score = 80f
        )
        val incomplete = record(
            date = "2026-07-23",
            sleep = "2026-07-23T17:00:00Z",
            target = "2026-07-23T16:20:00Z",
            status = MonitoringStatus.INCOMPLETE,
            score = 20f
        )

        assertEquals(listOf(confirmed), calculator.confirmed(listOf(confirmed, incomplete)))
        assertEquals(80.0, calculator.averageScore(listOf(confirmed, incomplete))!!, 0.0)
        assertEquals(1, calculator.goalAchievedCount(listOf(confirmed, incomplete)))
    }

    @Test
    fun `goal comparison uses target saved on each record`() {
        val early = record(
            date = "2026-07-22",
            sleep = "2026-07-22T16:10:00Z",
            target = "2026-07-22T16:20:00Z"
        )
        val late = record(
            date = "2026-07-23",
            sleep = "2026-07-23T16:30:00Z",
            target = "2026-07-23T16:20:00Z"
        )

        assertTrue(calculator.isGoalAchieved(early))
        assertFalse(calculator.isGoalAchieved(late))
        assertEquals(1, calculator.goalAchievedCount(listOf(early, late)))
        assertEquals(0.5, calculator.goalAchievementRate(listOf(early, late))!!, 0.0)
    }

    @Test
    fun `average sleep time wraps correctly across midnight`() {
        val beforeMidnight = record(
            date = "2026-07-22",
            sleep = "2026-07-22T15:50:00Z",
            target = "2026-07-22T16:30:00Z"
        )
        val afterMidnight = record(
            date = "2026-07-23",
            sleep = "2026-07-22T16:10:00Z",
            target = "2026-07-22T16:30:00Z"
        )

        assertEquals(LocalTime.MIDNIGHT, calculator.averageSleepTime(listOf(beforeMidnight, afterMidnight)))
    }

    @Test
    fun `average sleep time is circular around any time of day`() {
        val beforeNoon = record(
            date = "2026-07-22",
            sleep = "2026-07-22T03:50:00Z",
            target = "2026-07-22T04:30:00Z"
        )
        val afterNoon = record(
            date = "2026-07-23",
            sleep = "2026-07-22T04:10:00Z",
            target = "2026-07-22T04:30:00Z"
        )

        assertEquals(LocalTime.NOON, calculator.averageSleepTime(listOf(beforeNoon, afterNoon)))
    }

    @Test
    fun `goal rate excludes confirmed records without historical target`() {
        val knownTarget = record(
            date = "2026-07-22",
            sleep = "2026-07-22T16:10:00Z",
            target = "2026-07-22T16:20:00Z"
        )
        val migrated = knownTarget.copy(date = "2026-07-21", targetBedtimeTime = null)

        assertEquals(1.0, calculator.goalAchievementRate(listOf(knownTarget, migrated))!!, 0.0)
    }

    private fun record(
        date: String,
        sleep: String,
        target: String,
        status: MonitoringStatus = MonitoringStatus.SLEEP_CONFIRMED,
        score: Float = 90f
    ) = SleepRecord(
        date = date,
        monitorStartTime = Instant.parse(sleep).toEpochMilli(),
        sleepTime = Instant.parse(sleep).toEpochMilli(),
        targetBedtimeTime = Instant.parse(target).toEpochMilli(),
        status = status.name,
        sleepScore = score,
        createdAt = Instant.parse(sleep).toEpochMilli()
    )
}
