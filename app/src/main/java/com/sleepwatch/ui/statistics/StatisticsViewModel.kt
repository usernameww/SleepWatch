package com.sleepwatch.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.monitoring.SleepStatisticsCalculator
import com.sleepwatch.domain.usecase.GetSleepRecordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class StatsPeriod { WEEK, MONTH, YEAR }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getSleepRecordsUseCase: GetSleepRecordsUseCase,
    private val clock: Clock
) : ViewModel() {
    private val calculator = SleepStatisticsCalculator(clock.zone)

    private val _period = MutableStateFlow(StatsPeriod.WEEK)
    val period: StateFlow<StatsPeriod> = _period

    val weekRecords: StateFlow<List<SleepRecord>> = getRecordsForPeriod(7)
    val monthRecords: StateFlow<List<SleepRecord>> = getRecordsForPeriod(30)

    val yearRecords: StateFlow<List<SleepRecord>> = flow {
        val today = LocalDate.now(clock)
        emitAll(getSleepRecordsUseCase.getRecordsBetween(today.minusYears(1).toString(), today.toString()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPeriod(p: StatsPeriod) { _period.value = p }

    fun averageSleepTime(records: List<SleepRecord>): String {
        return calculator.averageSleepTime(records)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
    }

    fun averageScore(records: List<SleepRecord>): String {
        return calculator.averageScore(records)?.let { String.format("%.1f", it) } ?: "--"
    }

    fun goalAchievedCount(records: List<SleepRecord>): Int = calculator.goalAchievedCount(records)

    fun goalAchievementRate(records: List<SleepRecord>): String =
        calculator.goalAchievementRate(records)
            ?.let { String.format("%.0f%%", it * 100) }
            ?: "--"

    private fun getRecordsForPeriod(days: Int): StateFlow<List<SleepRecord>> {
        val today = LocalDate.now(clock)
        return getSleepRecordsUseCase.getRecordsBetween(today.minusDays((days - 1).toLong()).toString(), today.toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }
}
