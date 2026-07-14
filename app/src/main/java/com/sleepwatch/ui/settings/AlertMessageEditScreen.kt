package com.sleepwatch.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sleepwatch.data.db.entity.AlertMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertMessageEditScreen(
    onBack: () -> Unit,
    viewModel: AlertMessageEditViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑提醒消息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                AlertMessageCard(
                    message = message,
                    onToggle = { viewModel.toggleEnabled(message) },
                    onUpdate = { viewModel.updateMessage(it) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AlertMessageCard(
    message: AlertMessage,
    onToggle: () -> Unit,
    onUpdate: (AlertMessage) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var editedTitle by remember(message) { mutableStateOf(message.title) }
    var editedContent by remember(message) { mutableStateOf(message.content) }
    var editedTip by remember(message) { mutableStateOf(message.healthTip) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "第 ${message.level} 级：${message.title}",
                    style = MaterialTheme.typography.titleMedium
                )
                Switch(
                    checked = message.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    label = { Text("提醒内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedTip,
                    onValueChange = { editedTip = it },
                    label = { Text("健康知识") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onUpdate(message.copy(
                            title = editedTitle,
                            content = editedContent,
                            healthTip = editedTip
                        ))
                        isExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("编辑")
                }
            }
        }
    }
}
