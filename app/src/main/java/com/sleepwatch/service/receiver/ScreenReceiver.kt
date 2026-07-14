package com.sleepwatch.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sleepwatch.service.MonitorService

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, MonitorService::class.java).apply {
            action = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> MonitorService.ACTION_SCREEN_ON
                Intent.ACTION_SCREEN_OFF -> MonitorService.ACTION_SCREEN_OFF
                else -> return
            }
        }
        context.startService(serviceIntent)
    }
}
