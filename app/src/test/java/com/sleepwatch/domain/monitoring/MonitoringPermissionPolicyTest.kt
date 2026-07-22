package com.sleepwatch.domain.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringPermissionPolicyTest {
    @Test
    fun `required permissions block monitoring`() {
        val health = MonitoringPermissionHealth(
            notificationsGranted = true,
            overlayGranted = false,
            exactAlarmsGranted = true,
            batteryUnrestricted = true
        )

        assertFalse(health.canEnableMonitoring)
        assertTrue(health.missingRequired.contains(MonitoringPermission.OVERLAY))
    }

    @Test
    fun `battery optimization is recommended but does not block monitoring`() {
        val health = MonitoringPermissionHealth(
            notificationsGranted = true,
            overlayGranted = true,
            exactAlarmsGranted = true,
            batteryUnrestricted = false
        )

        assertTrue(health.canEnableMonitoring)
        assertTrue(health.missingRecommended.contains(MonitoringPermission.BATTERY_UNRESTRICTED))
    }
}
