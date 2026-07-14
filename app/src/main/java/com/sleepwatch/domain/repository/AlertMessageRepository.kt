package com.sleepwatch.domain.repository

import com.sleepwatch.data.db.entity.AlertMessage
import kotlinx.coroutines.flow.Flow

interface AlertMessageRepository {
    suspend fun insert(message: AlertMessage): Long
    suspend fun insertAll(messages: List<AlertMessage>)
    suspend fun update(message: AlertMessage)
    fun getEnabledMessages(): Flow<List<AlertMessage>>
    fun getAllMessages(): Flow<List<AlertMessage>>
    suspend fun getByLevel(level: Int): AlertMessage?
    suspend fun deleteAll()
    suspend fun deleteById(id: Long)
}
