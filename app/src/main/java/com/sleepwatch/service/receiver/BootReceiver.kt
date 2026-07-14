package com.sleepwatch.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepwatch.service.MonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MonitorService::class.java).apply {
                action = MonitorService.ACTION_START
            }
            context.startService(serviceIntent)
        }
    }
}
