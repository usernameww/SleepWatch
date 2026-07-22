package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.SleepRecord
import java.time.Instant
import java.time.LocalDate

fun SleepRecord.toMonitoringSnapshot(): MonitoringSessionSnapshot = MonitoringSessionSnapshot(
    cycleDate = LocalDate.parse(date),
    status = MonitoringStatus.valueOf(status),
    windowStartAt = Instant.ofEpochMilli(monitorStartTime),
    windowEndAt = Instant.ofEpochMilli(requireNotNull(monitorEndTime)),
    targetBedtimeAt = Instant.ofEpochMilli(requireNotNull(targetBedtimeTime)),
    intervalMinutes = checkIntervalMinutes,
    inactiveThreshold = inactiveThreshold,
    firstInactiveCheckAt = firstInactiveCheckTime?.let(Instant::ofEpochMilli),
    consecutiveInactiveChecks = consecutiveInactiveChecks,
    totalCheckCount = totalCheckCount,
    activeCheckCount = activeCheckCount,
    lastCheckAt = lastCheckTime?.let(Instant::ofEpochMilli),
    nextCheckAt = nextCheckTime?.let(Instant::ofEpochMilli),
    sleepTime = sleepTime?.let(Instant::ofEpochMilli)
)

fun SleepRecord.withMonitoringSnapshot(snapshot: MonitoringSessionSnapshot): SleepRecord = copy(
    monitorEndTime = snapshot.windowEndAt.toEpochMilli(),
    targetBedtimeTime = snapshot.targetBedtimeAt.toEpochMilli(),
    sleepTime = snapshot.sleepTime?.toEpochMilli(),
    firstInactiveCheckTime = snapshot.firstInactiveCheckAt?.toEpochMilli(),
    consecutiveInactiveChecks = snapshot.consecutiveInactiveChecks,
    totalCheckCount = snapshot.totalCheckCount,
    activeCheckCount = snapshot.activeCheckCount,
    lastCheckTime = snapshot.lastCheckAt?.toEpochMilli(),
    nextCheckTime = snapshot.nextCheckAt?.toEpochMilli(),
    checkIntervalMinutes = snapshot.intervalMinutes,
    inactiveThreshold = snapshot.inactiveThreshold,
    status = snapshot.status.name
)
