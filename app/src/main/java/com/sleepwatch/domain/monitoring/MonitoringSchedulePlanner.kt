package com.sleepwatch.domain.monitoring

import java.time.Instant

sealed interface ScheduledMonitorAction {
    val at: Instant

    data class WindowStart(override val at: Instant) : ScheduledMonitorAction
    data class Check(override val at: Instant) : ScheduledMonitorAction
    data class WindowEnd(override val at: Instant) : ScheduledMonitorAction
}

class MonitoringSchedulePlanner {
    fun plan(
        now: Instant,
        window: MonitoringWindow,
        nextCheckAt: Instant?
    ): List<ScheduledMonitorAction> {
        if (!window.isActiveAt(now)) {
            return listOf(ScheduledMonitorAction.WindowStart(window.startAt))
        }
        if (nextCheckAt == null) {
            return listOf(ScheduledMonitorAction.WindowEnd(window.endAt))
        }
        val checkAt = if (nextCheckAt <= now) now else nextCheckAt
        return listOf(
            ScheduledMonitorAction.Check(checkAt),
            ScheduledMonitorAction.WindowEnd(window.endAt)
        )
    }
}
