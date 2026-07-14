package com.sleepwatch.domain.repository

import com.sleepwatch.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

interface AchievementRepository {
    suspend fun insert(achievement: Achievement)
    suspend fun update(achievement: Achievement)
    suspend fun getByType(type: String): Achievement?
    fun getByTypeFlow(type: String): Flow<Achievement?>
    fun getAllAchievements(): Flow<List<Achievement>>
    suspend fun updateProgress(type: String, progress: Int)
    suspend fun unlock(type: String, unlockedAt: Long)
    suspend fun deleteAll()
}
