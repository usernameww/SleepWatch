package com.sleepwatch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.domain.usecase.GetAlertMessagesUseCase
import com.sleepwatch.domain.usecase.SaveSleepRecordUseCase
import com.sleepwatch.service.receiver.ScreenReceiver
import com.sleepwatch.ui.alert.AlertActivity
import com.sleepwatch.ui.alert.AlertScreen
import com.sleepwatch.ui.alert.AlertViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service(), LifecycleOwner, ViewModelStoreOwner {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var saveSleepRecordUseCase: SaveSleepRecordUseCase
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var getAlertMessagesUseCase: GetAlertMessagesUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val stateMachine = MonitorStateMachine()
    private var screenReceiver: ScreenReceiver? = null
    private var checkJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alertOverlay: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    companion object {
        const val CHANNEL_ID = "monitor_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val ACTION_START = "com.sleepwatch.START"
        const val ACTION_STOP = "com.sleepwatch.STOP"
        const val ACTION_SCREEN_ON = "com.sleepwatch.SCREEN_ON"
        const val ACTION_SCREEN_OFF = "com.sleepwatch.SCREEN_OFF"
        const val ACTION_ALERT_DISMISSED = "com.sleepwatch.ALERT_DISMISSED"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_SCREEN_OFF -> handleScreenOff()
            ACTION_ALERT_DISMISSED -> handleAlertDismissed()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()

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

            // Check if we're already past monitoring start time and phone is on
            if (isPastMonitoringStart(startHour, startMinute) && isPhoneScreenOn()) {
                stateMachine.onScreenOn() // -> ALERTING
                triggerAlert()
            }
        }
    }

    private fun stopMonitoring() {
        alarmScheduler.cancelCheck()
        checkJob?.cancel()
        unregisterScreenReceiver()
        removeAlertOverlay()
        stateMachine.reset()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleScreenOn() {
        scope.launch {
            if (isSkippedOrEmergency()) return@launch

            val newState = stateMachine.onScreenOn()
            if (newState == MonitorState.ALERTING) {
                triggerAlert()
            }
        }
    }

    private fun handleScreenOff() {
        scope.launch {
            if (isSkippedOrEmergency()) return@launch

            val newState = stateMachine.onScreenOff()
            if (newState == MonitorState.SLEEP_DETECTED) {
                recordSleepDetected()
            }
        }
    }

    private fun handleAlertDismissed() {
        // User clicked "我知道了" - go back to MONITORING state
        // so we can alert again on next screen-on event
        if (stateMachine.state == MonitorState.ALERTING) {
            stateMachine.backToMonitoring()
        }
    }

    private fun startPeriodicCheck() {
        checkJob = scope.launch {
            while (isActive) {
                delay(60_000) // Check every 60 seconds
                if (isSkippedOrEmergency()) continue

                val endHour = settingsDataStore.monitorEndHour.first()
                val endMinute = settingsDataStore.monitorEndMinute.first()
                if (isPastEndTime(endHour, endMinute)) {
                    // End monitoring for tonight
                    if (stateMachine.state == MonitorState.MONITORING || stateMachine.state == MonitorState.ALERTING) {
                        recordSleepDetected()
                    }
                    continue
                }

                val startHour = settingsDataStore.monitorStartHour.first()
                val startMinute = settingsDataStore.monitorStartMinute.first()

                when {
                    // If we're past monitoring time and in MONITORING state, check if phone is on
                    stateMachine.state == MonitorState.MONITORING && isPastMonitoringStart(startHour, startMinute) -> {
                        if (isPhoneScreenOn()) {
                            stateMachine.onScreenOn() // -> ALERTING
                            triggerAlert()
                        }
                    }
                    // If we're in ALERTING, keep triggering alerts
                    stateMachine.state == MonitorState.ALERTING -> {
                        triggerAlert()
                    }
                }
            }
        }
    }

    private suspend fun triggerAlert() {
        val monitorStartHour = settingsDataStore.monitorStartHour.first()
        val monitorStartMinute = settingsDataStore.monitorStartMinute.first()
        val record = saveSleepRecordUseCase.getOrCreateTodayRecord(monitorStartHour, monitorStartMinute)
        saveSleepRecordUseCase.recordAlert(record)
        playAlertEffects()

        if (Settings.canDrawOverlays(this)) {
            // Overlay permission granted: show floating window on top of any app
            launchAlertOverlay()
        } else {
            // No overlay permission: fallback to notification + activity launch
            sendAlertNotification(record.totalAlertCount)
            launchAlertActivity()
        }
    }

    private fun launchAlertOverlay() {
        removeAlertOverlay()

        val alertViewModel = AlertViewModel(getAlertMessagesUseCase, settingsDataStore)

        val composeView = ComposeView(this).apply {
            setContent {
                AlertScreen(
                    onDismiss = {
                        handleAlertDismissed()
                        removeAlertOverlay()
                    },
                    viewModel = alertViewModel
                )
            }
        }
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(composeView, params)
        alertOverlay = composeView
    }

    private fun removeAlertOverlay() {
        alertOverlay?.let {
            try {
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.removeView(it)
            } catch (_: Exception) {}
            alertOverlay = null
        }
    }

    private suspend fun recordSleepDetected() {
        val monitorStartHour = settingsDataStore.monitorStartHour.first()
        val monitorStartMinute = settingsDataStore.monitorStartMinute.first()
        val record = saveSleepRecordUseCase.getOrCreateTodayRecord(monitorStartHour, monitorStartMinute)
        saveSleepRecordUseCase.recordSleepTime(record)

        // Re-read emergency to avoid stale value
        val today = getTodayDateString()
        val currentEmergency = settingsDataStore.emergencyDate.first()
        if (currentEmergency == today) {
            saveSleepRecordUseCase.recordEmergency(record)
        }

        stateMachine.reset()
    }

    private fun isPastMonitoringStart(startHour: Int, startMinute: Int): Boolean {
        val now = Calendar.getInstance()
        val monitorStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
        }
        return now.after(monitorStart)
    }

    private fun isPastEndTime(endHour: Int, endMinute: Int): Boolean {
        val now = Calendar.getInstance()
        val endTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
        }
        return now.after(endTime)
    }

    private fun isPhoneScreenOn(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private suspend fun isSkippedOrEmergency(): Boolean {
        val today = getTodayDateString()
        val skipped = settingsDataStore.skippedDate.first()
        val emergency = settingsDataStore.emergencyDate.first()
        return skipped == today || emergency == today
    }

    private fun sendAlertNotification(alertCount: Int) {
        // On Android 13+, POST_NOTIFICATIONS permission is required
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(this, AlertActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
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
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
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

    private fun launchAlertActivity(): Boolean {
        return try {
            val intent = Intent(this, AlertActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
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

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepWatch::Monitor")
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scope.launch {
            val enabled = settingsDataStore.serviceEnabled.first()
            if (enabled) {
                val restartIntent = Intent(this@MonitorService, MonitorService::class.java).apply {
                    action = ACTION_START
                }
                startService(restartIntent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeAlertOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        releaseWakeLock()
        scope.cancel()
        unregisterScreenReceiver()
    }
}
