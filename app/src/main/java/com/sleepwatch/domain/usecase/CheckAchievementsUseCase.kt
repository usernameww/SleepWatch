package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.repository.SleepRecordRepository
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

        // FIRST_EARLY_SLEEP
        val firstEarly = achievementRepository.getByType(FIRST_EARLY_SLEEP)
        if (firstEarly != null && firstEarly.unlockedAt == null) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
            }
            val count = sleepRecordRepository.countEarlySleeps(
                cal.timeInMillis, "2000-01-01", "2099-12-31"
            )
            if (count > 0) {
                achievementRepository.unlock(FIRST_EARLY_SLEEP, System.currentTimeMillis())
                newlyUnlocked.add(FIRST_EARLY_SLEEP)
            }
        }

        // STREAK_WEEK
        newlyUnlocked += checkStreak(STREAK_WEEK, 7, targetHour, targetMinute)

        // STREAK_MONTH
        newlyUnlocked += checkStreak(STREAK_MONTH, 30, targetHour, targetMinute)

        // STREAK_90
        newlyUnlocked += checkStreak(STREAK_90, 90, targetHour, targetMinute)

        // PERFECT_SCORE
        val perfect = achievementRepository.getByType(PERFECT_SCORE)
        if (perfect != null && perfect.unlockedAt == null) {
            val cal = Calendar.getInstance()
            val today = dateFormat.format(cal.time)
            val record = sleepRecordRepository.getByDate(today)
            if (record?.sleepScore == 100f) {
                achievementRepository.unlock(PERFECT_SCORE, System.currentTimeMillis())
                newlyUnlocked.add(PERFECT_SCORE)
            }
        }

        return newlyUnlocked
    }

    private suspend fun checkStreak(type: String, days: Int, targetHour: Int, targetMinute: Int): String? {
        val achievement = achievementRepository.getByType(type) ?: return null
        if (achievement.unlockedAt != null) return null

        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }
        val startDate = dateFormat.format(cal.time)
        val endDate = dateFormat.format(Date())

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
        }

        val count = sleepRecordRepository.countEarlySleeps(
            targetCal.timeInMillis, startDate, endDate
        )
        achievementRepository.updateProgress(type, count)
        if (count >= days) {
            achievementRepository.unlock(type, System.currentTimeMillis())
            return type
        }
        return null
    }
}
