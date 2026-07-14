package com.sleepwatch.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.sleepwatch.service.MonitorService

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isInteractive = powerManager.isInteractive

        val action = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                if (isInteractive) MonitorService.ACTION_SCREEN_ON else return
            }
            Intent.ACTION_SCREEN_OFF -> {
                if (!isInteractive) MonitorService.ACTION_SCREEN_OFF else return
            }
            else -> return
        }

        val serviceIntent = Intent(context, MonitorService::class.java).apply {
            this.action = action
        }
        context.startService(serviceIntent)
    }
}
