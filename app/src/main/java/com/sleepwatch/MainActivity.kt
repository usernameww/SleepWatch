package com.sleepwatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.service.MonitorService
import com.sleepwatch.ui.navigation.NavGraph
import com.sleepwatch.ui.theme.SleepWatchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restore MonitorService if it was enabled
        lifecycleScope.launch {
            try {
                val enabled = settingsDataStore.serviceEnabled.first()
                if (enabled) {
                    Log.d("MainActivity", "Restoring MonitorService...")
                    val intent = Intent(this@MainActivity, MonitorService::class.java).apply {
                        action = MonitorService.ACTION_START
                    }
                    startService(intent)
                    Log.d("MainActivity", "MonitorService started successfully")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore MonitorService", e)
            }
        }

        setContent {
            SleepWatchTheme {
                NavGraph()
            }
        }
    }
}
