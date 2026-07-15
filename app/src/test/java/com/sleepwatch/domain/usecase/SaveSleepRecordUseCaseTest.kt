package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.SleepRecord
import org.junit.Assert.*
import org.junit.Test

class SaveSleepRecordUseCaseTest {

    @Test
    fun `score calculation with early sleep`() {
        // Sleep at 22:00, target 23:00 -> no late penalty
        val sleepTime = createTimestamp(22, 0)
        val targetHour = 23
        val targetMinute = 0
        val consecutiveDays = 0

        val score = calculateScore(sleepTime, targetHour, targetMinute, 0, consecutiveDays)
        // base 100, no late penalty, no alert penalty, no bonus = 100
        assertEquals(100f, score)
    }

    @Test
    fun `score calculation with very late sleep`() {
        // Sleep at 23:30, target 23:00 -> 30 min late, 10 alerts -> heavy penalty
        val sleepTime = createTimestamp(23, 30)
        val score = calculateScore(sleepTime, 23, 0, 10, 0)
        // 100 - 60 - 30 = 10
        assertEquals(10f, score)
    }

    @Test
    fun `score calculation with alerts`() {
        // Sleep at 23:30, target 23:00 -> 30 min late (-60), 2 alerts (-6)
        val sleepTime = createTimestamp(23, 30)
        val score = calculateScore(sleepTime, 23, 0, 2, 0)
        // 100 - 60 - 6 = 34
        assertEquals(34f, score)
    }

    @Test
    fun `score calculation with consecutive bonus`() {
        // Early sleep + 10 consecutive days
        val sleepTime = createTimestamp(22, 0)
        val score = calculateScore(sleepTime, 23, 0, 0, 10)
        // 100 + 10 = 110 -> clamped to 100
        assertEquals(100f, score)
    }

    @Test
    fun `score calculation with max consecutive bonus`() {
        val sleepTime = createTimestamp(22, 0)
        val score = calculateScore(sleepTime, 23, 0, 0, 25)
        // 100 + 20 (capped) = 120 -> clamped to 100
        assertEquals(100f, score)
    }

    @Test
    fun `score calculation with mixed penalties and bonus`() {
        // 15 min late (-30), 1 alert (-3), 5 consecutive (+5)
        val sleepTime = createTimestamp(23, 15)
        val score = calculateScore(sleepTime, 23, 0, 1, 5)
        // 100 - 30 - 3 + 5 = 72
        assertEquals(72f, score)
    }

    @Test
    fun `score is clamped between 0 and 100`() {
        // Very late + many alerts
        val sleepTime = createTimestamp(5, 0)
        val score = calculateScore(sleepTime, 23, 0, 10, 0)
        assertTrue(score in 0f..100f)

        // Very early + many consecutive
        val earlySleep = createTimestamp(21, 0)
        val highScore = calculateScore(earlySleep, 23, 0, 0, 30)
        assertTrue(highScore in 0f..100f)
    }

    private fun createTimestamp(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun calculateScore(
        sleepTime: Long,
        targetHour: Int,
        targetMinute: Int,
        alertCount: Int,
        consecutiveDays: Int
    ): Float {
        val baseScore = 100f
        val targetCal = java.util.Calendar.getInstance().apply {
            timeInMillis = sleepTime
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, targetMinute)
            set(java.util.Calendar.SECOND, 0)
        }
        val minutesLate = ((sleepTime - targetCal.timeInMillis) / 60000).toInt().coerceAtLeast(0)
        val latePenalty = minutesLate * 2f
        val alertPenalty = alertCount * 3f
        val consecutiveBonus = (consecutiveDays * 1).coerceAtMost(20).toFloat()
        return (baseScore - latePenalty - alertPenalty + consecutiveBonus).coerceIn(0f, 100f)
    }
}
