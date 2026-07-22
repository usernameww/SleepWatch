package com.sleepwatch.domain.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringTriggerValidatorTest {
    @Test
    fun `stale alarm is rejected after schedule changes`() {
        assertFalse(MonitoringTriggerValidator.matches(expectedTriggerAt = 1000, persistedTriggerAt = 2000))
    }

    @Test
    fun `current and manually requested triggers are accepted`() {
        assertTrue(MonitoringTriggerValidator.matches(expectedTriggerAt = 2000, persistedTriggerAt = 2000))
        assertTrue(MonitoringTriggerValidator.matches(expectedTriggerAt = null, persistedTriggerAt = 2000))
    }
}
