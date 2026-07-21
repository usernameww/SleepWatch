package com.sleepwatch.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetAlertMessagesUseCase
import com.sleepwatch.ui.alert.AlertInfo
import com.sleepwatch.ui.alert.AlertViewModelInterface
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple ViewModel for alert overlay in service (without Hilt injection)
 */
class SimpleAlertViewModel(
    private val getAlertMessagesUseCase: GetAlertMessagesUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel(), AlertViewModelInterface {

    val messages = getAlertMessagesUseCase.getEnabledMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentLevel = 0

    override fun getNextMessage(): AlertInfo {
        val allMessages = messages.value
        if (allMessages.isEmpty()) {
            return AlertInfo("该睡觉了", "请放下手机休息", "", 1, 1)
        }
        currentLevel = (currentLevel % allMessages.size)
        val msg = allMessages[currentLevel]
        currentLevel++
        return AlertInfo(
            title = msg.title,
            content = msg.content,
            healthTip = msg.healthTip,
            level = msg.level,
            totalLevels = allMessages.size
        )
    }

    override fun skipTonight() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDataStore.setSkippedDate(today)
        }
    }
}
