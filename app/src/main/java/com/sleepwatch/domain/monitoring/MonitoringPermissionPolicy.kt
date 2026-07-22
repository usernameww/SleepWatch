package com.sleepwatch.domain.monitoring

enum class MonitoringPermission {
    NOTIFICATIONS,
    OVERLAY,
    EXACT_ALARMS,
    BATTERY_UNRESTRICTED
}

data class MonitoringPermissionHealth(
    val notificationsGranted: Boolean,
    val overlayGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val batteryUnrestricted: Boolean
) {
    val missingRequired: Set<MonitoringPermission>
        get() = buildSet {
            if (!notificationsGranted) add(MonitoringPermission.NOTIFICATIONS)
            if (!overlayGranted) add(MonitoringPermission.OVERLAY)
            if (!exactAlarmsGranted) add(MonitoringPermission.EXACT_ALARMS)
        }

    val missingRecommended: Set<MonitoringPermission>
        get() = buildSet {
            if (!batteryUnrestricted) add(MonitoringPermission.BATTERY_UNRESTRICTED)
        }

    val canEnableMonitoring: Boolean
        get() = missingRequired.isEmpty()
}
