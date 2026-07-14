package com.sleepwatch.service

enum class MonitorState {
    IDLE, MONITORING, ALERTING, SLEEP_DETECTED
}

class MonitorStateMachine {
    var state: MonitorState = MonitorState.IDLE
        private set

    private var consecutiveScreenOffCount = 0
    private var screenOffThreshold = 3

    fun setScreenOffThreshold(threshold: Int) {
        screenOffThreshold = threshold
    }

    fun startMonitoring() {
        if (state == MonitorState.IDLE) {
            state = MonitorState.MONITORING
            consecutiveScreenOffCount = 0
        }
    }

    fun onScreenOn(): MonitorState {
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
                consecutiveScreenOffCount++
                if (consecutiveScreenOffCount >= screenOffThreshold) {
                    state = MonitorState.SLEEP_DETECTED
                }
                state
            }
            MonitorState.SLEEP_DETECTED -> state
            MonitorState.IDLE -> state
        }
    }

    fun reset() {
        state = MonitorState.IDLE
        consecutiveScreenOffCount = 0
    }

    fun pause() {
        consecutiveScreenOffCount = 0
    }
}
