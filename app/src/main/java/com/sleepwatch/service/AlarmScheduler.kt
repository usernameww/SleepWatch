package com.sleepwatch.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.sleepwatch.domain.monitoring.ScheduledMonitorAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleWindowStart(triggerAtMillis: Long) =
        schedule(ACTION_WINDOW_START, REQUEST_WINDOW_START, triggerAtMillis)

    fun scheduleCheck(triggerAtMillis: Long) =
        schedule(ACTION_CHECK, REQUEST_CHECK, triggerAtMillis)

    fun scheduleWindowEnd(triggerAtMillis: Long) =
        schedule(ACTION_WINDOW_END, REQUEST_WINDOW_END, triggerAtMillis)

    fun reconcile(actions: List<ScheduledMonitorAction>) {
        cancelAll()
        actions.forEach { action ->
            when (action) {
                is ScheduledMonitorAction.WindowStart -> scheduleWindowStart(action.at.toEpochMilli())
                is ScheduledMonitorAction.Check -> scheduleCheck(action.at.toEpochMilli())
                is ScheduledMonitorAction.WindowEnd -> scheduleWindowEnd(action.at.toEpochMilli())
            }
        }
    }

    fun cancelAll() {
        cancel(ACTION_WINDOW_START, REQUEST_WINDOW_START)
        cancel(ACTION_CHECK, REQUEST_CHECK)
        cancel(ACTION_WINDOW_END, REQUEST_WINDOW_END)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun schedule(action: String, requestCode: Int, triggerAtMillis: Long) {
        val pendingIntent = pendingIntent(action, requestCode, triggerAtMillis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancel(action: String, requestCode: Int) {
        val pendingIntent = pendingIntent(action, requestCode)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(
        action: String,
        requestCode: Int,
        triggerAtMillis: Long? = null
    ): PendingIntent {
        val intent = Intent(context, MonitorAlarmReceiver::class.java).setAction(action).apply {
            triggerAtMillis?.let { putExtra(EXTRA_TRIGGER_AT, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_WINDOW_START = "com.sleepwatch.alarm.WINDOW_START"
        const val ACTION_CHECK = "com.sleepwatch.alarm.CHECK"
        const val ACTION_WINDOW_END = "com.sleepwatch.alarm.WINDOW_END"
        const val EXTRA_TRIGGER_AT = "com.sleepwatch.alarm.TRIGGER_AT"

        private const val REQUEST_WINDOW_START = 100
        private const val REQUEST_CHECK = 101
        private const val REQUEST_WINDOW_END = 102
    }
}

class MonitorAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val serviceAction = when (intent?.action) {
            MonitorAlarmScheduler.ACTION_WINDOW_START -> MonitorService.ACTION_WINDOW_START
            MonitorAlarmScheduler.ACTION_CHECK -> MonitorService.ACTION_CHECK
            MonitorAlarmScheduler.ACTION_WINDOW_END -> MonitorService.ACTION_WINDOW_END
            else -> return
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, MonitorService::class.java)
                .setAction(serviceAction)
                .putExtra(
                    MonitorService.EXTRA_TRIGGER_AT,
                    intent?.getLongExtra(MonitorAlarmScheduler.EXTRA_TRIGGER_AT, Long.MIN_VALUE)
                        ?: Long.MIN_VALUE
                )
        )
    }
}
