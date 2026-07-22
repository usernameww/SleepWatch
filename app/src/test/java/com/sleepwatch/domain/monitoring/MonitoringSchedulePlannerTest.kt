package com.sleepwatch.domain.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MonitoringSchedulePlannerTest {
    private val planner = MonitoringSchedulePlanner()

    @Test
    fun `outside window schedules only next window start`() {
        val now = Instant.parse("2026-07-24T04:00:00Z")
        val window = MonitoringWindow(
            cycleDate = java.time.LocalDate.of(2026, 7, 24),
            startAt = Instant.parse("2026-07-24T15:00:00Z"),
            endAt = Instant.parse("2026-07-24T22:00:00Z"),
            targetBedtimeAt = Instant.parse("2026-07-24T15:00:00Z")
        )

        assertEquals(
            listOf(ScheduledMonitorAction.WindowStart(window.startAt)),
            planner.plan(now, window, null)
        )
    }

    @Test
    fun `active session schedules next check and window end`() {
        val now = Instant.parse("2026-07-23T16:05:00Z")
        val nextCheck = Instant.parse("2026-07-23T16:10:00Z")
        val end = Instant.parse("2026-07-23T21:00:00Z")
        val window = MonitoringWindow(
            cycleDate = java.time.LocalDate.of(2026, 7, 24),
            startAt = Instant.parse("2026-07-23T16:00:00Z"),
            endAt = end,
            targetBedtimeAt = Instant.parse("2026-07-23T15:00:00Z")
        )

        assertEquals(
            listOf(
                ScheduledMonitorAction.Check(nextCheck),
                ScheduledMonitorAction.WindowEnd(end)
            ),
            planner.plan(now, window, nextCheck)
        )
    }

    @Test
    fun `overdue check is scheduled immediately`() {
        val now = Instant.parse("2026-07-23T16:15:00Z")
        val window = MonitoringWindow(
            cycleDate = java.time.LocalDate.of(2026, 7, 24),
            startAt = Instant.parse("2026-07-23T16:00:00Z"),
            endAt = Instant.parse("2026-07-23T21:00:00Z"),
            targetBedtimeAt = Instant.parse("2026-07-23T15:00:00Z")
        )

        assertEquals(
            ScheduledMonitorAction.Check(now),
            planner.plan(now, window, Instant.parse("2026-07-23T16:10:00Z")).first()
        )
    }

    @Test
    fun `active session without another check schedules only window end`() {
        val now = Instant.parse("2026-07-23T20:55:00Z")
        val end = Instant.parse("2026-07-23T21:00:00Z")
        val window = MonitoringWindow(
            cycleDate = java.time.LocalDate.of(2026, 7, 24),
            startAt = Instant.parse("2026-07-23T16:00:00Z"),
            endAt = end,
            targetBedtimeAt = Instant.parse("2026-07-23T15:00:00Z")
        )

        assertEquals(
            listOf(ScheduledMonitorAction.WindowEnd(end)),
            planner.plan(now, window, null)
        )
    }
}
