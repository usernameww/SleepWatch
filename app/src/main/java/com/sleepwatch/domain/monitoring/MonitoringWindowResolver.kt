package com.sleepwatch.domain.monitoring

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class MonitoringWindowResolver(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun resolve(now: ZonedDateTime, settings: MonitoringSettings): MonitoringWindow {
        val localNow = now.withZoneSameInstant(zoneId)
        val today = localNow.toLocalDate()
        val previousWindow = buildWindow(today.minusDays(1), settings)
        if (previousWindow.isActiveAt(localNow.toInstant())) return previousWindow

        val todayWindow = buildWindow(today, settings)
        if (todayWindow.isActiveAt(localNow.toInstant()) || localNow.toInstant() < todayWindow.startAt) {
            return todayWindow
        }
        return buildWindow(today.plusDays(1), settings)
    }

    fun resolve(nowMillis: Long, settings: MonitoringSettings): MonitoringWindow =
        resolve(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zoneId), settings)

    private fun buildWindow(startDate: LocalDate, settings: MonitoringSettings): MonitoringWindow {
        val start = startDate.atTime(settings.startTime).atZone(zoneId)
        val endDate = if (settings.endTime <= settings.startTime) startDate.plusDays(1) else startDate
        val end = endDate.atTime(settings.endTime).atZone(zoneId)
        val target = listOf(startDate.minusDays(1), startDate, startDate.plusDays(1))
            .map { it.atTime(settings.targetBedtime).atZone(zoneId) }
            .minBy { abs(Duration.between(start, it).toMinutes()) }

        return MonitoringWindow(
            cycleDate = startDate,
            startAt = start.toInstant(),
            endAt = end.toInstant(),
            targetBedtimeAt = target.toInstant()
        )
    }
}
