package com.sleepwatch.domain.monitoring

object MonitoringTriggerValidator {
    fun matches(expectedTriggerAt: Long?, persistedTriggerAt: Long?): Boolean =
        expectedTriggerAt == null || expectedTriggerAt == persistedTriggerAt
}
