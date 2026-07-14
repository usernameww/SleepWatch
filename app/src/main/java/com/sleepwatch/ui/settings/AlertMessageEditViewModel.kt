package com.sleepwatch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.AlertMessage
import com.sleepwatch.domain.repository.AlertMessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertMessageEditViewModel @Inject constructor(
    private val repository: AlertMessageRepository
) : ViewModel() {

    val messages = repository.getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateMessage(message: AlertMessage) {
        viewModelScope.launch { repository.update(message) }
    }

    fun toggleEnabled(message: AlertMessage) {
        viewModelScope.launch {
            repository.update(message.copy(isEnabled = !message.isEnabled))
        }
    }
}
