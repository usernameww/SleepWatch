package com.sleepwatch.service.accessibility

import kotlinx.coroutines.CancellationException

internal enum class AccessibilityRecoveryResult {
    MONITORING_DISABLED,
    EXACT_ALARM_SCHEDULED,
    DIRECT_RECONCILE_STARTED,
    FAILED
}

internal class AccessibilityRecoveryCoordinator(
    private val isMonitoringEnabled: suspend () -> Boolean,
    private val scheduleExactReconcile: (Long) -> Unit,
    private val isBatteryUnrestricted: () -> Boolean,
    private val startDirectReconcile: () -> Unit,
    private val nowMillis: () -> Long,
    private val onFailure: (Throwable) -> Unit = {}
) {
    suspend fun requestRecovery(): AccessibilityRecoveryResult {
        val enabled = try {
            isMonitoringEnabled()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            reportFailure(error)
            return AccessibilityRecoveryResult.FAILED
        }
        if (!enabled) return AccessibilityRecoveryResult.MONITORING_DISABLED

        return try {
            scheduleExactReconcile(nowMillis() + RECOVERY_DELAY_MILLIS)
            AccessibilityRecoveryResult.EXACT_ALARM_SCHEDULED
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            recoverWithDirectStart(error)
        } catch (error: Exception) {
            reportFailure(error)
            AccessibilityRecoveryResult.FAILED
        }
    }

    private fun recoverWithDirectStart(
        exactAlarmError: SecurityException
    ): AccessibilityRecoveryResult {
        return try {
            if (!isBatteryUnrestricted()) {
                reportFailure(exactAlarmError)
                AccessibilityRecoveryResult.FAILED
            } else {
                startDirectReconcile()
                AccessibilityRecoveryResult.DIRECT_RECONCILE_STARTED
            }
        } catch (error: Exception) {
            reportFailure(error)
            AccessibilityRecoveryResult.FAILED
        }
    }

    private fun reportFailure(error: Throwable) {
        runCatching { onFailure(error) }
    }

    companion object {
        const val RECOVERY_DELAY_MILLIS = 1_000L
    }
}
