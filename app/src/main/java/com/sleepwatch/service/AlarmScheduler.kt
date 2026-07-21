package com.sleepwatch.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleCheck(hour: Int, minute: Int, intervalMinutes: Int) {
        val intent = Intent(context, MonitorCheckReceiver::class.java).apply {
            putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        scheduleAlarm(calendar.timeInMillis, pendingIntent)
    }

    private fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelCheck() {
        val intent = Intent(context, MonitorCheckReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
    }
}

class MonitorCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Re-schedule the next alarm
        val intervalMinutes = intent?.getIntExtra(AlarmScheduler.EXTRA_INTERVAL_MINUTES, 0) ?: 0
        if (intervalMinutes > 0) {
            val nextTriggerMillis = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L)
            val rescheduleIntent = Intent(context, MonitorCheckReceiver::class.java).apply {
                putExtra(AlarmScheduler.EXTRA_INTERVAL_MINUTES, intervalMinutes)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, rescheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTriggerMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMillis,
                    pendingIntent
                )
            }
        }

        // Trigger the monitor service
        val serviceIntent = Intent(context, MonitorService::class.java).apply {
            action = MonitorService.ACTION_SCREEN_ON
        }
        try {
            context.startService(serviceIntent)
        } catch (_: Exception) {
            // Service may not be running
        }
    }
}
