package com.sleepwatch.ui.setup

data class PermissionItem(
    val name: String,
    val description: String,
    val isRequired: Boolean,
    val isGranted: Boolean,
    val grantedStatus: String = "已授权",
    val missingStatus: String = if (isRequired) "未授权（必需）" else "未设置（推荐）",
    val action: () -> Unit,
    val actionLabel: String = "授权"
)

internal fun accessibilityPermissionItem(
    isEnabled: Boolean,
    onRequest: () -> Unit
): PermissionItem = PermissionItem(
    name = "后台增强（无障碍服务）",
    description = "用于在最近任务中划掉 SleepWatch 后恢复睡眠监测",
    isRequired = false,
    isGranted = isEnabled,
    grantedStatus = "已开启",
    missingStatus = "未开启（可选）",
    action = onRequest,
    actionLabel = "开启"
)
