package com.sleepwatch.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPermissionModelsTest {
    @Test
    fun `accessibility recovery is optional and explains its disabled state`() {
        val item = accessibilityPermissionItem(isEnabled = false, onRequest = {})

        assertFalse(item.isRequired)
        assertFalse(item.isGranted)
        assertEquals("未开启（可选）", item.missingStatus)
        assertEquals("用于在最近任务中划掉 SleepWatch 后恢复睡眠监测", item.description)
        assertEquals("开启", item.actionLabel)
    }

    @Test
    fun `enabled accessibility card uses enabled wording and forwards request`() {
        var requested = false
        val item = accessibilityPermissionItem(
            isEnabled = true,
            onRequest = { requested = true }
        )

        assertEquals("已开启", item.grantedStatus)
        item.action()
        assertTrue(requested)
    }
}
