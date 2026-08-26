package com.sleepwatch.service.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRecoveryCoordinatorTest {
    @Test
    fun `enabled monitoring schedules exact reconciliation one second later`() = runTest {
        var scheduledAt: Long? = null
        var directStarts = 0
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { scheduledAt = it },
            isBatteryUnrestricted = { false },
            startDirectReconcile = { directStarts++ },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.EXACT_ALARM_SCHEDULED, result)
        assertEquals(11_000L, scheduledAt)
        assertEquals(0, directStarts)
    }

    @Test
    fun `disabled monitoring performs no recovery action`() = runTest {
        var scheduled = false
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { false },
            scheduleExactReconcile = { scheduled = true },
            isBatteryUnrestricted = { true },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.MONITORING_DISABLED, result)
        assertFalse(scheduled)
        assertFalse(directStarted)
    }

    @Test
    fun `missing exact alarm permission uses direct start only when battery unrestricted`() = runTest {
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { throw SecurityException("exact alarm unavailable") },
            isBatteryUnrestricted = { true },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.DIRECT_RECONCILE_STARTED, result)
        assertTrue(directStarted)
    }

    @Test
    fun `missing exact alarm permission without battery exemption fails closed`() = runTest {
        val securityError = SecurityException("exact alarm unavailable")
        var reported: Throwable? = null
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { throw securityError },
            isBatteryUnrestricted = { false },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L },
            onFailure = { reported = it }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.FAILED, result)
        assertFalse(directStarted)
        assertSame(securityError, reported)
    }

    @Test
    fun `rejected direct start is reported and contained`() = runTest {
        val startError = IllegalStateException("background start rejected")
        var reported: Throwable? = null
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { throw SecurityException("exact alarm unavailable") },
            isBatteryUnrestricted = { true },
            startDirectReconcile = { throw startError },
            nowMillis = { 10_000L },
            onFailure = { reported = it }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.FAILED, result)
        assertSame(startError, reported)
    }

    @Test
    fun `settings read failure is reported and contained`() = runTest {
        val readError = IllegalStateException("settings unavailable")
        var reported: Throwable? = null
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { throw readError },
            scheduleExactReconcile = {},
            isBatteryUnrestricted = { true },
            startDirectReconcile = {},
            nowMillis = { 10_000L },
            onFailure = { reported = it }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.FAILED, result)
        assertSame(readError, reported)
    }
}
