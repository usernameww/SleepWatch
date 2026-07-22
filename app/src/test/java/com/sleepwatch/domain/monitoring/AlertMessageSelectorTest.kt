package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.AlertMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertMessageSelectorTest {
    @Test
    fun `selects progressive message from persisted alert count`() {
        val messages = (1..3).map { level ->
            AlertMessage(level = level, title = "title-$level", content = "content-$level", healthTip = "")
        }

        assertEquals(1, AlertMessageSelector.select(messages, 0)?.level)
        assertEquals(2, AlertMessageSelector.select(messages, 1)?.level)
        assertEquals(1, AlertMessageSelector.select(messages, 3)?.level)
    }

    @Test
    fun `empty messages return null`() {
        assertEquals(null, AlertMessageSelector.select(emptyList(), 4))
    }
}
