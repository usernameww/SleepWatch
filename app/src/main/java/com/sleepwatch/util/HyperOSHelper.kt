package com.sleepwatch.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object HyperOSHelper {

    fun isHyperOS(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.miui.ui.version.name") as? String
            version != null && version.isNotEmpty()
        } catch (_: Exception) {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        }
    }

    fun openAutoStartSettings(context: Context) {
        try {
            // MIUI/HyperOS auto-start settings
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app details settings
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("package_name", context.packageName)
                putExtra("package_label", "SleepWatch")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun getAutoStartGuideText(): String {
        return if (isHyperOS()) {
            "请在 设置 → 应用管理 → SleepWatch → 自启动 中开启自启动权限"
        } else {
            "请在系统设置中允许 SleepWatch 自启动"
        }
    }

    fun getBackgroundGuideText(): String {
        return if (isHyperOS()) {
            "请在最近任务列表中下拉锁定 SleepWatch，并将省电策略设为\"无限制\""
        } else {
            "请在系统设置中关闭 SleepWatch 的电池优化"
        }
    }
}
