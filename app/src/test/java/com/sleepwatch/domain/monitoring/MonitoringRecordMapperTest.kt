package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MonitoringRecordMapperTest {
    @Test
    fun `round trip preserves recoverable monitoring state`() {
        val record = SleepRecord(
            id = 7,
            date = "2026-07-24",
            monitorStartTime = 1000,
            monitorEndTime = 5000,
            targetBedtimeTime = 900,
            firstInactiveCheckTime = 2000,
            consecutiveInactiveChecks = 2,
            totalCheckCount = 4,
            activeCheckCount = 2,
            lastCheckTime = 3000,
            nextCheckTime = 4000,
            checkIntervalMinutes = 10,
            inactiveThreshold = 3,
            status = MonitoringStatus.MONITORING.name,
            createdAt = 1
        )

        val snapshot = record.toMonitoringSnapshot()
        val updated = snapshot.copy(
            status = MonitoringStatus.SLEEP_CONFIRMED,
            sleepTime = Instant.ofEpochMilli(2000),
            nextCheckAt = null
        )

        val result = record.withMonitoringSnapshot(updated)

        assertEquals(7, result.id)
        assertEquals("SLEEP_CONFIRMED", result.status)
        assertEquals(2000L, result.sleepTime)
        assertEquals(2, result.consecutiveInactiveChecks)
        assertEquals(null, result.nextCheckTime)
    }
}
