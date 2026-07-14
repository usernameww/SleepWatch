package com.sleepwatch.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val latestRecord by viewModel.latestRecord.collectAsState()
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val monitorStart by viewModel.monitorStartTime.collectAsState()

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = timeFormat.format(Date(currentTime)),
            fontSize = 56.sp,
            fontWeight = FontWeight.Light
        )

        Text(
            text = dateFormat.format(Date(currentTime)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (serviceEnabled) "监测中" else "监测未启动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "监测开始时间：${String.format("%02d:%02d", monitorStart.first, monitorStart.second)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (viewModel.isTonightSkipped()) {
                    Text(
                        text = "今晚已跳过监测",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (viewModel.isTonightEmergency()) {
                    Text(
                        text = "今晚触发了紧急事项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        latestRecord?.let { record ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "昨晚记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    record.sleepTime?.let { sleepTime ->
                        Text("入睡时间：${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sleepTime))}")
                    }
                    record.sleepScore?.let { score ->
                        Text("睡眠评分：${String.format("%.0f", score)}")
                    }
                    Text("提醒次数：${record.totalAlertCount}")
                    if (record.hasEmergency) {
                        Text("触发紧急事项", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } ?: run {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "暂无睡眠记录",
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Switch(
            checked = serviceEnabled,
            onCheckedChange = { viewModel.toggleService(it) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = if (serviceEnabled) "点击关闭监测" else "点击开启监测",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
