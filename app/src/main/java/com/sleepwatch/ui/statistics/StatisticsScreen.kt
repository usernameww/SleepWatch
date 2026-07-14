package com.sleepwatch.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleepwatch.data.db.entity.SleepRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel = hiltViewModel()) {
    val period by viewModel.period.collectAsState()
    val weekRecords by viewModel.weekRecords.collectAsState()
    val monthRecords by viewModel.monthRecords.collectAsState()
    val yearRecords by viewModel.yearRecords.collectAsState()
    val targetHour by viewModel.targetBedtimeHour.collectAsState()
    val targetMinute by viewModel.targetBedtimeMinute.collectAsState()

    val currentRecords = when (period) {
        StatsPeriod.WEEK -> weekRecords
        StatsPeriod.MONTH -> monthRecords
        StatsPeriod.YEAR -> yearRecords
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("统计") })

        // Period selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { viewModel.setPeriod(p) },
                    label = {
                        Text(when (p) {
                            StatsPeriod.WEEK -> "本周"
                            StatsPeriod.MONTH -> "本月"
                            StatsPeriod.YEAR -> "本年"
                        })
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "平均入睡",
                value = viewModel.averageSleepTime(currentRecords)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "平均评分",
                value = viewModel.averageScore(currentRecords)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "达标天数",
                value = "${viewModel.goalAchievedCount(currentRecords, targetHour, targetMinute)}"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chart area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when (period) {
                        StatsPeriod.WEEK -> "本周入睡时间"
                        StatsPeriod.MONTH -> "本月入睡时间"
                        StatsPeriod.YEAR -> "本年月均入睡时间"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (currentRecords.isEmpty()) {
                    Text(
                        text = "暂无数据",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    when (period) {
                        StatsPeriod.WEEK -> WeekChart(currentRecords, targetHour, targetMinute)
                        StatsPeriod.MONTH -> MonthChart(currentRecords)
                        StatsPeriod.YEAR -> YearChart(currentRecords)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detail list
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "详细记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (currentRecords.isEmpty()) {
                    Text(
                        text = "暂无记录",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    currentRecords.takeLast(10).reversed().forEach { record ->
                        RecordRow(record)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WeekChart(records: List<SleepRecord>, targetHour: Int, targetMinute: Int) {
    val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    val dayFormat = SimpleDateFormat("E", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val chartHeight = 180.dp
    val maxValue = 24 * 60 // minutes in a day

    Column {
        // Simple bar chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            records.takeLast(7).forEach { record ->
                val sleepTime = record.sleepTime
                val minutes = if (sleepTime != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = sleepTime }
                    cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                } else null

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (minutes != null) {
                        val barHeight = (minutes.toFloat() / maxValue * chartHeight.value).dp
                        val isEarly = minutes <= targetHour * 60 + targetMinute
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (isEarly) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dayFormat.format(Date(record.monitorStartTime)),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Text(" 早于目标", style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
            )
            Text(" 晚于目标", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MonthChart(records: List<SleepRecord>) {
    // Calendar heatmap
    val daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val recordMap = records.associateBy {
        SimpleDateFormat("dd", Locale.getDefault()).format(Date(it.monitorStartTime))
    }

    Column {
        Text(
            text = SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Grid
        for (week in 0..5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (day in 1..7) {
                    val dayNum = week * 7 + day
                    if (dayNum in 1..daysInMonth) {
                        val key = String.format("%02d", dayNum)
                        val record = recordMap[key]
                        val score = record?.sleepScore
                        val color = when {
                            score == null -> MaterialTheme.colorScheme.surfaceVariant
                            score >= 80 -> MaterialTheme.colorScheme.primary
                            score >= 60 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color.copy(alpha = if (dayNum <= today) 0.8f else 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$dayNum",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dayNum <= today) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(MaterialTheme.colorScheme.primary, "≥80分")
            Spacer(modifier = Modifier.width(12.dp))
            LegendItem(MaterialTheme.colorScheme.tertiary, "60-79分")
            Spacer(modifier = Modifier.width(12.dp))
            LegendItem(MaterialTheme.colorScheme.error, "<60分")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun YearChart(records: List<SleepRecord>) {
    val monthFormat = SimpleDateFormat("MM", Locale.getDefault())
    val monthRecords = records.groupBy {
        monthFormat.format(Date(it.monitorStartTime))
    }.toSortedMap()

    Column {
        Text(
            text = SimpleDateFormat("yyyy年", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Simple month bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            monthRecords.forEach { (month, recs) ->
                val avgScore = recs.mapNotNull { it.sleepScore }.average()
                val barHeight = if (avgScore.isNaN()) 0.dp else (avgScore / 100 * 140).dp

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${month}月",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: SleepRecord) {
    val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateFormat.format(Date(record.monitorStartTime)),
            style = MaterialTheme.typography.bodyMedium
        )
        record.sleepTime?.let {
            Text(
                text = timeFormat.format(Date(it)),
                style = MaterialTheme.typography.bodyMedium
            )
        } ?: Text("--:--", style = MaterialTheme.typography.bodyMedium)

        record.sleepScore?.let {
            val color = when {
                it >= 80 -> MaterialTheme.colorScheme.primary
                it >= 60 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            }
            Text(
                text = String.format("%.0f分", it),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        } ?: Text("--", style = MaterialTheme.typography.bodyMedium)
    }
}
