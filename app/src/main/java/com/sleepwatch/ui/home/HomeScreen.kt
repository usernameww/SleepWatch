package com.sleepwatch.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleepwatch.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val latestRecord by viewModel.latestRecord.collectAsState()
    val serviceEnabled by viewModel.serviceEnabled.collectAsState()
    val monitorStart by viewModel.monitorStartTime.collectAsState()

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeText by remember {
        derivedStateOf { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime)) }
    }
    val secondsText by remember {
        derivedStateOf { SimpleDateFormat("ss", Locale.getDefault()).format(Date(currentTime)) }
    }
    val dateText by remember {
        derivedStateOf {
            val cal = Calendar.getInstance()
            val dayOfWeek = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "周一"
                Calendar.TUESDAY -> "周二"
                Calendar.WEDNESDAY -> "周三"
                Calendar.THURSDAY -> "周四"
                Calendar.FRIDAY -> "周五"
                Calendar.SATURDAY -> "周六"
                Calendar.SUNDAY -> "周日"
                else -> ""
            }
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            "${month}月${day}日 $dayOfWeek"
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Clock
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraLight,
                letterSpacing = (-4).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = secondsText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Light
            ),
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Sleep Score Ring
        val score = latestRecord?.sleepScore
        SleepScoreRing(
            score = score,
            modifier = Modifier.size(180.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (score != null) "昨晚睡眠评分" else "暂无评分",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Status Card
        StatusCard(
            serviceEnabled = serviceEnabled,
            monitorStartHour = monitorStart.first,
            monitorStartMinute = monitorStart.second,
            isSkipped = viewModel.isTonightSkipped(),
            isEmergency = viewModel.isTonightEmergency()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Last Night Summary
        latestRecord?.let { record ->
            LastNightCard(
                sleepTime = record.sleepTime,
                alertCount = record.totalAlertCount,
                hasEmergency = record.hasEmergency
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Toggle
        MonitorToggle(
            enabled = serviceEnabled,
            onToggle = { viewModel.toggleService(it) }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SleepScoreRing(
    score: Float?,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(score) {
        if (score != null) {
            animatedProgress.snapTo(0f)
            animatedProgress.animateTo(
                targetValue = score / 100f,
                animationSpec = tween(
                    durationMillis = 1200,
                    easing = FastOutSlowInEasing
                )
            )
        }
    }

    val ringColor = when {
        score == null -> MaterialTheme.colorScheme.outline
        score >= 80f -> Sage
        score >= 60f -> Amber
        else -> Coral
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val padding = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(padding, padding)

            // Background track
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Progress arc
            if (score != null) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(ringColor.copy(alpha = 0.3f), ringColor, ringColor.copy(alpha = 0.3f))
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress.value * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (score != null) {
                Text(
                    text = String.format("%.0f", score),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Light
                    ),
                    color = ringColor
                )
                Text(
                    text = "分",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Icon(
                    imageVector = Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    serviceEnabled: Boolean,
    monitorStartHour: Int,
    monitorStartMinute: Int,
    isSkipped: Boolean,
    isEmergency: Boolean
) {
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (serviceEnabled) "监测中" else "监测已关闭",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "开始时间 ${String.format("%02d:%02d", monitorStartHour, monitorStartMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (serviceEnabled) Sage else MaterialTheme.colorScheme.outline)
            )
        }

        if (isSkipped) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "今晚已跳过监测",
                style = MaterialTheme.typography.bodySmall,
                color = Amber
            )
        }

        if (isEmergency) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "今晚触发了紧急事项",
                style = MaterialTheme.typography.bodySmall,
                color = Coral
            )
        }
    }
}

@Composable
private fun LastNightCard(
    sleepTime: Long?,
    alertCount: Int,
    hasEmergency: Boolean
) {
    val shape = RoundedCornerShape(24.dp)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), shape)
            .padding(24.dp)
    ) {
        Text(
            text = "昨晚记录",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            sleepTime?.let {
                StatItem(
                    label = "入睡",
                    value = timeFormat.format(Date(it)),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            StatItem(
                label = "提醒",
                value = "$alertCount 次",
                color = if (alertCount > 3) Amber else MaterialTheme.colorScheme.onSurface
            )
            if (hasEmergency) {
                StatItem(
                    label = "状态",
                    value = "紧急",
                    color = Coral
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun MonitorToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(20.dp)

    Button(
        onClick = { onToggle(!enabled) },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.primary,
            contentColor = if (enabled)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
        ),
        border = if (enabled) ButtonDefaults.outlinedButtonBorder else null
    ) {
        Icon(
            imageVector = if (enabled) Icons.Default.NightsStay else Icons.Default.Bedtime,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (enabled) "关闭监测" else "开启监测",
            style = MaterialTheme.typography.labelLarge
        )
    }
}
