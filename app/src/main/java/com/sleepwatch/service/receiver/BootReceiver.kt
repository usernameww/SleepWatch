package com.sleepwatch.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.service.MonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val dataStore = SettingsDataStore(context)
            val enabled = runBlocking { dataStore.serviceEnabled.first() }
            if (enabled) {
                val startHour = runBlocking { dataStore.monitorStartHour.first() }
                val startMinute = runBlocking { dataStore.monitorStartMinute.first() }
                val now = Calendar.getInstance()
                val monitorStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                }
                // Start if current time is within monitoring window
                if (now.after(monitorStart) || now.get(Calendar.HOUR_OF_DAY) < 12) {
                    val serviceIntent = Intent(context, MonitorService::class.java).apply {
                        action = MonitorService.ACTION_START
                    }
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
