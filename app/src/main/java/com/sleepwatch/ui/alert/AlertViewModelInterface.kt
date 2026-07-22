package com.sleepwatch.ui.alert

/**
 * Interface for alert view models to support both Hilt and non-Hilt implementations
 */
interface AlertViewModelInterface {
    suspend fun getNextMessage(): AlertInfo
}
