package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class CheckAchievementsUseCase @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val sleepRecordRepository: SleepRecordRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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
        for (type in types) {
            if (achievementRepository.getByType(type) == null) {
                achievementRepository.insert(Achievement(type = type))
            }
        }
    }

    suspend fun checkAll(targetHour: Int, targetMinute: Int): List<String> {
        val newlyUnlocked = mutableListOf<String>()
        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
        }
        val targetTimestamp = targetCal.timeInMillis

        // FIRST_EARLY_SLEEP
        checkAndUnlock(FIRST_EARLY_SLEEP) {
            val count = sleepRecordRepository.countEarlySleeps(targetTimestamp, "2000-01-01", "2099-12-31")
            count > 0
        }?.let { newlyUnlocked.add(it) }

        // STREAK_WEEK
        checkAndUnlock(STREAK_WEEK) { checkConsecutiveDays(7, targetTimestamp, STREAK_WEEK) }?.let { newlyUnlocked.add(it) }

        // STREAK_MONTH
        checkAndUnlock(STREAK_MONTH) { checkConsecutiveDays(30, targetTimestamp, STREAK_MONTH) }?.let { newlyUnlocked.add(it) }

        // STREAK_90
        checkAndUnlock(STREAK_90) { checkConsecutiveDays(90, targetTimestamp, STREAK_90) }?.let { newlyUnlocked.add(it) }

        // LOW_ALERT_WEEK
        checkAndUnlock(LOW_ALERT_WEEK) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
            val startDate = dateFormat.format(cal.time)
            val endDate = dateFormat.format(Date())
            val records = mutableListOf<com.sleepwatch.data.db.entity.SleepRecord>()
            sleepRecordRepository.getRecordsBetween(startDate, endDate).collect { records.addAll(it) }
            val totalAlerts = records.sumOf { it.totalAlertCount }
            totalAlerts in 0..5 && records.isNotEmpty()
        }?.let { newlyUnlocked.add(it) }

        // PERFECT_SCORE
        checkAndUnlock(PERFECT_SCORE) {
            val today = dateFormat.format(Date())
            val record = sleepRecordRepository.getByDate(today)
            record?.sleepScore == 100f
        }?.let { newlyUnlocked.add(it) }

        // EARLY_CHAMPION
        checkAndUnlock(EARLY_CHAMPION) {
            checkConsecutiveScoreDays(7, 90f, targetTimestamp)
        }?.let { newlyUnlocked.add(it) }

        return newlyUnlocked
    }

    private suspend fun checkAndUnlock(type: String, condition: suspend () -> Boolean): String? {
        val achievement = achievementRepository.getByType(type) ?: return null
        if (achievement.unlockedAt != null) return null
        if (condition()) {
            achievementRepository.unlock(type, System.currentTimeMillis())
            return type
        }
        return null
    }

    private suspend fun checkConsecutiveDays(days: Int, targetTimestamp: Long, type: String): Boolean {
        val endDate = dateFormat.format(Date())
        val startDate = dateFormat.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.time)

        // 一次性获取所有记录，避免多次数据库查询
        val records = sleepRecordRepository.getRecordsBetween(startDate, endDate)
            .first()
            .associateBy { it.date }

        val cal = Calendar.getInstance()
        var consecutiveCount = 0
        for (i in 0 until days) {
            val date = dateFormat.format(cal.time)
            val record = records[date]
            if (record?.sleepTime != null && record.sleepTime <= targetTimestamp) {
                consecutiveCount++
            } else {
                break // 遇到中断立即退出
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        achievementRepository.updateProgress(type, consecutiveCount)
        return consecutiveCount >= days
    }

    private suspend fun checkConsecutiveScoreDays(days: Int, minScore: Float, targetTimestamp: Long): Boolean {
        val endDate = dateFormat.format(Date())
        val startDate = dateFormat.format(Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }.time)

        // 一次性获取所有记录，避免多次数据库查询
        val records = sleepRecordRepository.getRecordsBetween(startDate, endDate)
            .first()
            .associateBy { it.date }

        val cal = Calendar.getInstance()
        var consecutiveCount = 0
        for (i in 0 until days) {
            val date = dateFormat.format(cal.time)
            val record = records[date]
            if (record?.sleepScore != null && record.sleepScore >= minScore
                && record.sleepTime != null && record.sleepTime <= targetTimestamp) {
                consecutiveCount++
            } else {
                break // 遇到中断立即退出
            }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        achievementRepository.updateProgress(EARLY_CHAMPION, consecutiveCount)
        return consecutiveCount >= days
    }
}
