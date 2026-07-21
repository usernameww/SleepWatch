package com.sleepwatch.service

enum class MonitorState {
    IDLE, MONITORING, ALERTING, SLEEP_DETECTED
}

class MonitorStateMachine {
    @Volatile
    var state: MonitorState = MonitorState.IDLE
        private set

    private var consecutiveScreenOffCount = 0
    private var screenOffThreshold = 3

    @Synchronized
    fun setScreenOffThreshold(threshold: Int) {
        screenOffThreshold = threshold
    }

    suspend fun startMonitoring() {
        if (state == MonitorState.IDLE) {
            state = MonitorState.MONITORING
            consecutiveScreenOffCount = 0
        }
    }

    suspend fun onScreenOn(): MonitorState {
        return when (state) {
            MonitorState.MONITORING -> {
                state = MonitorState.ALERTING
                state
            }
            MonitorState.ALERTING -> state
            MonitorState.SLEEP_DETECTED -> {
                state = MonitorState.MONITORING
                consecutiveScreenOffCount = 0
                state
            }
            MonitorState.IDLE -> state
        }
    }

    suspend fun onScreenOn(
        currentHour: Int,
        currentMinute: Int,
        monitorStartHour: Int,
        monitorStartMinute: Int,
        monitorEndHour: Int,
        monitorEndMinute: Int
    ): MonitorState {
        return when (state) {
            MonitorState.MONITORING -> {
                state = MonitorState.ALERTING
                state
            }
            MonitorState.ALERTING -> state
            MonitorState.SLEEP_DETECTED -> {
                if (isWithinMonitoringWindow(currentHour, currentMinute, monitorStartHour, monitorStartMinute, monitorEndHour, monitorEndMinute)) {
                    state = MonitorState.MONITORING
                    consecutiveScreenOffCount = 0
                }
                state
            }
            MonitorState.IDLE -> state
        }
    }

    @Synchronized
    fun onScreenOff(): MonitorState {
        return when (state) {
            MonitorState.MONITORING -> {
                consecutiveScreenOffCount++
                if (consecutiveScreenOffCount >= screenOffThreshold) {
                    state = MonitorState.SLEEP_DETECTED
                }
                state
            }
            MonitorState.ALERTING -> {
                // User is looking at the alert - screen off is not sleep-related
                // Do not increment counter; stay in ALERTING
                state
            }
            MonitorState.SLEEP_DETECTED -> state
            MonitorState.IDLE -> state
        }
    }

    @Synchronized
    fun backToMonitoring() {
        if (state == MonitorState.ALERTING) {
            state = MonitorState.MONITORING
            consecutiveScreenOffCount = 0
        }
    }

    @Synchronized
    fun reset() {
        state = MonitorState.IDLE
        consecutiveScreenOffCount = 0
    }

    @Synchronized
    fun pause() {
        consecutiveScreenOffCount = 0
    }

    private fun isWithinMonitoringWindow(
        currentHour: Int,
        currentMinute: Int,
        monitorStartHour: Int,
        monitorStartMinute: Int,
        monitorEndHour: Int,
        monitorEndMinute: Int
    ): Boolean {
        val current = currentHour * 60 + currentMinute
        val start = monitorStartHour * 60 + monitorStartMinute
        val end = monitorEndHour * 60 + monitorEndMinute

        return if (start <= end) {
            // Normal window, e.g. 22:00 - 06:00 is NOT this case
            // e.g. 09:00 - 17:00
            current in start..end
        } else {
            // Overnight window, e.g. 22:00 - 06:00
            current >= start || current <= end
        }
    }
}
