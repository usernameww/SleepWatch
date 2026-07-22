package com.sleepwatch.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepwatch.data.db.entity.Achievement
import com.sleepwatch.domain.repository.AchievementRepository
import com.sleepwatch.domain.usecase.CheckAchievementsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val achievementRepository: AchievementRepository,
    private val checkAchievementsUseCase: CheckAchievementsUseCase
) : ViewModel() {

    val achievements = achievementRepository.getAllAchievements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _newlyUnlocked = MutableStateFlow<List<String>>(emptyList())
    val newlyUnlocked: StateFlow<List<String>> = _newlyUnlocked

    fun initializeAchievements() {
        viewModelScope.launch {
            checkAchievementsUseCase.initializeAchievements()
        }
    }

    fun checkAchievements() {
        viewModelScope.launch {
            val unlocked = checkAchievementsUseCase.checkAll()
            if (unlocked.isNotEmpty()) {
                _newlyUnlocked.value = unlocked
            }
        }
    }

    fun dismissNewlyUnlocked() {
        _newlyUnlocked.value = emptyList()
    }

    fun getAchievementTitle(type: String): String = when (type) {
        CheckAchievementsUseCase.FIRST_EARLY_SLEEP -> "初次早睡"
        CheckAchievementsUseCase.STREAK_WEEK -> "坚持一周"
        CheckAchievementsUseCase.STREAK_MONTH -> "坚持一月"
        CheckAchievementsUseCase.STREAK_90 -> "自律达人"
        CheckAchievementsUseCase.LOW_ALERT_WEEK -> "低提醒周"
        CheckAchievementsUseCase.PERFECT_SCORE -> "完美评分"
        CheckAchievementsUseCase.EARLY_CHAMPION -> "早睡冠军"
        else -> type
    }

    fun getAchievementDescription(type: String): String = when (type) {
        CheckAchievementsUseCase.FIRST_EARLY_SLEEP -> "第一次在目标时间前入睡"
        CheckAchievementsUseCase.STREAK_WEEK -> "连续7天在目标时间前入睡"
        CheckAchievementsUseCase.STREAK_MONTH -> "连续30天在目标时间前入睡"
        CheckAchievementsUseCase.STREAK_90 -> "连续90天在目标时间前入睡"
        CheckAchievementsUseCase.LOW_ALERT_WEEK -> "一周内总提醒次数 ≤ 5"
        CheckAchievementsUseCase.PERFECT_SCORE -> "单日睡眠评分达到 100"
        CheckAchievementsUseCase.EARLY_CHAMPION -> "连续7天评分 ≥ 90"
        else -> ""
    }

    fun getAchievementTarget(type: String): Int = when (type) {
        CheckAchievementsUseCase.FIRST_EARLY_SLEEP -> 1
        CheckAchievementsUseCase.STREAK_WEEK -> 7
        CheckAchievementsUseCase.STREAK_MONTH -> 30
        CheckAchievementsUseCase.STREAK_90 -> 90
        CheckAchievementsUseCase.LOW_ALERT_WEEK -> 1
        CheckAchievementsUseCase.PERFECT_SCORE -> 1
        CheckAchievementsUseCase.EARLY_CHAMPION -> 7
        else -> 1
    }

    fun formatUnlockTime(unlockedAt: Long?): String {
        if (unlockedAt == null) return "未解锁"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(unlockedAt))
    }
}
