package com.sleepwatch.ui.alert

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.sleepwatch.ui.theme.SleepWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SleepWatchTheme {
                AlertRoute(
                    onDismiss = { finish() },
                    onSkip = {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, com.sleepwatch.service.MonitorService::class.java)
                                .setAction(com.sleepwatch.service.MonitorService.ACTION_SKIP)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun AlertRoute(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    viewModel: AlertViewModelInterface = hiltViewModel<AlertViewModel>()
) {
    val alertInfo = remember { mutableStateOf<AlertInfo?>(null) }

    LaunchedEffect(Unit) {
        alertInfo.value = viewModel.getNextMessage()
    }

    AlertScreen(
        info = alertInfo.value,
        onDismiss = onDismiss,
        onSkip = onSkip
    )
}

@Composable
fun AlertScreen(
    info: AlertInfo?,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1026),
                        Color(0xFF141B3D),
                        Color(0xFF0B1026)
                    )
                )
            )
    ) {
        // Subtle radial glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(y = (-50).dp)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7EC8A0).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Skip button - TOP LEFT
        TextButton(
            onClick = {
                onSkip()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 48.dp)
        ) {
            Text(
                text = "今晚不再提醒",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8E8E93)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 100.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFF7EC8A0)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Time
            Text(
                text = timeFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-2).sp
                ),
                color = Color(0xFFF0ECE3)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Message title with level indicator
            if (info != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Level dots
                    repeat(info.totalLevels) { index ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .padding(horizontal = 2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < info.level)
                                        Color(0xFF7EC8A0)
                                    else
                                        Color.White.copy(alpha = 0.15f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = info.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFF7EC8A0)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Message content card
                val msgShape = RoundedCornerShape(20.dp)
                Text(
                    text = info.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp
                    ),
                    color = Color(0xFFF0ECE3).copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(msgShape)
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.06f), msgShape)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                )

                // Health tip
                if (info.healthTip.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = info.healthTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Dismiss button - BOTTOM CENTER
            val btnShape = RoundedCornerShape(16.dp)
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = btnShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7EC8A0),
                    contentColor = Color(0xFF0B1026)
                )
            ) {
                Text(
                    "我知道了",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}
