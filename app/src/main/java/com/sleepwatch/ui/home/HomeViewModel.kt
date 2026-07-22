package com.sleepwatch.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetSleepRecordsUseCase
import com.sleepwatch.domain.monitoring.MonitoringStatus
import com.sleepwatch.domain.monitoring.MonitoringSettings
import com.sleepwatch.domain.monitoring.MonitoringWindowResolver
import com.sleepwatch.service.MonitorService
import com.sleepwatch.service.MonitoringPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getSleepRecordsUseCase: GetSleepRecordsUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val permissionChecker: MonitoringPermissionChecker,
    private val clock: Clock,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val latestRecord: StateFlow<SleepRecord?> = getSleepRecordsUseCase.getLatestRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serviceEnabled: StateFlow<Boolean> = settingsDataStore.serviceEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _permissionError = MutableStateFlow<String?>(null)
    val permissionError: StateFlow<String?> = _permissionError.asStateFlow()

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

    fun isCurrentWindowSkipped(): Boolean {
        val now = clock.instant()
        val start = LocalTime.of(monitorStartTime.value.first, monitorStartTime.value.second)
        val end = LocalTime.of(monitorEndTime.value.first, monitorEndTime.value.second)
        val window = MonitoringWindowResolver(clock.zone).resolve(
            ZonedDateTime.ofInstant(now, clock.zone),
            MonitoringSettings(start, end, start, 10, 3)
        )
        return window.isActiveAt(now) &&
            latestRecord.value?.date == window.cycleDate.toString() &&
            latestRecord.value?.status == MonitoringStatus.SKIPPED.name
    }

    fun toggleService(enabled: Boolean) {
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
}
