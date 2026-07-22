package com.sleepwatch.domain.monitoring

import java.time.Duration

class MonitoringEngine {
    fun reduce(
        session: MonitoringSessionSnapshot,
        event: MonitoringEvent
    ): MonitoringDecision {
        if (session.status != MonitoringStatus.MONITORING) {
            return MonitoringDecision(session, stopSession = true, nextCheckAt = null)
        }
        return when (event) {
            is MonitoringEvent.WindowStarted -> handleCheck(
                session,
                event.at,
                event.usage,
                event.intervalMinutes,
                event.inactiveThreshold,
                event.windowEndAt
            )
            is MonitoringEvent.ScheduledCheck -> handleCheck(
                session,
                event.at,
                event.usage,
                event.intervalMinutes,
                event.inactiveThreshold,
                event.windowEndAt
            )
            is MonitoringEvent.UserUnlocked -> MonitoringDecision(
                session.copy(
                    firstInactiveCheckAt = null,
                    consecutiveInactiveChecks = 0
                )
            )
            is MonitoringEvent.WindowEnded -> stop(
                session.copy(status = MonitoringStatus.INCOMPLETE, nextCheckAt = null)
            )
            is MonitoringEvent.Skipped -> stop(
                session.copy(status = MonitoringStatus.SKIPPED, nextCheckAt = null)
            )
            is MonitoringEvent.Reconfigured -> handleReconfigured(session, event)
        }
    }

    private fun handleCheck(
        session: MonitoringSessionSnapshot,
        at: java.time.Instant,
        usage: UsageSnapshot,
        intervalMinutes: Int,
        inactiveThreshold: Int,
        windowEndAt: java.time.Instant
    ): MonitoringDecision {
        if (session.lastCheckAt != null && at <= session.lastCheckAt) {
            return MonitoringDecision(session)
        }
        if (at >= windowEndAt) {
            return stop(session.copy(status = MonitoringStatus.INCOMPLETE, nextCheckAt = null))
        }

        val nextCheck = at.plus(Duration.ofMinutes(intervalMinutes.toLong()))
            .takeIf { it < windowEndAt }
        if (usage.isUsing) {
            val updated = session.copy(
                intervalMinutes = intervalMinutes,
                inactiveThreshold = inactiveThreshold,
                windowEndAt = windowEndAt,
                firstInactiveCheckAt = null,
                consecutiveInactiveChecks = 0,
                totalCheckCount = session.totalCheckCount + 1,
                activeCheckCount = session.activeCheckCount + 1,
                lastCheckAt = at,
                nextCheckAt = nextCheck
            )
            return MonitoringDecision(updated, showAlert = true, nextCheckAt = nextCheck)
        }

        val firstInactive = session.firstInactiveCheckAt ?: at
        val inactiveCount = session.consecutiveInactiveChecks + 1
        val confirmed = inactiveCount >= inactiveThreshold
        val updated = session.copy(
            status = if (confirmed) MonitoringStatus.SLEEP_CONFIRMED else MonitoringStatus.MONITORING,
            intervalMinutes = intervalMinutes,
            inactiveThreshold = inactiveThreshold,
            windowEndAt = windowEndAt,
            firstInactiveCheckAt = firstInactive,
            consecutiveInactiveChecks = inactiveCount,
            totalCheckCount = session.totalCheckCount + 1,
            lastCheckAt = at,
            nextCheckAt = if (confirmed) null else nextCheck,
            sleepTime = if (confirmed) firstInactive else null
        )
        return MonitoringDecision(
            updatedSession = updated,
            stopSession = confirmed,
            nextCheckAt = updated.nextCheckAt
        )
    }

    private fun handleReconfigured(
        session: MonitoringSessionSnapshot,
        event: MonitoringEvent.Reconfigured
    ): MonitoringDecision {
        if (!event.isWithinWindow) {
            return stop(
                session.copy(
                    status = MonitoringStatus.ENDED_BY_CONFIG_CHANGE,
                    nextCheckAt = null
                )
            )
        }
        if (session.firstInactiveCheckAt != null &&
            session.consecutiveInactiveChecks >= event.inactiveThreshold
        ) {
            return stop(
                session.copy(
                    status = MonitoringStatus.SLEEP_CONFIRMED,
                    intervalMinutes = event.intervalMinutes,
                    inactiveThreshold = event.inactiveThreshold,
                    windowEndAt = event.windowEndAt,
                    nextCheckAt = null,
                    sleepTime = session.firstInactiveCheckAt
                )
            )
        }
        val next = event.at.plus(Duration.ofMinutes(event.intervalMinutes.toLong()))
            .takeIf { it < event.windowEndAt }
        return MonitoringDecision(
            session.copy(
                intervalMinutes = event.intervalMinutes,
                inactiveThreshold = event.inactiveThreshold,
                windowEndAt = event.windowEndAt,
                nextCheckAt = next
            ),
            nextCheckAt = next
        )
    }

    private fun stop(session: MonitoringSessionSnapshot) = MonitoringDecision(
        updatedSession = session,
        stopSession = true,
        nextCheckAt = null
    )
}
