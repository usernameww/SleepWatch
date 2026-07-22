package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.monitoring.MonitoringStatus
import com.sleepwatch.domain.monitoring.SleepStatisticsCalculator
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CheckAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val sleepRecordRepository: SleepRecordRepository,
    private val clock: Clock
) {
    private val calculator = SleepStatisticsCalculator(clock.zone)

    companion object {
        const val FIRST_EARLY_SLEEP = "first_early_sleep"
        const val STREAK_WEEK = "streak_week"
        const val STREAK_MONTH = "streak_month"
        const val STREAK_90 = "streak_90"
        const val LOW_ALERT_WEEK = "low_alert_week"
        const val PERFECT_SCORE = "perfect_score"
        const val EARLY_CHAMPION = "early_champion"
    }

    suspend fun initializeAchievements() {
        val types = listOf(
            FIRST_EARLY_SLEEP, STREAK_WEEK, STREAK_MONTH, STREAK_90,
            LOW_ALERT_WEEK, PERFECT_SCORE, EARLY_CHAMPION
        )
        types.forEach { type ->
            if (achievementRepository.getByType(type) == null) {
                achievementRepository.insert(Achievement(type = type))
            }
        }
    }

    suspend fun checkAll(): List<String> {
        initializeAchievements()
        val today = LocalDate.now(clock)
        val allRecords = recordsBetween(LocalDate.of(2000, 1, 1), today)
        val anchorDate = allRecords.maxOfOrNull { LocalDate.parse(it.date) } ?: today
        val recent = allRecords.filter { LocalDate.parse(it.date) >= anchorDate.minusDays(89) }
        val newlyUnlocked = mutableListOf<String>()

        checkAndUnlock(FIRST_EARLY_SLEEP) {
            allRecords.any(calculator::isGoalAchieved)
        }?.let(newlyUnlocked::add)
        checkAndUnlock(STREAK_WEEK) {
            checkConsecutiveDays(recent, anchorDate, 7, STREAK_WEEK) { calculator.isGoalAchieved(it) }
        }?.let(newlyUnlocked::add)
        checkAndUnlock(STREAK_MONTH) {
            checkConsecutiveDays(recent, anchorDate, 30, STREAK_MONTH) { calculator.isGoalAchieved(it) }
        }?.let(newlyUnlocked::add)
        checkAndUnlock(STREAK_90) {
            checkConsecutiveDays(recent, anchorDate, 90, STREAK_90) { calculator.isGoalAchieved(it) }
        }?.let(newlyUnlocked::add)

        val lastWeek = recent.filter { LocalDate.parse(it.date) >= anchorDate.minusDays(6) }
            .filter { it.status == MonitoringStatus.SLEEP_CONFIRMED.name }
        checkAndUnlock(LOW_ALERT_WEEK) {
            lastWeek.isNotEmpty() && lastWeek.sumOf { it.totalAlertCount } <= 5
        }?.let(newlyUnlocked::add)
        checkAndUnlock(PERFECT_SCORE) {
            allRecords.any { it.status == MonitoringStatus.SLEEP_CONFIRMED.name && it.sleepScore == 100f }
        }?.let(newlyUnlocked::add)
        checkAndUnlock(EARLY_CHAMPION) {
            checkConsecutiveDays(recent, anchorDate, 7, EARLY_CHAMPION) {
                calculator.isGoalAchieved(it) && (it.sleepScore ?: 0f) >= 90f
            }
        }?.let(newlyUnlocked::add)

        return newlyUnlocked
    }

    suspend fun consecutiveEarlyDaysThrough(record: SleepRecord): Int {
        if (!calculator.isGoalAchieved(record)) return 0
        val cycleDate = LocalDate.parse(record.date)
        val byDate = recordsBetween(cycleDate.minusDays(19), cycleDate)
            .groupBy { it.date }
            .mapValues { (_, sameDate) ->
                sameDate.firstOrNull(calculator::isGoalAchieved) ?: sameDate.last()
            }
        var count = 0
        for (offset in 0 until 20) {
            val candidate = byDate[cycleDate.minusDays(offset.toLong()).toString()]
            if (candidate != null && calculator.isGoalAchieved(candidate)) count++ else break
        }
        return count
    }

    private suspend fun recordsBetween(start: LocalDate, end: LocalDate): List<SleepRecord> =
        sleepRecordRepository.getRecordsBetween(start.toString(), end.toString()).first()

    private suspend fun checkAndUnlock(type: String, condition: suspend () -> Boolean): String? {
        val achievement = achievementRepository.getByType(type) ?: return null
        if (achievement.unlockedAt != null || !condition()) return null
        achievementRepository.unlock(type, clock.millis())
        return type
    }

    private suspend fun checkConsecutiveDays(
        records: List<SleepRecord>,
        today: LocalDate,
        days: Int,
        type: String,
        matches: (SleepRecord) -> Boolean
    ): Boolean {
        val byDate = records.groupBy { it.date }.mapValues { (_, sameDate) ->
            sameDate.firstOrNull(matches) ?: sameDate.last()
        }
        var consecutive = 0
        for (offset in 0 until days) {
            val record = byDate[today.minusDays(offset.toLong()).toString()]
            if (record != null && matches(record)) consecutive++ else break
        }
        achievementRepository.updateProgress(type, consecutive)
        return consecutive >= days
    }
}
