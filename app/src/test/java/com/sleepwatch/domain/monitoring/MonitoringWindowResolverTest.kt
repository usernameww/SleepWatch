package com.sleepwatch.domain.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class MonitoringWindowResolverTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val resolver = MonitoringWindowResolver(zone)

    @Test
    fun `resolves overnight window from previous calendar day`() {
        val settings = MonitoringSettings(
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(6, 0),
            targetBedtime = LocalTime.of(23, 30),
            intervalMinutes = 10,
            inactiveThreshold = 3
        )

        val now = ZonedDateTime.of(2026, 7, 24, 1, 0, 0, 0, zone)
        val window = resolver.resolve(now, settings)

        assertEquals(LocalDate.of(2026, 7, 23), window.cycleDate)
        assertEquals(ZonedDateTime.of(2026, 7, 23, 23, 0, 0, 0, zone).toInstant(), window.startAt)
        assertEquals(ZonedDateTime.of(2026, 7, 24, 6, 0, 0, 0, zone).toInstant(), window.endAt)
        assertTrue(window.isActiveAt(now.toInstant()))
    }

    @Test
    fun `midnight start uses previous evening as nearest target bedtime`() {
        val settings = MonitoringSettings(
            startTime = LocalTime.MIDNIGHT,
            endTime = LocalTime.of(5, 0),
            targetBedtime = LocalTime.of(23, 0),
            intervalMinutes = 10,
            inactiveThreshold = 3
        )

        val now = ZonedDateTime.of(2026, 7, 24, 0, 30, 0, 0, zone)
        val window = resolver.resolve(now, settings)

        assertEquals(LocalDate.of(2026, 7, 24), window.cycleDate)
        assertEquals(ZonedDateTime.of(2026, 7, 23, 23, 0, 0, 0, zone).toInstant(), window.targetBedtimeAt)
    }

    @Test
    fun `outside window resolves next start without claiming active`() {
        val settings = MonitoringSettings(
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(6, 0),
            targetBedtime = LocalTime.of(23, 0),
            intervalMinutes = 10,
            inactiveThreshold = 3
        )

        val now = ZonedDateTime.of(2026, 7, 24, 12, 0, 0, 0, zone)
        val window = resolver.resolve(now, settings)

        assertEquals(LocalDate.of(2026, 7, 24), window.cycleDate)
        assertFalse(window.isActiveAt(now.toInstant()))
        assertEquals(ZonedDateTime.of(2026, 7, 24, 23, 0, 0, 0, zone).toInstant(), window.startAt)
    }

    @Test
    fun `overnight window crosses year boundary`() {
        val settings = MonitoringSettings(
            startTime = LocalTime.of(23, 30),
            endTime = LocalTime.of(1, 0),
            targetBedtime = LocalTime.of(23, 30),
            intervalMinutes = 60,
            inactiveThreshold = 10
        )

        val now = ZonedDateTime.of(2027, 1, 1, 0, 10, 0, 0, zone)
        val window = resolver.resolve(now, settings)

        assertEquals(LocalDate.of(2026, 12, 31), window.cycleDate)
        assertEquals(ZonedDateTime.of(2027, 1, 1, 1, 0, 0, 0, zone).toInstant(), window.endAt)
    }
}
