package com.sleepwatch.data.db.dao

import androidx.room.*
import com.sleepwatch.data.db.entity.AlertMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AlertMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<AlertMessage>)

    @Update
    suspend fun update(message: AlertMessage)

    @Query("SELECT * FROM alert_messages WHERE isEnabled = 1 ORDER BY level ASC")
    fun getEnabledMessages(): Flow<List<AlertMessage>>

    @Query("SELECT * FROM alert_messages ORDER BY level ASC")
    fun getAllMessages(): Flow<List<AlertMessage>>

    @Query("SELECT * FROM alert_messages WHERE level = :level LIMIT 1")
    suspend fun getByLevel(level: Int): AlertMessage?

    @Query("DELETE FROM alert_messages")
    suspend fun deleteAll()
}
