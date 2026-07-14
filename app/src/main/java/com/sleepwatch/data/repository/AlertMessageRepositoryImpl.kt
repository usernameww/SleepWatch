package com.sleepwatch.data.repository

import com.sleepwatch.data.db.dao.AlertMessageDao
import com.sleepwatch.data.db.entity.AlertMessage
import com.sleepwatch.domain.repository.AlertMessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertMessageRepositoryImpl @Inject constructor(
    private val dao: AlertMessageDao
) : AlertMessageRepository {

    override suspend fun insert(message: AlertMessage): Long = dao.insert(message)

    override suspend fun insertAll(messages: List<AlertMessage>) = dao.insertAll(messages)

    override suspend fun update(message: AlertMessage) = dao.update(message)

    override fun getEnabledMessages(): Flow<List<AlertMessage>> = dao.getEnabledMessages()

    override fun getAllMessages(): Flow<List<AlertMessage>> = dao.getAllMessages()

    override suspend fun getByLevel(level: Int): AlertMessage? = dao.getByLevel(level)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}
