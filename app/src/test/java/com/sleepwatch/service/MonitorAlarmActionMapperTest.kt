package com.sleepwatch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorAlarmActionMapperTest {
    @Test
    fun `all alarm actions map to the matching service action`() {
        assertEquals(
            MonitorService.ACTION_WINDOW_START,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_WINDOW_START)
        )
        assertEquals(
            MonitorService.ACTION_CHECK,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_CHECK)
        )
        assertEquals(
            MonitorService.ACTION_WINDOW_END,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_WINDOW_END)
        )
        assertEquals(
            MonitorService.ACTION_RECONCILE,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_RECONCILE)
        )
    }

    @Test
    fun `unknown or missing alarm action is ignored`() {
        assertNull(MonitorAlarmActionMapper.toServiceAction("unknown"))
        assertNull(MonitorAlarmActionMapper.toServiceAction(null))
    }
}
