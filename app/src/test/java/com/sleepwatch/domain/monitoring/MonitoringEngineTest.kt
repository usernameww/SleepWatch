package com.sleepwatch.domain.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MonitoringEngineTest {
    private val engine = MonitoringEngine()
    private val start = Instant.parse("2026-07-23T16:00:00Z") // 00:00 Asia/Shanghai
    private val end = Instant.parse("2026-07-23T21:00:00Z")

    @Test
    fun `third inactive check confirms sleep at first inactive check`() {
        var session = newSession()

        session = engine.reduce(session, scheduledCheck(start, isUsing = false)).updatedSession
        session = engine.reduce(session, scheduledCheck(start.plusSeconds(600), isUsing = false)).updatedSession
        val decision = engine.reduce(session, scheduledCheck(start.plusSeconds(1200), isUsing = false))

        assertEquals(MonitoringStatus.SLEEP_CONFIRMED, decision.updatedSession.status)
        assertEquals(start, decision.updatedSession.sleepTime)
        assertEquals(3, decision.updatedSession.consecutiveInactiveChecks)
        assertEquals(3, decision.updatedSession.totalCheckCount)
        assertTrue(decision.stopSession)
        assertNull(decision.nextCheckAt)
    }

    @Test
    fun `active check resets candidate and requests alert`() {
        var session = newSession()
        session = engine.reduce(session, scheduledCheck(start, isUsing = false)).updatedSession

        val decision = engine.reduce(
            session,
            scheduledCheck(start.plusSeconds(600), isUsing = true)
        )

        assertEquals(0, decision.updatedSession.consecutiveInactiveChecks)
        assertNull(decision.updatedSession.firstInactiveCheckAt)
        assertEquals(1, decision.updatedSession.activeCheckCount)
        assertTrue(decision.showAlert)
        assertFalse(decision.stopSession)
    }

    @Test
    fun `unlock between checks resets inactive sequence without counting a check`() {
        var session = engine.reduce(
            newSession(),
            scheduledCheck(start, isUsing = false)
        ).updatedSession

        session = engine.reduce(
            session,
            MonitoringEvent.UserUnlocked(start.plusSeconds(120))
        ).updatedSession

        assertEquals(0, session.consecutiveInactiveChecks)
        assertNull(session.firstInactiveCheckAt)
        assertEquals(1, session.totalCheckCount)
    }

    @Test
    fun `duplicate scheduled check is idempotent`() {
        val event = scheduledCheck(start, isUsing = true)
        val first = engine.reduce(newSession(), event).updatedSession
        val duplicate = engine.reduce(first, event)

        assertEquals(first, duplicate.updatedSession)
        assertFalse(duplicate.showAlert)
    }

    @Test
    fun `window end leaves sleep time empty and marks incomplete`() {
        val decision = engine.reduce(newSession(), MonitoringEvent.WindowEnded(end))

        assertEquals(MonitoringStatus.INCOMPLETE, decision.updatedSession.status)
        assertNull(decision.updatedSession.sleepTime)
        assertTrue(decision.stopSession)
    }

    @Test
    fun `reconfigure outside new window ends active session`() {
        val decision = engine.reduce(
            newSession(),
            MonitoringEvent.Reconfigured(
                at = start.plusSeconds(60),
                isWithinWindow = false,
                intervalMinutes = 5,
                inactiveThreshold = 2,
                windowEndAt = end
            )
        )

        assertEquals(MonitoringStatus.ENDED_BY_CONFIG_CHANGE, decision.updatedSession.status)
        assertTrue(decision.stopSession)
    }

    @Test
    fun `threshold one confirms on first inactive check`() {
        val decision = engine.reduce(
            newSession().copy(inactiveThreshold = 1),
            MonitoringEvent.ScheduledCheck(
                at = start,
                usage = UsageSnapshot(isInteractive = false, isDeviceLocked = true),
                intervalMinutes = 1,
                inactiveThreshold = 1,
                windowEndAt = end
            )
        )

        assertEquals(MonitoringStatus.SLEEP_CONFIRMED, decision.updatedSession.status)
        assertEquals(start, decision.updatedSession.sleepTime)
    }

    @Test
    fun `sixty minute interval schedules exact next check`() {
        val decision = engine.reduce(
            newSession(),
            MonitoringEvent.ScheduledCheck(
                at = start,
                usage = UsageSnapshot(isInteractive = true, isDeviceLocked = false),
                intervalMinutes = 60,
                inactiveThreshold = 10,
                windowEndAt = end
            )
        )

        assertEquals(start.plusSeconds(3600), decision.nextCheckAt)
    }

    @Test
    fun `lowering threshold confirms with original inactive time`() {
        var session = engine.reduce(newSession(), scheduledCheck(start, isUsing = false)).updatedSession
        session = engine.reduce(
            session,
            scheduledCheck(start.plusSeconds(600), isUsing = false)
        ).updatedSession

        val decision = engine.reduce(
            session,
            MonitoringEvent.Reconfigured(
                at = start.plusSeconds(700),
                isWithinWindow = true,
                intervalMinutes = 5,
                inactiveThreshold = 2,
                windowEndAt = end
            )
        )

        assertEquals(MonitoringStatus.SLEEP_CONFIRMED, decision.updatedSession.status)
        assertEquals(start, decision.updatedSession.sleepTime)
        assertTrue(decision.stopSession)
    }

    private fun scheduledCheck(at: Instant, isUsing: Boolean) = MonitoringEvent.ScheduledCheck(
        at = at,
        usage = UsageSnapshot(isInteractive = isUsing, isDeviceLocked = false),
        intervalMinutes = 10,
        inactiveThreshold = 3,
        windowEndAt = end
    )

    private fun newSession() = MonitoringSessionSnapshot(
        cycleDate = LocalDate.of(2026, 7, 24),
        status = MonitoringStatus.MONITORING,
        windowStartAt = start,
        windowEndAt = end,
        targetBedtimeAt = start.minusSeconds(3600),
        intervalMinutes = 10,
        inactiveThreshold = 3
    )
}
