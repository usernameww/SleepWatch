package com.sleepwatch.ui.alert

import androidx.lifecycle.ViewModel
import com.sleepwatch.domain.usecase.GetAlertMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class AlertInfo(
    val title: String,
    val content: String,
    val healthTip: String,
    val level: Int,
    val totalLevels: Int
)

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val getAlertMessagesUseCase: GetAlertMessagesUseCase
) : ViewModel(), AlertViewModelInterface {

    override suspend fun getNextMessage(): AlertInfo {
        val allMessages = getAlertMessagesUseCase.getEnabledMessages().first()
        if (allMessages.isEmpty()) {
            return AlertInfo("该睡觉了", "请放下手机休息", "", 1, 1)
        }
        val msg = allMessages.first()
        return AlertInfo(
            title = msg.title,
            content = msg.content,
            healthTip = msg.healthTip,
            level = msg.level,
            totalLevels = allMessages.size
        )
    }

}
