package com.sleepwatch.domain.usecase

import com.sleepwatch.data.db.entity.AlertMessage
import com.sleepwatch.domain.repository.AlertMessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAlertMessagesUseCase @Inject constructor(
    private val repository: AlertMessageRepository
) {
    fun getEnabledMessages(): Flow<List<AlertMessage>> = repository.getEnabledMessages()

    fun getAllMessages(): Flow<List<AlertMessage>> = repository.getAllMessages()

    suspend fun getNextMessage(currentLevel: Int): AlertMessage? {
        val nextLevel = currentLevel + 1
        return repository.getByLevel(nextLevel) ?: repository.getByLevel(1)
    }
}
