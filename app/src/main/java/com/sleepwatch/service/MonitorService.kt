package com.sleepwatch.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.sleepwatch.MainActivity
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.data.db.entity.SleepRecord
import com.sleepwatch.domain.monitoring.AlertMessageSelector
import com.sleepwatch.domain.monitoring.MonitoringDecision
import com.sleepwatch.domain.monitoring.MonitoringEngine
import com.sleepwatch.domain.monitoring.MonitoringEvent
import com.sleepwatch.domain.monitoring.MonitoringSchedulePlanner
import com.sleepwatch.domain.monitoring.MonitoringSettings
import com.sleepwatch.domain.monitoring.MonitoringStatus
import com.sleepwatch.domain.monitoring.MonitoringWindow
import com.sleepwatch.domain.monitoring.MonitoringWindowResolver
import com.sleepwatch.domain.monitoring.MonitoringTriggerValidator
import com.sleepwatch.domain.monitoring.UsageSnapshot
import com.sleepwatch.domain.monitoring.toMonitoringSnapshot
import com.sleepwatch.domain.monitoring.withMonitoringSnapshot
import com.sleepwatch.domain.repository.SleepRecordRepository
import com.sleepwatch.domain.usecase.GetAlertMessagesUseCase
import com.sleepwatch.domain.usecase.CheckAchievementsUseCase
import com.sleepwatch.domain.usecase.SaveSleepRecordUseCase
import com.sleepwatch.ui.alert.AlertInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var sleepRecordRepository: SleepRecordRepository
    @Inject lateinit var saveSleepRecordUseCase: SaveSleepRecordUseCase
    @Inject lateinit var alarmScheduler: MonitorAlarmScheduler
    @Inject lateinit var getAlertMessagesUseCase: GetAlertMessagesUseCase
    @Inject lateinit var checkAchievementsUseCase: CheckAchievementsUseCase
    @Inject lateinit var permissionChecker: MonitoringPermissionChecker
    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val actionMutex = Mutex()
    private val engine = MonitoringEngine()
    private val schedulePlanner = MonitoringSchedulePlanner()
    private lateinit var overlayController: AlertOverlayController
    private var deviceReceiver: BroadcastReceiver? = null
    private var isFreshServiceInstance = true

    private val windowResolver: MonitoringWindowResolver
        get() = MonitoringWindowResolver(clock.zone)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        overlayController = AlertOverlayController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createMonitorNotification("正在恢复监测状态"))
        val action = intent?.action ?: ACTION_RECONCILE
        val expectedTriggerAt = intent?.getLongExtra(EXTRA_TRIGGER_AT, Long.MIN_VALUE)
            ?.takeUnless { it == Long.MIN_VALUE }
        scope.launch {
            actionMutex.withLock {
                try {
                    when (action) {
                        ACTION_STOP -> stopByUser()
                        ACTION_SKIP -> skipCurrentWindow()
                        ACTION_WINDOW_END -> endCurrentWindow(expectedTriggerAt)
                        ACTION_CHECK -> checkCurrentWindow(expectedTriggerAt)
                        ACTION_RECONFIGURE -> reconfigureCurrentWindow()
                        ACTION_START, ACTION_WINDOW_START, ACTION_RECONCILE -> reconcileAndRunIfDue()
                        else -> reconcileAndRunIfDue()
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Monitoring action failed: $action", error)
                    stopServiceNow()
                } finally {
                    isFreshServiceInstance = false
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun reconcileAndRunIfDue() {
        if (!settingsDataStore.serviceEnabled.first()) {
            alarmScheduler.cancelAll()
            stopServiceNow()
            return
        }
        if (!ensureRequiredPermissions()) return

        val now = clock.instant()
        val settings = loadSettings()
        val window = windowResolver.resolve(ZonedDateTime.ofInstant(now, clock.zone), settings)
        var active = sleepRecordRepository.getActiveRecord()

        if (!window.isActiveAt(now)) {
            active?.let {
                sleepRecordRepository.update(
                    it.copy(status = MonitoringStatus.INCOMPLETE.name, nextCheckTime = null)
                )
            }
            scheduleOutsideWindow(now, window)
            stopServiceNow()
            return
        }

        if (active != null && (
                now.toEpochMilli() < active.monitorStartTime ||
                    active.monitorEndTime?.let { now.toEpochMilli() >= it } == true
                )
        ) {
            sleepRecordRepository.update(
                active.copy(status = MonitoringStatus.INCOMPLETE.name, nextCheckTime = null)
            )
            active = null
        }

        var record = active ?: createSession(window, settings) ?: run {
            scheduleNextWindow(window, settings)
            stopServiceNow()
            return
        }
        if (record.monitorEndTime != window.endAt.toEpochMilli() ||
            record.targetBedtimeTime != window.targetBedtimeAt.toEpochMilli()
        ) {
            record = record.copy(
                monitorEndTime = window.endAt.toEpochMilli(),
                targetBedtimeTime = window.targetBedtimeAt.toEpochMilli()
            )
            sleepRecordRepository.update(record)
        }
        record = resetUnverifiableCandidateAfterRecovery(record)
        registerDeviceReceiver()
        updateForeground(record)

        val dueAt = record.nextCheckTime?.let(Instant::ofEpochMilli)
        when {
            record.lastCheckTime == null -> performCheck(record, now, settings, window)
            dueAt != null && dueAt <= now -> performCheck(record, now, settings, window)
            else -> scheduleActiveWindow(now, window, dueAt)
        }
    }

    private suspend fun checkCurrentWindow(expectedTriggerAt: Long?) {
        if (!settingsDataStore.serviceEnabled.first()) {
            stopByUser()
            return
        }
        if (!ensureRequiredPermissions()) return
        val now = clock.instant()
        val settings = loadSettings()
        val window = windowResolver.resolve(ZonedDateTime.ofInstant(now, clock.zone), settings)
        var active = sleepRecordRepository.getActiveRecord()
        if (active == null || !window.isActiveAt(now)) {
            reconcileAndRunIfDue()
            return
        }
        if (!MonitoringTriggerValidator.matches(expectedTriggerAt, active.nextCheckTime)) {
            reconcileAndRunIfDue()
            return
        }
        active = resetUnverifiableCandidateAfterRecovery(active)
        registerDeviceReceiver()
        performCheck(active, now, settings, window)
    }

    private suspend fun performCheck(
        record: SleepRecord,
        at: Instant,
        settings: MonitoringSettings,
        window: MonitoringWindow
    ) {
        val event = if (record.lastCheckTime == null) {
            MonitoringEvent.WindowStarted(
                at = at,
                usage = currentUsage(),
                intervalMinutes = settings.intervalMinutes,
                inactiveThreshold = settings.inactiveThreshold,
                windowEndAt = window.endAt
            )
        } else {
            MonitoringEvent.ScheduledCheck(
                at = at,
                usage = currentUsage(),
                intervalMinutes = settings.intervalMinutes,
                inactiveThreshold = settings.inactiveThreshold,
                windowEndAt = window.endAt
            )
        }
        val decision = engine.reduce(
            record.toMonitoringSnapshot().copy(
                windowEndAt = window.endAt,
                targetBedtimeAt = window.targetBedtimeAt
            ),
            event
        )
        val updated = record.withMonitoringSnapshot(decision.updatedSession)
        sleepRecordRepository.update(updated)

        if (decision.stopSession) {
            if (updated.status == MonitoringStatus.SLEEP_CONFIRMED.name) {
                finalizeConfirmedRecord(updated)
            }
            scheduleNextWindow(window, settings)
            stopServiceNow()
        } else {
            scheduleActiveWindow(at, window, decision.nextCheckAt)
            updateForeground(updated)
            if (decision.showAlert) showAlert(updated)
        }
    }

    private suspend fun endCurrentWindow(expectedTriggerAt: Long?) {
        val active = sleepRecordRepository.getActiveRecord()
        if (active != null &&
            !MonitoringTriggerValidator.matches(expectedTriggerAt, active.monitorEndTime)
        ) {
            reconcileAndRunIfDue()
            return
        }
        if (active != null) {
            val decision = engine.reduce(
                active.toMonitoringSnapshot(),
                MonitoringEvent.WindowEnded(clock.instant())
            )
            sleepRecordRepository.update(active.withMonitoringSnapshot(decision.updatedSession))
        }
        val settings = loadSettings()
        val now = clock.instant()
        val window = windowResolver.resolve(ZonedDateTime.ofInstant(now, clock.zone), settings)
        scheduleOutsideWindow(now, window)
        stopServiceNow()
    }

    private suspend fun skipCurrentWindow() {
        sleepRecordRepository.getActiveRecord()?.let { active ->
            val decision = engine.reduce(
                active.toMonitoringSnapshot(),
                MonitoringEvent.Skipped(clock.instant())
            )
            sleepRecordRepository.update(active.withMonitoringSnapshot(decision.updatedSession))
            val settings = loadSettings()
            val window = MonitoringWindow(
                cycleDate = decision.updatedSession.cycleDate,
                startAt = decision.updatedSession.windowStartAt,
                endAt = decision.updatedSession.windowEndAt,
                targetBedtimeAt = decision.updatedSession.targetBedtimeAt
            )
            scheduleNextWindow(window, settings)
        }
        stopServiceNow()
    }

    private suspend fun reconfigureCurrentWindow() {
        if (!settingsDataStore.serviceEnabled.first()) {
            stopByUser()
            return
        }
        if (!ensureRequiredPermissions()) return
        val settings = loadSettings()
        val now = clock.instant()
        val window = windowResolver.resolve(ZonedDateTime.ofInstant(now, clock.zone), settings)
        val active = sleepRecordRepository.getActiveRecord()
        if (active == null) {
            reconcileAndRunIfDue()
            return
        }

        val decision = engine.reduce(
            active.toMonitoringSnapshot().copy(targetBedtimeAt = window.targetBedtimeAt),
            MonitoringEvent.Reconfigured(
                at = now,
                isWithinWindow = window.isActiveAt(now),
                intervalMinutes = settings.intervalMinutes,
                inactiveThreshold = settings.inactiveThreshold,
                windowEndAt = window.endAt
            )
        )
        val updated = active.withMonitoringSnapshot(decision.updatedSession)
        sleepRecordRepository.update(updated)
        if (decision.stopSession) {
            if (updated.status == MonitoringStatus.SLEEP_CONFIRMED.name) {
                finalizeConfirmedRecord(updated)
            }
            scheduleOutsideWindow(now, window)
            stopServiceNow()
        } else {
            registerDeviceReceiver()
            scheduleActiveWindow(now, window, decision.nextCheckAt)
            updateForeground(updated)
        }
    }

    private suspend fun resetInactiveSequenceOnUnlock() {
        val active = sleepRecordRepository.getActiveRecord() ?: return
        val decision = engine.reduce(
            active.toMonitoringSnapshot(),
            MonitoringEvent.UserUnlocked(clock.instant())
        )
        sleepRecordRepository.update(active.withMonitoringSnapshot(decision.updatedSession))
    }

    private suspend fun finalizeConfirmedRecord(record: SleepRecord) {
        try {
            val streak = checkAchievementsUseCase.consecutiveEarlyDaysThrough(record)
            saveSleepRecordUseCase.calculateAndSaveScore(record, streak)
            checkAchievementsUseCase.checkAll()
        } catch (error: Exception) {
            Log.e(TAG, "Sleep was confirmed but scoring or achievements failed", error)
        }
    }

    private suspend fun resetUnverifiableCandidateAfterRecovery(record: SleepRecord): SleepRecord {
        if (!isFreshServiceInstance || record.lastCheckTime == null ||
            record.firstInactiveCheckTime == null
        ) return record
        val reset = record.copy(
            firstInactiveCheckTime = null,
            consecutiveInactiveChecks = 0
        )
        sleepRecordRepository.update(reset)
        return reset
    }

    private suspend fun stopByUser() {
        sleepRecordRepository.getActiveRecord()?.let { active ->
            sleepRecordRepository.update(
                active.copy(status = MonitoringStatus.INCOMPLETE.name, nextCheckTime = null)
            )
        }
        alarmScheduler.cancelAll()
        stopServiceNow()
    }

    private suspend fun ensureRequiredPermissions(): Boolean {
        val message = permissionChecker.missingRequiredMessage() ?: return true
        settingsDataStore.setServiceEnabled(false)
        sleepRecordRepository.getActiveRecord()?.let { active ->
            sleepRecordRepository.update(
                active.copy(status = MonitoringStatus.INCOMPLETE.name, nextCheckTime = null)
            )
        }
        alarmScheduler.cancelAll()
        sendPermissionRequiredNotification(message)
        stopServiceNow()
        return false
    }

    private fun sendPermissionRequiredNotification(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SleepWatch 监测已停用")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(PERMISSION_NOTIFICATION_ID, notification)
    }

    private suspend fun createSession(
        window: MonitoringWindow,
        settings: MonitoringSettings
    ): SleepRecord? {
        val existing = sleepRecordRepository.getByMonitorStartTime(window.startAt.toEpochMilli())
        if (existing != null) {
            return if (existing.status == MonitoringStatus.MONITORING.name) existing else null
        }
        val record = SleepRecord(
            date = window.cycleDate.toString(),
            monitorStartTime = window.startAt.toEpochMilli(),
            monitorEndTime = window.endAt.toEpochMilli(),
            targetBedtimeTime = window.targetBedtimeAt.toEpochMilli(),
            checkIntervalMinutes = settings.intervalMinutes,
            inactiveThreshold = settings.inactiveThreshold,
            status = MonitoringStatus.MONITORING.name,
            createdAt = clock.millis()
        )
        val id = sleepRecordRepository.insert(record)
        return record.copy(id = id)
    }

    private fun scheduleActiveWindow(
        now: Instant,
        window: MonitoringWindow,
        nextCheckAt: Instant?
    ) {
        alarmScheduler.reconcile(schedulePlanner.plan(now, window, nextCheckAt))
    }

    private fun scheduleOutsideWindow(now: Instant, window: MonitoringWindow) {
        alarmScheduler.reconcile(schedulePlanner.plan(now, window, null))
    }

    private fun scheduleNextWindow(current: MonitoringWindow, settings: MonitoringSettings) {
        val afterEnd = current.endAt.plusMillis(1)
        val next = windowResolver.resolve(ZonedDateTime.ofInstant(afterEnd, clock.zone), settings)
        alarmScheduler.reconcile(listOf(com.sleepwatch.domain.monitoring.ScheduledMonitorAction.WindowStart(next.startAt)))
    }

    private suspend fun showAlert(record: SleepRecord) {
        if (overlayController.isShowing) return
        val messages = getAlertMessagesUseCase.getEnabledMessages().first()
        val message = AlertMessageSelector.select(messages, record.totalAlertCount)
        val info = if (message == null) {
            AlertInfo("该睡觉了", "请放下手机休息", "", 1, 1)
        } else {
            AlertInfo(
                title = message.title,
                content = message.content,
                healthTip = message.healthTip,
                level = message.level,
                totalLevels = messages.size
            )
        }

        val displayed = if (Settings.canDrawOverlays(this)) {
            overlayController.show(
                info = info,
                onDismiss = { overlayController.hide() },
                onSkip = {
                    overlayController.hide()
                    scope.launch { actionMutex.withLock { skipCurrentWindow() } }
                }
            ) || sendAlertNotification(info, record.totalAlertCount + 1)
        } else {
            sendAlertNotification(info, record.totalAlertCount + 1)
        }
        if (displayed) {
            sleepRecordRepository.update(
                record.copy(
                    firstAlertTime = record.firstAlertTime ?: clock.millis(),
                    totalAlertCount = record.totalAlertCount + 1
                )
            )
            playAlertEffects()
        }
    }

    private fun sendAlertNotification(info: AlertInfo, alertCount: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(info.title)
            .setContentText("第 $alertCount 次提醒：${info.content}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
        return true
    }

    private suspend fun playAlertEffects() {
        if (settingsDataStore.soundEnabled.first()) {
            runCatching {
                RingtoneManager.getRingtone(
                    applicationContext,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                )?.play()
            }
        }
        if (settingsDataStore.vibrationEnabled.first()) {
            runCatching {
                getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun currentUsage(): UsageSnapshot {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        return UsageSnapshot(
            isInteractive = powerManager.isInteractive,
            isDeviceLocked = keyguardManager.isDeviceLocked
        )
    }

    private suspend fun loadSettings() = MonitoringSettings(
        startTime = LocalTime.of(
            settingsDataStore.monitorStartHour.first(),
            settingsDataStore.monitorStartMinute.first()
        ),
        endTime = LocalTime.of(
            settingsDataStore.monitorEndHour.first(),
            settingsDataStore.monitorEndMinute.first()
        ),
        targetBedtime = LocalTime.of(
            settingsDataStore.targetBedtimeHour.first(),
            settingsDataStore.targetBedtimeMinute.first()
        ),
        intervalMinutes = settingsDataStore.checkIntervalMinutes.first(),
        inactiveThreshold = settingsDataStore.screenOffThreshold.first()
    )

    private fun registerDeviceReceiver() {
        if (deviceReceiver != null) return
        deviceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_USER_PRESENT -> scope.launch {
                        actionMutex.withLock { resetInactiveSequenceOnUnlock() }
                    }
                    Intent.ACTION_SCREEN_OFF -> overlayController.hide()
                }
            }
        }.also { receiver ->
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun unregisterDeviceReceiver() {
        deviceReceiver?.let { runCatching { unregisterReceiver(it) } }
        deviceReceiver = null
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "睡眠监测", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "入睡提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun createMonitorNotification(content: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("SleepWatch 监测中")
            .setContentText(content)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForeground(record: SleepRecord) {
        val next = record.nextCheckTime?.let {
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                .withZone(clock.zone)
                .format(Instant.ofEpochMilli(it))
        } ?: "即将检测"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createMonitorNotification("下次检测：$next"))
    }

    private fun stopServiceNow() {
        overlayController.hide()
        unregisterDeviceReceiver()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        overlayController.hide()
        unregisterDeviceReceiver()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MonitorService"
        const val CHANNEL_ID = "monitor_channel"
        const val ALERT_CHANNEL_ID = "alert_channel"
        const val NOTIFICATION_ID = 1
        const val ALERT_NOTIFICATION_ID = 2
        const val PERMISSION_NOTIFICATION_ID = 3

        const val ACTION_START = "com.sleepwatch.action.START"
        const val ACTION_STOP = "com.sleepwatch.action.STOP"
        const val ACTION_RECONCILE = "com.sleepwatch.action.RECONCILE"
        const val ACTION_WINDOW_START = "com.sleepwatch.action.WINDOW_START"
        const val ACTION_CHECK = "com.sleepwatch.action.CHECK"
        const val ACTION_WINDOW_END = "com.sleepwatch.action.WINDOW_END"
        const val ACTION_RECONFIGURE = "com.sleepwatch.action.RECONFIGURE"
        const val ACTION_SKIP = "com.sleepwatch.action.SKIP"
        const val EXTRA_TRIGGER_AT = "com.sleepwatch.action.TRIGGER_AT"
    }
}
