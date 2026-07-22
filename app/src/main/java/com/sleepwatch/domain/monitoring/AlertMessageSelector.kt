package com.sleepwatch.domain.monitoring

import com.sleepwatch.data.db.entity.AlertMessage

object AlertMessageSelector {
    fun select(messages: List<AlertMessage>, displayedAlertCount: Int): AlertMessage? {
        if (messages.isEmpty()) return null
        return messages[displayedAlertCount.mod(messages.size)]
    }
}
