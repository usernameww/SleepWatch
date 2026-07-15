package com.sleepwatch.ui.alert

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
                AlertScreen(onDismiss = { finish() })
            }
        }
    }
}

@Composable
fun AlertScreen(
    onDismiss: () -> Unit,
    viewModel: AlertViewModel = hiltViewModel()
) {
    val message = remember { mutableStateOf("") }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        message.value = viewModel.getNextMessage()
        startTime = System.currentTimeMillis()
    }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val elapsed = (currentTime - startTime) / 60000

    // Pulse animation for the icon
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "该睡觉了",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp
                ),
                color = Color(0xFFF0ECE3)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = timeFormat.format(Date(currentTime)),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-2).sp
                ),
                color = Color(0xFFF0ECE3)
            )

            if (elapsed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已超时 ${elapsed} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E8E93)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Message card
            val msgShape = RoundedCornerShape(20.dp)
            Text(
                text = message.value,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 28.sp
                ),
                color = Color(0xFFF0ECE3).copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(msgShape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.06f), msgShape)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Buttons
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

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    viewModel.skipTonight()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = btnShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF0ECE3).copy(alpha = 0.7f)
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
            ) {
                Text("今晚不再提醒", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
