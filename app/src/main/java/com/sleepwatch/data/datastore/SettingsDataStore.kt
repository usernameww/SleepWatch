package com.sleepwatch.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MONITOR_START_HOUR = intPreferencesKey("monitor_start_hour")
        val MONITOR_START_MINUTE = intPreferencesKey("monitor_start_minute")
        val MONITOR_END_HOUR = intPreferencesKey("monitor_end_hour")
        val MONITOR_END_MINUTE = intPreferencesKey("monitor_end_minute")
        val CHECK_INTERVAL_MINUTES = intPreferencesKey("check_interval_minutes")
        val SCREEN_OFF_THRESHOLD = intPreferencesKey("screen_off_threshold")
        val TARGET_BEDTIME_HOUR = intPreferencesKey("target_bedtime_hour")
        val TARGET_BEDTIME_MINUTE = intPreferencesKey("target_bedtime_minute")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
    }

    val monitorStartHour: Flow<Int> = context.dataStore.data.map { it[Keys.MONITOR_START_HOUR] ?: 0 }
    val monitorStartMinute: Flow<Int> = context.dataStore.data.map { it[Keys.MONITOR_START_MINUTE] ?: 0 }
    val monitorEndHour: Flow<Int> = context.dataStore.data.map { it[Keys.MONITOR_END_HOUR] ?: 5 }
    val monitorEndMinute: Flow<Int> = context.dataStore.data.map { it[Keys.MONITOR_END_MINUTE] ?: 0 }
    val checkIntervalMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.CHECK_INTERVAL_MINUTES] ?: 10 }
    val screenOffThreshold: Flow<Int> = context.dataStore.data.map { it[Keys.SCREEN_OFF_THRESHOLD] ?: 3 }
    val targetBedtimeHour: Flow<Int> = context.dataStore.data.map { it[Keys.TARGET_BEDTIME_HOUR] ?: 23 }
    val targetBedtimeMinute: Flow<Int> = context.dataStore.data.map { it[Keys.TARGET_BEDTIME_MINUTE] ?: 0 }
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SOUND_ENABLED] ?: true }
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.VIBRATION_ENABLED] ?: true }
    val serviceEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SERVICE_ENABLED] ?: false }

    suspend fun setMonitorStartTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.MONITOR_START_HOUR] = hour
            it[Keys.MONITOR_START_MINUTE] = minute
        }
    }

    suspend fun setMonitorEndTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.MONITOR_END_HOUR] = hour
            it[Keys.MONITOR_END_MINUTE] = minute
        }
    }

    suspend fun setCheckInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.CHECK_INTERVAL_MINUTES] = minutes }
    }

    suspend fun setScreenOffThreshold(count: Int) {
        context.dataStore.edit { it[Keys.SCREEN_OFF_THRESHOLD] = count }
    }

    suspend fun setTargetBedtime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.TARGET_BEDTIME_HOUR] = hour
            it[Keys.TARGET_BEDTIME_MINUTE] = minute
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SERVICE_ENABLED] = enabled }
    }

}
