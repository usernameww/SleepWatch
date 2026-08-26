package com.sleepwatch.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusCheckerTest {
    private val sleepWatch = AccessibilityServiceId(
        packageName = "com.sleepwatch",
        className = "com.sleepwatch.service.accessibility.SleepWatchAccessibilityService"
    )

    @Test
    fun `exact service component is reported enabled`() {
        assertTrue(isExpectedServiceEnabled(sleepWatch, listOf(sleepWatch)))
    }

    @Test
    fun `same class name in a different package is not accepted`() {
        val otherPackage = sleepWatch.copy(packageName = "com.example.other")

        assertFalse(isExpectedServiceEnabled(sleepWatch, listOf(otherPackage)))
    }

    @Test
    fun `empty enabled service list is disabled`() {
        assertFalse(isExpectedServiceEnabled(sleepWatch, emptyList()))
    }
}
