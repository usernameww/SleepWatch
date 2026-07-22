package com.sleepwatch.domain.monitoring

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class MonitoringSettings(
    val startTime: LocalTime,
    val endTime: LocalTime,
    val targetBedtime: LocalTime,
    val intervalMinutes: Int,
    val inactiveThreshold: Int
) {
    init {
        require(intervalMinutes in 1..60) { "intervalMinutes must be between 1 and 60" }
        require(inactiveThreshold in 1..10) { "inactiveThreshold must be between 1 and 10" }
    }
}

data class MonitoringWindow(
    val cycleDate: LocalDate,
    val startAt: Instant,
    val endAt: Instant,
    val targetBedtimeAt: Instant
) {
    fun isActiveAt(instant: Instant): Boolean = instant >= startAt && instant < endAt
}

enum class MonitoringStatus {
    MONITORING,
    SLEEP_CONFIRMED,
    INCOMPLETE,
    SKIPPED,
    ENDED_BY_CONFIG_CHANGE
}

data class UsageSnapshot(
    val isInteractive: Boolean,
    val isDeviceLocked: Boolean
) {
    val isUsing: Boolean get() = isInteractive && !isDeviceLocked
}

data class MonitoringSessionSnapshot(
    val cycleDate: LocalDate,
    val status: MonitoringStatus,
    val windowStartAt: Instant,
    val windowEndAt: Instant,
    val targetBedtimeAt: Instant,
    val intervalMinutes: Int,
    val inactiveThreshold: Int,
    val firstInactiveCheckAt: Instant? = null,
    val consecutiveInactiveChecks: Int = 0,
    val totalCheckCount: Int = 0,
    val activeCheckCount: Int = 0,
    val lastCheckAt: Instant? = null,
    val nextCheckAt: Instant? = null,
    val sleepTime: Instant? = null
)

sealed interface MonitoringEvent {
    val at: Instant

    data class WindowStarted(
        override val at: Instant,
        val usage: UsageSnapshot,
        val intervalMinutes: Int,
        val inactiveThreshold: Int,
        val windowEndAt: Instant
    ) : MonitoringEvent

    data class ScheduledCheck(
        override val at: Instant,
        val usage: UsageSnapshot,
        val intervalMinutes: Int,
        val inactiveThreshold: Int,
        val windowEndAt: Instant
    ) : MonitoringEvent

    data class UserUnlocked(override val at: Instant) : MonitoringEvent
    data class WindowEnded(override val at: Instant) : MonitoringEvent
    data class Skipped(override val at: Instant) : MonitoringEvent

    data class Reconfigured(
        override val at: Instant,
        val isWithinWindow: Boolean,
        val intervalMinutes: Int,
        val inactiveThreshold: Int,
        val windowEndAt: Instant
    ) : MonitoringEvent
}

data class MonitoringDecision(
    val updatedSession: MonitoringSessionSnapshot,
    val showAlert: Boolean = false,
    val stopSession: Boolean = false,
    val nextCheckAt: Instant? = updatedSession.nextCheckAt
)
