package com.sleepwatch.ui.setup

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    fun checkPermissions() {
        val items = mutableListOf<PermissionItem>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(PermissionItem(
                name = "通知权限",
                description = "允许发送睡眠提醒通知",
                isGranted = false, // checked at runtime
                action = { requestNotificationPermission() }
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
        items.add(PermissionItem(
            name = "电池优化白名单",
            description = "防止系统杀死监测服务",
            isGranted = false, // checked via PowerManager
            action = { requestBatteryOptimization() }
        ))

        _permissions.value = items
    }

    fun updatePermissionStatus(index: Int, granted: Boolean) {
        val current = _permissions.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(isGranted = granted)
            _permissions.value = current
        }
    }

    fun allGranted(): Boolean = _permissions.value.all { it.isGranted }

    private val _notificationRequestTrigger = MutableStateFlow(false)
    val notificationRequestTrigger: StateFlow<Boolean> = _notificationRequestTrigger

    fun resetNotificationTrigger() {
        _notificationRequestTrigger.value = false
    }

    private fun requestNotificationPermission() {
        _notificationRequestTrigger.value = true
    }

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
