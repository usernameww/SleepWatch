package com.sleepwatch.service

internal object MonitorAlarmActionMapper {
    fun toServiceAction(alarmAction: String?): String? = when (alarmAction) {
        MonitorAlarmScheduler.ACTION_WINDOW_START -> MonitorService.ACTION_WINDOW_START
        MonitorAlarmScheduler.ACTION_CHECK -> MonitorService.ACTION_CHECK
        MonitorAlarmScheduler.ACTION_WINDOW_END -> MonitorService.ACTION_WINDOW_END
        MonitorAlarmScheduler.ACTION_RECONCILE -> MonitorService.ACTION_RECONCILE
        else -> null
    }
}
