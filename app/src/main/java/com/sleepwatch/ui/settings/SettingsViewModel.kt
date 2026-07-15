package com.sleepwatch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.DefaultData
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.AlertMessageRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val sleepRecordRepository: SleepRecordRepository,
    private val alertMessageRepository: AlertMessageRepository,
    private val achievementRepository: AchievementRepository
) : ViewModel() {

    val monitorStartHour = settingsDataStore.monitorStartHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val monitorStartMinute = settingsDataStore.monitorStartMinute
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
    val skippedDate = settingsDataStore.skippedDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val emergencyDate = settingsDataStore.emergencyDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setMonitorStartTime(hour: Int, minute: Int) {
        viewModelScope.launch { settingsDataStore.setMonitorStartTime(hour, minute) }
    }

    fun setCheckInterval(minutes: Int) {
        viewModelScope.launch { settingsDataStore.setCheckInterval(minutes) }
    }

    fun setScreenOffThreshold(count: Int) {
        viewModelScope.launch { settingsDataStore.setScreenOffThreshold(count) }
    }

    fun setTargetBedtime(hour: Int, minute: Int) {
        viewModelScope.launch { settingsDataStore.setTargetBedtime(hour, minute) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setVibrationEnabled(enabled) }
    }

    fun setServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setServiceEnabled(enabled) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            sleepRecordRepository.deleteAll()
            alertMessageRepository.deleteAll()
            alertMessageRepository.insertAll(DefaultData.alertMessages)
            achievementRepository.deleteAll()
            settingsDataStore.clearSkippedAndEmergency()
        }
    }
}
