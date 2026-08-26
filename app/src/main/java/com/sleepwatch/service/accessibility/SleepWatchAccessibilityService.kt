package com.sleepwatch.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.service.MonitorAlarmScheduler
import com.sleepwatch.service.MonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@AndroidEntryPoint
class SleepWatchAccessibilityService : AccessibilityService() {
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var alarmScheduler: MonitorAlarmScheduler
    @Inject lateinit var clock: Clock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val recoveryCoordinator by lazy {
        AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { settingsDataStore.serviceEnabled.first() },
            scheduleExactReconcile = alarmScheduler::scheduleReconcile,
            isBatteryUnrestricted = {
                getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(packageName)
            },
            startDirectReconcile = {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, MonitorService::class.java)
                        .setAction(MonitorService.ACTION_RECONCILE)
                )
            },
            nowMillis = clock::millis,
            onFailure = { error ->
                Log.w(TAG, "Accessibility recovery request failed", error)
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        requestRecovery("connected")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        requestRecovery("task_removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun requestRecovery(reason: String) {
        serviceScope.launch {
            val result = recoveryCoordinator.requestRecovery()
            Log.d(TAG, "Accessibility recovery $reason: $result")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SleepWatchAccessibility"
    }
}
