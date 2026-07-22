package com.sleepwatch.service

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.sleepwatch.domain.monitoring.MonitoringPermission
import com.sleepwatch.domain.monitoring.MonitoringPermissionHealth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoringPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun current(): MonitoringPermissionHealth {
        val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val exactAlarmsGranted = Build.VERSION.SDK_INT < 31 ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return MonitoringPermissionHealth(
            notificationsGranted = notificationsGranted,
            overlayGranted = Settings.canDrawOverlays(context),
            exactAlarmsGranted = exactAlarmsGranted,
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    fun missingRequiredMessage(): String? {
        val names = current().missingRequired.map { permission ->
            when (permission) {
                MonitoringPermission.NOTIFICATIONS -> "通知"
                MonitoringPermission.OVERLAY -> "悬浮窗"
                MonitoringPermission.EXACT_ALARMS -> "精确闹钟"
                MonitoringPermission.BATTERY_UNRESTRICTED -> "电池无限制"
            }
        }
        return names.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "请先授予", postfix = "权限", separator = "、")
    }
}
