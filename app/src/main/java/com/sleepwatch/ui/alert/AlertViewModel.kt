package com.sleepwatch.ui.alert

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetAlertMessagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val getAlertMessagesUseCase: GetAlertMessagesUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val messages = getAlertMessagesUseCase.getEnabledMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentLevel = 0

    fun getNextMessage(): AlertInfo {
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

    fun skipTonight() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDataStore.setSkippedDate(today)
        }
    }
}
