package com.sleepwatch.data.repository

import com.sleepwatch.data.db.dao.AchievementDao
import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.domain.repository.AchievementRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AchievementRepositoryImpl @Inject constructor(
    private val dao: AchievementDao
) : AchievementRepository {

    override suspend fun insert(achievement: Achievement) = dao.insert(achievement)

    override suspend fun update(achievement: Achievement) = dao.update(achievement)

    override suspend fun getByType(type: String): Achievement? = dao.getByType(type)

    override fun getByTypeFlow(type: String): Flow<Achievement?> = dao.getByTypeFlow(type)

    override fun getAllAchievements(): Flow<List<Achievement>> = dao.getAllAchievements()

    override suspend fun updateProgress(type: String, progress: Int) = dao.updateProgress(type, progress)

    override suspend fun unlock(type: String, unlockedAt: Long) = dao.unlock(type, unlockedAt)

    override suspend fun deleteAll() = dao.deleteAll()
}
