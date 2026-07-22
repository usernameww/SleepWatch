package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SleepScoreCalculatorTest {
    private val calculator = SleepScoreCalculator()

    @Test
    fun `uses target timestamp stored on the record`() {
        val target = Instant.parse("2026-07-23T15:00:00Z").toEpochMilli()
        val sleep = Instant.parse("2026-07-23T15:15:00Z").toEpochMilli()
        val record = SleepRecord(
            date = "2026-07-23",
            monitorStartTime = target,
            targetBedtimeTime = target,
            sleepTime = sleep,
            totalAlertCount = 1,
            status = MonitoringStatus.SLEEP_CONFIRMED.name,
            createdAt = target
        )

        assertEquals(67f, calculator.calculate(record, consecutiveEarlyDays = 0))
    }

    @Test
    fun `record without concrete target has no score`() {
        val record = SleepRecord(
            date = "2026-07-20",
            monitorStartTime = 1000,
            sleepTime = 2000,
            status = MonitoringStatus.SLEEP_CONFIRMED.name,
            createdAt = 1000
        )

        assertEquals(null, calculator.calculate(record, 0))
    }

    @Test
    fun `consecutive early days add a capped bonus`() {
        val target = Instant.parse("2026-07-23T15:00:00Z").toEpochMilli()
        val record = SleepRecord(
            date = "2026-07-23",
            monitorStartTime = target,
            targetBedtimeTime = target,
            sleepTime = target + 10 * 60_000,
            status = MonitoringStatus.SLEEP_CONFIRMED.name,
            createdAt = target
        )

        assertEquals(100f, calculator.calculate(record, consecutiveEarlyDays = 30))
    }
}
