package com.sleepwatch.service

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sleepwatch.ui.alert.AlertInfo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertOverlayControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var controller: AlertOverlayController? = null

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            controller?.hide()
            controller = null
        }
    }

    @Test
    fun show_attachesComposeOverlayWithoutMissingSavedStateOwnerCrash() {
        assumeTrue(Settings.canDrawOverlays(context))

        instrumentation.runOnMainSync {
            controller = AlertOverlayController(context)
            assertTrue(
                controller!!.show(
                    info = AlertInfo(
                        title = "测试提醒",
                        content = "测试悬浮窗生命周期",
                        healthTip = "",
                        level = 1,
                        totalLevels = 1
                    ),
                    onDismiss = {},
                    onSkip = {}
                )
            )
        }
        instrumentation.waitForIdleSync()
    }
}
