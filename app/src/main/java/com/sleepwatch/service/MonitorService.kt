package com.sleepwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.SaveSleepRecordUseCase
import com.sleepwatch.service.receiver.ScreenReceiver
import com.sleepwatch.ui.alert.AlertActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var saveSleepRecordUseCase: SaveSleepRecordUseCase
    @Inject lateinit var alarmScheduler: AlarmScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateMachine = MonitorStateMachine()
    private var screenReceiver: ScreenReceiver? = null
    private var checkJob: Job? = null

    companion object {
        const val CHANNEL_ID = "monitor_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val ACTION_START = "com.sleepwatch.START"
        const val ACTION_STOP = "com.sleepwatch.STOP"
        const val ACTION_SCREEN_ON = "com.sleepwatch.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.sleepwatch.SCREEN_OFF"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_SCREEN_OFF -> handleScreenOff()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, createNotification())

        scope.launch {
            val threshold = settingsDataStore.screenOffThreshold.first()
            stateMachine.setScreenOffThreshold(threshold)
            stateMachine.startMonitoring()

            val startHour = settingsDataStore.monitorStartHour.first()
            val startMinute = settingsDataStore.monitorStartMinute.first()
            val interval = settingsDataStore.checkIntervalMinutes.first()

            alarmScheduler.scheduleCheck(startHour, startMinute, interval)
            registerScreenReceiver()
            startPeriodicCheck()
        }
    }

    private fun stopMonitoring() {
        alarmScheduler.cancelCheck()
        checkJob?.cancel()
        unregisterScreenReceiver()
        stateMachine.reset()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleScreenOn() {
        scope.launch {
            val today = getTodayDateString()
            val skipped = settingsDataStore.skippedDate.first()
            val emergency = settingsDataStore.emergencyDate.first()

            if (skipped == today || emergency == today) return@launch

            val newState = stateMachine.onScreenOn()
            if (newState == MonitorState.ALERTING) {
                val monitorStartHour = settingsDataStore.monitorStartHour.first()
                val monitorStartMinute = settingsDataStore.monitorStartMinute.first()
                val record = saveSleepRecordUseCase.getOrCreateTodayRecord(monitorStartHour, monitorStartMinute)
                saveSleepRecordUseCase.recordScreenOnCheck(record)

                if (record.firstAlertTime == null) {
                    val updatedRecord = saveSleepRecordUseCase.recordAlert(record)
                    sendAlertNotification(updatedRecord.totalAlertCount)
                    playAlertEffects()
                    launchAlertActivity()
                }
            }
        }
    }

    private fun handleScreenOff() {
        scope.launch {
            val today = getTodayDateString()
            val skipped = settingsDataStore.skippedDate.first()
            val emergency = settingsDataStore.emergencyDate.first()

            if (skipped == today || emergency == today) return@launch

            val newState = stateMachine.onScreenOff()
            if (newState == MonitorState.SLEEP_DETECTED) {
                val monitorStartHour = settingsDataStore.monitorStartHour.first()
                val monitorStartMinute = settingsDataStore.monitorStartMinute.first()
                val record = saveSleepRecordUseCase.getOrCreateTodayRecord(monitorStartHour, monitorStartMinute)
                saveSleepRecordUseCase.recordSleepTime(record)

                // Re-read emergency to avoid stale value
                val currentEmergency = settingsDataStore.emergencyDate.first()
                if (currentEmergency == today) {
                    saveSleepRecordUseCase.recordEmergency(record)
                }

                stateMachine.reset()
            }
        }
    }

    private fun startPeriodicCheck() {
        checkJob = scope.launch {
            while (isActive) {
                delay(60_000)
                val today = getTodayDateString()
                val skipped = settingsDataStore.skippedDate.first()
                val emergency = settingsDataStore.emergencyDate.first()
                if (skipped != today && emergency != today) {
                    if (stateMachine.state == MonitorState.ALERTING) {
                        val monitorStartHour = settingsDataStore.monitorStartHour.first()
                        val monitorStartMinute = settingsDataStore.monitorStartMinute.first()
                        val record = saveSleepRecordUseCase.getOrCreateTodayRecord(monitorStartHour, monitorStartMinute)
                        saveSleepRecordUseCase.recordAlert(record)
                        sendAlertNotification(record.totalAlertCount)
                        playAlertEffects()
                        launchAlertActivity()
                    }
                }
            }
        }
    }

    private fun sendAlertNotification(alertCount: Int) {
        val intent = Intent(this, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("该睡觉了")
            .setContentText("第 ${alertCount} 次提醒：请放下手机休息")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private suspend fun playAlertEffects() {
        val soundEnabled = settingsDataStore.soundEnabled.first()
        val vibrationEnabled = settingsDataStore.vibrationEnabled.first()

        if (soundEnabled) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(applicationContext, notification)
                ringtone?.play()
            } catch (_: Exception) {}
        }

        if (vibrationEnabled) {
            try {
                val vibrator = getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) {}
        }
    }

    private fun launchAlertActivity() {
        val intent = Intent(this, AlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = ScreenReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            screenReceiver = null
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "睡眠监测",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "SleepWatch 前台服务通知"
        }
        manager.createNotificationChannel(monitorChannel)

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "入睡提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "睡眠提醒弹窗通知"
            enableVibration(true)
        }
        manager.createNotificationChannel(alertChannel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SleepWatch 监测中")
            .setContentText("正在监测您的睡眠状态")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unregisterScreenReceiver()
    }
}
