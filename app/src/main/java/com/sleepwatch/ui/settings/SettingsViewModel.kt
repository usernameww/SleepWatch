package com.sleepwatch.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.DefaultData
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.AlertMessageRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
import com.sleepwatch.service.MonitorService
import com.sleepwatch.service.MonitoringPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val sleepRecordRepository: SleepRecordRepository,
    private val alertMessageRepository: AlertMessageRepository,
    private val achievementRepository: AchievementRepository,
    private val permissionChecker: MonitoringPermissionChecker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val monitorStartHour = settingsDataStore.monitorStartHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val monitorStartMinute = settingsDataStore.monitorStartMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val monitorEndHour = settingsDataStore.monitorEndHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    val monitorEndMinute = settingsDataStore.monitorEndMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val checkInterval = settingsDataStore.checkIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val screenOffThreshold = settingsDataStore.screenOffThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val targetBedtimeHour = settingsDataStore.targetBedtimeHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 23)
    val targetBedtimeMinute = settingsDataStore.targetBedtimeMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val soundEnabled = settingsDataStore.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val vibrationEnabled = settingsDataStore.vibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val serviceEnabled = settingsDataStore.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val _permissionError = MutableStateFlow<String?>(null)
    val permissionError: StateFlow<String?> = _permissionError.asStateFlow()

    fun setMonitorStartTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsDataStore.setMonitorStartTime(hour, minute)
            reconfigureServiceIfEnabled()
        }
    }

    fun setMonitorEndTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsDataStore.setMonitorEndTime(hour, minute)
            reconfigureServiceIfEnabled()
        }
    }

    fun setCheckInterval(minutes: Int) {
        viewModelScope.launch {
            settingsDataStore.setCheckInterval(minutes)
            reconfigureServiceIfEnabled()
        }
    }

    fun setScreenOffThreshold(count: Int) {
        viewModelScope.launch {
            settingsDataStore.setScreenOffThreshold(count)
            reconfigureServiceIfEnabled()
        }
    }

    fun setTargetBedtime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsDataStore.setTargetBedtime(hour, minute)
            reconfigureServiceIfEnabled()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVibrationEnabled(enabled) }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                permissionChecker.missingRequiredMessage()?.let { message ->
                    _permissionError.value = message
                    return@launch
                }
            }
            _permissionError.value = null
            settingsDataStore.setServiceEnabled(enabled)
            val intent = Intent(context, MonitorService::class.java).apply {
                action = if (enabled) MonitorService.ACTION_START else MonitorService.ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            sleepRecordRepository.deleteAll()
            alertMessageRepository.deleteAll()
            alertMessageRepository.insertAll(DefaultData.alertMessages)
            achievementRepository.deleteAll()
        }
    }

    private suspend fun reconfigureServiceIfEnabled() {
        val enabled = settingsDataStore.serviceEnabled.first()
        if (enabled) {
            val intent = Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_RECONFIGURE
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
