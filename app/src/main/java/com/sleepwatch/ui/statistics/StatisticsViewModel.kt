package com.sleepwatch.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetSleepRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class StatsPeriod { WEEK, MONTH, YEAR }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getSleepRecordsUseCase: GetSleepRecordsUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period

    val targetBedtimeHour = settingsDataStore.targetBedtimeHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 23)
    val targetBedtimeMinute = settingsDataStore.targetBedtimeMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekRecords: StateFlow<List<SleepRecord>> = getRecordsForPeriod(7)
    val monthRecords: StateFlow<List<SleepRecord>> = getRecordsForPeriod(30)

    val yearRecords: StateFlow<List<SleepRecord>> = flow {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = sdf.format(cal.time)
        val endDate = sdf.format(Date())
        emitAll(getSleepRecordsUseCase.getRecordsBetween(startDate, endDate))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeriod(p: StatsPeriod) { _period.value = p }

    fun averageSleepTime(records: List<SleepRecord>): String {
        val sleepTimes = records.mapNotNull { it.sleepTime }
        if (sleepTimes.isEmpty()) return "--:--"
        val avg = sleepTimes.average().toLong()
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(avg))
    }

    fun averageScore(records: List<SleepRecord>): String {
        val scores = records.mapNotNull { it.sleepScore }
        if (scores.isEmpty()) return "--"
        return String.format("%.1f", scores.average())
    }

    fun goalAchievedCount(records: List<SleepRecord>, targetHour: Int, targetMinute: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
        }
        val targetTimestamp = cal.timeInMillis
        return records.count { record ->
            record.sleepTime != null && record.sleepTime <= targetTimestamp
        }
    }

    private fun getRecordsForPeriod(days: Int): StateFlow<List<SleepRecord>> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDate = sdf.format(cal.time)
        val endDate = sdf.format(Date())
        return getSleepRecordsUseCase.getRecordsBetween(startDate, endDate)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}
