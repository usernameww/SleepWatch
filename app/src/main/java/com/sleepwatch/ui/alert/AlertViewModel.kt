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

@HiltViewModel
class AlertViewModel @Inject constructor(
    private val getAlertMessagesUseCase: GetAlertMessagesUseCase,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val messages = getAlertMessagesUseCase.getEnabledMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var currentLevel = 0

    fun getNextMessage(): String {
        val allMessages = messages.value
        if (allMessages.isEmpty()) return "该睡觉了！"
        currentLevel = (currentLevel % allMessages.size) + 1
        if (currentLevel > allMessages.size) currentLevel = 1
        return allMessages.getOrNull(currentLevel - 1)?.content ?: "该睡觉了！"
    }

    fun skipTonight() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDataStore.setSkippedDate(today)
        }
    }

    fun markEmergency() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            settingsDataStore.setEmergencyDate(today)
        }
    }
}
