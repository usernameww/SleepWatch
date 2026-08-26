package com.sleepwatch.service.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class AccessibilityServiceId(
    val packageName: String,
    val className: String
)

internal fun isExpectedServiceEnabled(
    expected: AccessibilityServiceId,
    enabledServices: List<AccessibilityServiceId>
): Boolean = expected in enabledServices

@Singleton
class AccessibilityServiceStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .map { info ->
                AccessibilityServiceId(
                    packageName = info.resolveInfo.serviceInfo.packageName,
                    className = info.resolveInfo.serviceInfo.name
                )
            }
        val expected = AccessibilityServiceId(
            packageName = context.packageName,
            className = SleepWatchAccessibilityService::class.java.name
        )
        return isExpectedServiceEnabled(expected, enabledServices)
    }
}
