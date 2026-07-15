package com.sleepwatch.ui.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetSleepRecordsUseCase
import com.sleepwatch.domain.usecase.SaveSleepRecordUseCase
import com.sleepwatch.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSleepRecordsUseCase: GetSleepRecordsUseCase,
    private val saveSleepRecordUseCase: SaveSleepRecordUseCase,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val latestRecord: StateFlow<SleepRecord?> = getSleepRecordsUseCase.getLatestRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serviceEnabled: StateFlow<Boolean> = settingsDataStore.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val skippedDate: StateFlow<String?> = settingsDataStore.skippedDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emergencyDate: StateFlow<String?> = settingsDataStore.emergencyDate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val monitorStartTime: StateFlow<Pair<Int, Int>> = combine(
        settingsDataStore.monitorStartHour,
        settingsDataStore.monitorStartMinute
    ) { hour, minute -> Pair(hour, minute) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    val monitorEndTime: StateFlow<Pair<Int, Int>> = combine(
        settingsDataStore.monitorEndHour,
        settingsDataStore.monitorEndMinute
    ) { hour, minute -> Pair(hour, minute) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(5, 0))

    fun isTonightSkipped(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return skippedDate.value == today
    }

    fun isTonightEmergency(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return emergencyDate.value == today
    }

    fun toggleService(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setServiceEnabled(enabled)
            if (enabled) {
                val intent = Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START
                }
                context.startService(intent)
            } else {
                val intent = Intent(context, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_STOP
                }
                context.startService(intent)
            }
        }
    }
}
