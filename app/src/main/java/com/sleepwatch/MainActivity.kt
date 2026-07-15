package com.sleepwatch

import android.content.Intent
import android.os.Bundle
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
            val enabled = settingsDataStore.serviceEnabled.first()
            if (enabled) {
                val intent = Intent(this@MainActivity, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START
                }
                startService(intent)
            }
        }

        setContent {
            SleepWatchTheme {
                NavGraph()
            }
        }
    }
}
