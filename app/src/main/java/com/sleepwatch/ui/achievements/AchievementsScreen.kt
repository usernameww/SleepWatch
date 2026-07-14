package com.sleepwatch.ui.achievements

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleepwatch.data.db.entity.Achievement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(viewModel: AchievementsViewModel = hiltViewModel()) {
    val achievements by viewModel.achievements.collectAsState()
    val newlyUnlocked by viewModel.newlyUnlocked.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initializeAchievements()
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(newlyUnlocked) {
        newlyUnlocked.forEach { type ->
            snackbarHostState.showSnackbar(
                message = "🎉 成就解锁：${viewModel.getAchievementTitle(type)}",
                duration = SnackbarDuration.Short
            )
        }
        viewModel.dismissNewlyUnlocked()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("成就") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            val unlocked = achievements.filter { it.unlockedAt != null }
            val locked = achievements.filter { it.unlockedAt == null }

            if (unlocked.isNotEmpty()) {
                item {
                    Text(
                        text = "已解锁 (${unlocked.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(unlocked, key = { it.type }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        title = viewModel.getAchievementTitle(achievement.type),
                        description = viewModel.getAchievementDescription(achievement.type),
                        target = viewModel.getAchievementTarget(achievement.type),
                        unlockTime = viewModel.formatUnlockTime(achievement.unlockedAt),
                        isUnlocked = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (locked.isNotEmpty()) {
                item {
                    Text(
                        text = "未解锁 (${locked.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(locked, key = { it.type }) { achievement ->
                    AchievementCard(
                        achievement = achievement,
                        title = viewModel.getAchievementTitle(achievement.type),
                        description = viewModel.getAchievementDescription(achievement.type),
                        target = viewModel.getAchievementTarget(achievement.type),
                        unlockTime = viewModel.formatUnlockTime(achievement.unlockedAt),
                        isUnlocked = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (achievements.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无成就数据",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    title: String,
    description: String,
    target: Int,
    unlockTime: String,
    isUnlocked: Boolean
) {
    val progress = (achievement.currentProgress.toFloat() / target).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isUnlocked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${achievement.currentProgress} / $target",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else {
                    Text(
                        text = unlockTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
