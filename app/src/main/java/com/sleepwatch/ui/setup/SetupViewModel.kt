package com.sleepwatch.ui.setup

import android.Manifest
import android.app.AlarmManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions

    private val _notificationRequestTrigger = MutableStateFlow(false)
    val notificationRequestTrigger: StateFlow<Boolean> = _notificationRequestTrigger

    fun checkPermissions() {
        val items = mutableListOf<PermissionItem>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            items.add(PermissionItem(
                name = "通知权限",
                description = "允许发送睡眠提醒通知",
                isGranted = granted,
                action = { _notificationRequestTrigger.value = true }
            ))
        }

        // Overlay permission
        items.add(PermissionItem(
            name = "悬浮窗权限",
            description = "显示全屏睡眠提醒弹窗",
            isGranted = Settings.canDrawOverlays(context),
            action = { requestOverlayPermission() }
        ))

        // Exact alarm (Android 12+)
        if (Build.VERSION.SDK_INT >= 31) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            items.add(PermissionItem(
                name = "精确闹钟权限",
                description = "精确唤醒睡眠检测",
                isGranted = alarmManager.canScheduleExactAlarms(),
                action = { requestExactAlarmPermission() }
            ))
        }

        // Battery optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        items.add(PermissionItem(
            name = "电池优化白名单",
            description = "防止系统杀死监测服务",
            isGranted = batteryIgnored,
            action = { requestBatteryOptimization() }
        ))

        _permissions.value = items
    }

    fun resetNotificationTrigger() {
        _notificationRequestTrigger.value = false
    }

    fun allGranted(): Boolean = _permissions.value.all { it.isGranted }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun requestBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

data class PermissionItem(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val action: () -> Unit
)
