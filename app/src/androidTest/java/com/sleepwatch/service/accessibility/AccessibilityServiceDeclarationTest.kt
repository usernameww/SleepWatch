package com.sleepwatch.service.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityServiceDeclarationTest {
    @Test
    fun accessibilityServiceIsSystemBoundAndSurvivesTaskRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, SleepWatchAccessibilityService::class.java),
            PackageManager.GET_META_DATA
        )

        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo.permission)
        assertTrue(serviceInfo.exported)
        assertEquals(0, serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
    }
}
