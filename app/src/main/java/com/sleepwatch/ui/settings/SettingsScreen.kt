package com.sleepwatch.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSetup: () -> Unit = {},
    onNavigateToAlertEdit: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val monitorStartHour by viewModel.monitorStartHour.collectAsState()
    val monitorStartMinute by viewModel.monitorStartMinute.collectAsState()
    val checkInterval by viewModel.checkInterval.collectAsState()
    val screenOffThreshold by viewModel.screenOffThreshold.collectAsState()
    val targetHour by viewModel.targetBedtimeHour.collectAsState()
    val targetMinute by viewModel.targetBedtimeMinute.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("设置") })

        SettingsSection(title = "监测服务") {
            SettingsSwitch(
                title = "启用睡眠监测",
                subtitle = if (serviceEnabled) "监测已开启" else "监测已关闭",
                checked = serviceEnabled,
                onCheckedChange = { viewModel.setServiceEnabled(it) }
            )
        }

        SettingsSection(title = "监测参数") {
            SettingsItem(
                title = "监测开始时间",
                subtitle = String.format("%02d:%02d", monitorStartHour, monitorStartMinute),
                icon = Icons.Default.Schedule
            )
            SettingsItem(
                title = "检测间隔",
                subtitle = "${checkInterval} 分钟",
                icon = Icons.Default.Timer
            )
            SettingsItem(
                title = "连续息屏阈值",
                subtitle = "${screenOffThreshold} 次",
                icon = Icons.Default.ScreenLockPortrait
            )
            SettingsItem(
                title = "目标就寝时间",
                subtitle = String.format("%02d:%02d", targetHour, targetMinute),
                icon = Icons.Default.Bedtime
            )
        }

        SettingsSection(title = "提醒设置") {
            SettingsSwitch(
                title = "提醒音效",
                subtitle = if (soundEnabled) "已开启" else "已关闭",
                checked = soundEnabled,
                onCheckedChange = { viewModel.setSoundEnabled(it) }
            )
            SettingsSwitch(
                title = "振动提醒",
                subtitle = if (vibrationEnabled) "已开启" else "已关闭",
                checked = vibrationEnabled,
                onCheckedChange = { viewModel.setVibrationEnabled(it) }
            )
            SettingsItem(
                title = "编辑提醒消息",
                subtitle = "自定义渐进式提醒文案",
                icon = Icons.Default.Edit,
                onClick = onNavigateToAlertEdit
            )
        }

        SettingsSection(title = "权限与引导") {
            SettingsItem(
                title = "权限引导",
                subtitle = "检查并设置所需权限",
                icon = Icons.Default.Security,
                onClick = onNavigateToSetup
            )
        }

        SettingsSection(title = "数据管理") {
            SettingsItem(
                title = "清除所有数据",
                subtitle = "删除所有睡眠记录、成就和设置",
                icon = Icons.Default.Delete,
                onClick = { showClearDialog = true },
                tint = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除所有数据") },
            text = { Text("此操作将删除所有睡眠记录、成就数据和提醒消息设置，且无法恢复。确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDialog = false
                    }
                ) {
                    Text("确定清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = { Text(title, color = tint) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
