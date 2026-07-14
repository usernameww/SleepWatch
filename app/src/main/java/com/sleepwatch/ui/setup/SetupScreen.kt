package com.sleepwatch.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val permissions by viewModel.permissions.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("权限引导") })
        },
        bottomBar = {
            Button(
                onClick = onComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                enabled = viewModel.allGranted()
            ) {
                Text("完成", style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "为了正常运行，SleepWatch 需要以下权限：",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            itemsIndexed(permissions) { index, permission ->
                PermissionCard(
                    permission = permission,
                    onRequest = {
                        permission.action()
                        // Re-check after returning
                        viewModel.checkPermissions()
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permission: PermissionItem,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.isGranted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (permission.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (permission.isGranted)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (permission.isGranted) "已授权" else "未授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (permission.isGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (!permission.isGranted) {
                Button(onClick = onRequest) {
                    Text("授权")
                }
            }
        }
    }
}
