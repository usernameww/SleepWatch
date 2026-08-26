package com.sleepwatch.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPermissionSubtitleTest {
    @Test
    fun `enabled background enhancement is shown in permission subtitle`() {
        assertEquals(
            "检查所需权限；后台增强已开启",
            permissionGuideSubtitle(accessibilityEnabled = true)
        )
    }

    @Test
    fun `disabled background enhancement is shown in permission subtitle`() {
        assertEquals(
            "检查所需权限；后台增强未开启",
            permissionGuideSubtitle(accessibilityEnabled = false)
        )
    }
}
