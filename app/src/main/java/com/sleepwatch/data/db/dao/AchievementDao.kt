package com.sleepwatch.data.db.dao

import androidx.room.*
import com.sleepwatch.data.db.entity.Achievement
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(achievement: Achievement)

    @Update
    suspend fun update(achievement: Achievement)

    @Query("SELECT * FROM achievements WHERE type = :type LIMIT 1")
    suspend fun getByType(type: String): Achievement?

    @Query("SELECT * FROM achievements WHERE type = :type LIMIT 1")
    fun getByTypeFlow(type: String): Flow<Achievement?>

    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun getAllAchievements(): Flow<List<Achievement>>

    @Query("UPDATE achievements SET currentProgress = :progress WHERE type = :type")
    suspend fun updateProgress(type: String, progress: Int)

    @Query("UPDATE achievements SET unlockedAt = :unlockedAt WHERE type = :type")
    suspend fun unlock(type: String, unlockedAt: Long)

    @Query("DELETE FROM achievements")
    suspend fun deleteAll()
}
