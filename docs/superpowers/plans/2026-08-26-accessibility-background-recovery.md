# Optional Accessibility Background Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional, privacy-minimized accessibility service that requests an immediate monitoring reconciliation after SleepWatch is removed from Recents, while preserving the existing exact-alarm and foreground-service architecture.

**Architecture:** A system-bound `SleepWatchAccessibilityService` delegates recovery decisions to a pure Kotlin coordinator. The coordinator schedules a unique exact reconciliation alarm when monitoring is enabled, with a battery-unrestricted direct-start fallback only when exact scheduling is unavailable. Existing `MonitorAlarmReceiver` and `MonitorService.ACTION_RECONCILE` remain the only path that reconstructs monitoring state.

**Tech Stack:** Kotlin 2.0, Android SDK 35/minSdk 26, AccessibilityService, AlarmManager, foreground service `specialUse`, Hilt, DataStore, Jetpack Compose Material 3, JUnit 4, kotlinx-coroutines-test, AndroidX instrumentation tests.

---

## Execution preflight

- Execute this plan in a dedicated Git worktree created with `superpowers:using-git-worktrees`.
- Start from the commit containing the approved design and its manifest-contract correction (`875ba83` or a descendant).
- Preserve the unrelated untracked `.superpowers/` directory; never stage it.
- Read the approved design before editing: `docs/superpowers/specs/2026-08-26-accessibility-background-recovery-design.md`.

## File map

### New production files

- `app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinator.kt` — pure recovery decision, exact-alarm request, and guarded fallback.
- `app/src/main/java/com/sleepwatch/service/accessibility/SleepWatchAccessibilityService.kt` — system callbacks and production dependency wiring only.
- `app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusChecker.kt` — reads the actual enabled-service list from `AccessibilityManager`.
- `app/src/main/java/com/sleepwatch/service/MonitorAlarmActionMapper.kt` — pure alarm-to-service action mapping.
- `app/src/main/java/com/sleepwatch/ui/setup/SetupPermissionModels.kt` — permission-card model and optional accessibility presentation factory.
- `app/src/main/res/xml/sleepwatch_accessibility_service.xml` — minimal accessibility capability declaration.

### New test files

- `app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinatorTest.kt`
- `app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusCheckerTest.kt`
- `app/src/test/java/com/sleepwatch/service/MonitorAlarmActionMapperTest.kt`
- `app/src/test/java/com/sleepwatch/ui/setup/SetupPermissionModelsTest.kt`
- `app/src/test/java/com/sleepwatch/ui/settings/SettingsPermissionSubtitleTest.kt`
- `app/src/androidTest/java/com/sleepwatch/service/accessibility/AccessibilityServiceDeclarationTest.kt`

### Modified production and documentation files

- `app/src/main/java/com/sleepwatch/service/AlarmScheduler.kt`
- `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`
- `app/src/main/java/com/sleepwatch/ui/setup/SetupScreen.kt`
- `app/src/main/java/com/sleepwatch/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/sleepwatch/ui/settings/SettingsScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings.xml`
- `docs/REQUIREMENTS.md`
- `docs/PROGRESS.md`

## Task 1: Build the pure recovery coordinator

**Files:**

- Create: `app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinatorTest.kt`
- Create: `app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinator.kt`

- [ ] **Step 1: Write the failing coordinator tests**

Create `AccessibilityRecoveryCoordinatorTest.kt`:

```kotlin
package com.sleepwatch.service.accessibility

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRecoveryCoordinatorTest {
    @Test
    fun `enabled monitoring schedules exact reconciliation one second later`() = runTest {
        var scheduledAt: Long? = null
        var directStarts = 0
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { scheduledAt = it },
            isBatteryUnrestricted = { false },
            startDirectReconcile = { directStarts++ },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.EXACT_ALARM_SCHEDULED, result)
        assertEquals(11_000L, scheduledAt)
        assertEquals(0, directStarts)
    }

    @Test
    fun `disabled monitoring performs no recovery action`() = runTest {
        var scheduled = false
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { false },
            scheduleExactReconcile = { scheduled = true },
            isBatteryUnrestricted = { true },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.MONITORING_DISABLED, result)
        assertFalse(scheduled)
        assertFalse(directStarted)
    }

    @Test
    fun `missing exact alarm permission uses direct start only when battery unrestricted`() = runTest {
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { throw SecurityException("exact alarm unavailable") },
            isBatteryUnrestricted = { true },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.DIRECT_RECONCILE_STARTED, result)
        assertTrue(directStarted)
    }

    @Test
    fun `missing exact alarm permission without battery exemption fails closed`() = runTest {
        val securityError = SecurityException("exact alarm unavailable")
        var reported: Throwable? = null
        var directStarted = false
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { true },
            scheduleExactReconcile = { throw securityError },
            isBatteryUnrestricted = { false },
            startDirectReconcile = { directStarted = true },
            nowMillis = { 10_000L },
            onFailure = { reported = it }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.FAILED, result)
        assertFalse(directStarted)
        assertSame(securityError, reported)
    }

    @Test
    fun `settings read failure is reported and contained`() = runTest {
        val readError = IllegalStateException("settings unavailable")
        var reported: Throwable? = null
        val coordinator = AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { throw readError },
            scheduleExactReconcile = {},
            isBatteryUnrestricted = { true },
            startDirectReconcile = {},
            nowMillis = { 10_000L },
            onFailure = { reported = it }
        )

        val result = coordinator.requestRecovery()

        assertEquals(AccessibilityRecoveryResult.FAILED, result)
        assertSame(readError, reported)
    }
}
```

- [ ] **Step 2: Run the focused tests and confirm the expected failure**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.service.accessibility.AccessibilityRecoveryCoordinatorTest
```

Expected: compilation fails because `AccessibilityRecoveryCoordinator` and `AccessibilityRecoveryResult` do not exist.

- [ ] **Step 3: Implement the minimal coordinator**

Create `AccessibilityRecoveryCoordinator.kt`:

```kotlin
package com.sleepwatch.service.accessibility

import kotlinx.coroutines.CancellationException

enum class AccessibilityRecoveryResult {
    MONITORING_DISABLED,
    EXACT_ALARM_SCHEDULED,
    DIRECT_RECONCILE_STARTED,
    FAILED
}

class AccessibilityRecoveryCoordinator(
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

    private fun recoverWithDirectStart(exactAlarmError: SecurityException): AccessibilityRecoveryResult {
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
```

- [ ] **Step 4: Run the focused tests and confirm they pass**

Run the same focused Gradle command. Expected: five tests pass.

- [ ] **Step 5: Commit the coordinator**

```bash
git add app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinator.kt app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityRecoveryCoordinatorTest.kt
git commit -m "feat: add accessibility recovery coordinator"
```

## Task 2: Add a unique reconciliation alarm route

**Files:**

- Create: `app/src/test/java/com/sleepwatch/service/MonitorAlarmActionMapperTest.kt`
- Create: `app/src/main/java/com/sleepwatch/service/MonitorAlarmActionMapper.kt`
- Modify: `app/src/main/java/com/sleepwatch/service/AlarmScheduler.kt`

- [ ] **Step 1: Write the failing alarm mapping test**

```kotlin
package com.sleepwatch.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorAlarmActionMapperTest {
    @Test
    fun `all alarm actions map to the matching service action`() {
        assertEquals(
            MonitorService.ACTION_WINDOW_START,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_WINDOW_START)
        )
        assertEquals(
            MonitorService.ACTION_CHECK,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_CHECK)
        )
        assertEquals(
            MonitorService.ACTION_WINDOW_END,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_WINDOW_END)
        )
        assertEquals(
            MonitorService.ACTION_RECONCILE,
            MonitorAlarmActionMapper.toServiceAction(MonitorAlarmScheduler.ACTION_RECONCILE)
        )
    }

    @Test
    fun `unknown or missing alarm action is ignored`() {
        assertNull(MonitorAlarmActionMapper.toServiceAction("unknown"))
        assertNull(MonitorAlarmActionMapper.toServiceAction(null))
    }
}
```

- [ ] **Step 2: Run the focused test and confirm it fails**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.service.MonitorAlarmActionMapperTest
```

Expected: compilation fails because the mapper and reconciliation alarm action do not exist.

- [ ] **Step 3: Create the action mapper**

Create `MonitorAlarmActionMapper.kt`:

```kotlin
package com.sleepwatch.service

object MonitorAlarmActionMapper {
    fun toServiceAction(alarmAction: String?): String? = when (alarmAction) {
        MonitorAlarmScheduler.ACTION_WINDOW_START -> MonitorService.ACTION_WINDOW_START
        MonitorAlarmScheduler.ACTION_CHECK -> MonitorService.ACTION_CHECK
        MonitorAlarmScheduler.ACTION_WINDOW_END -> MonitorService.ACTION_WINDOW_END
        MonitorAlarmScheduler.ACTION_RECONCILE -> MonitorService.ACTION_RECONCILE
        else -> null
    }
}
```

- [ ] **Step 4: Extend `MonitorAlarmScheduler` without disturbing scheduled window alarms**

In `AlarmScheduler.kt`, add the public scheduling method directly after `scheduleWindowEnd`:

```kotlin
fun scheduleReconcile(triggerAtMillis: Long) {
    if (!canScheduleExactAlarms()) {
        throw SecurityException("Exact alarm permission is required for accessibility recovery")
    }
    schedule(ACTION_RECONCILE, REQUEST_RECONCILE, triggerAtMillis)
}
```

Make `cancelAll()` cancel any pending recovery request as well as the three existing alarms:

```kotlin
fun cancelAll() {
    cancel(ACTION_WINDOW_START, REQUEST_WINDOW_START)
    cancel(ACTION_CHECK, REQUEST_CHECK)
    cancel(ACTION_WINDOW_END, REQUEST_WINDOW_END)
    cancel(ACTION_RECONCILE, REQUEST_RECONCILE)
}
```

Add the action and request code in the companion object:

```kotlin
const val ACTION_RECONCILE = "com.sleepwatch.alarm.RECONCILE"

private const val REQUEST_RECONCILE = 103
```

Do not add `scheduleReconcile()` to `reconcile(actions)`: accessibility recovery is a one-shot entry signal, not part of the persisted window schedule.

Replace the receiver's local `when` mapping with the tested mapper:

```kotlin
val serviceAction = MonitorAlarmActionMapper.toServiceAction(intent?.action) ?: return
```

Keep forwarding `EXTRA_TRIGGER_AT`. The reconciliation path ignores that extra, while the existing check/end validators continue using it.

- [ ] **Step 5: Run mapping and existing scheduling-domain tests**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.service.MonitorAlarmActionMapperTest --tests com.sleepwatch.domain.monitoring.MonitoringSchedulePlannerTest --tests com.sleepwatch.domain.monitoring.MonitoringTriggerValidatorTest
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit the alarm route**

```bash
git add app/src/main/java/com/sleepwatch/service/AlarmScheduler.kt app/src/main/java/com/sleepwatch/service/MonitorAlarmActionMapper.kt app/src/test/java/com/sleepwatch/service/MonitorAlarmActionMapperTest.kt
git commit -m "feat: add immediate monitoring reconciliation alarm"
```

## Task 3: Declare and wire the minimal accessibility service

**Files:**

- Create: `app/src/androidTest/java/com/sleepwatch/service/accessibility/AccessibilityServiceDeclarationTest.kt`
- Create: `app/src/main/java/com/sleepwatch/service/accessibility/SleepWatchAccessibilityService.kt`
- Create: `app/src/main/res/xml/sleepwatch_accessibility_service.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Write the failing manifest contract instrumentation test**

```kotlin
package com.sleepwatch.service.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityServiceDeclarationTest {
    @Test
    fun accessibilityServiceIsSystemBoundAndSurvivesTaskRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, SleepWatchAccessibilityService::class.java),
            PackageManager.GET_META_DATA
        )

        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo.permission)
        assertTrue(serviceInfo.exported)
        assertEquals(0, serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
    }
}
```

- [ ] **Step 2: Compile instrumentation sources and confirm failure**

Run:

```bash
sh gradlew compileDebugAndroidTestSources
```

Expected: compilation fails because `SleepWatchAccessibilityService` does not exist.

- [ ] **Step 3: Create the accessibility service with no event processing**

Create `SleepWatchAccessibilityService.kt`:

```kotlin
package com.sleepwatch.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.sleepwatch.data.datastore.SettingsDataStore
import com.sleepwatch.service.MonitorAlarmScheduler
import com.sleepwatch.service.MonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@AndroidEntryPoint
class SleepWatchAccessibilityService : AccessibilityService() {
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var alarmScheduler: MonitorAlarmScheduler
    @Inject lateinit var clock: Clock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val recoveryCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        AccessibilityRecoveryCoordinator(
            isMonitoringEnabled = { settingsDataStore.serviceEnabled.first() },
            scheduleExactReconcile = alarmScheduler::scheduleReconcile,
            isBatteryUnrestricted = {
                getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(packageName)
            },
            startDirectReconcile = {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, MonitorService::class.java)
                        .setAction(MonitorService.ACTION_RECONCILE)
                )
            },
            nowMillis = clock::millis,
            onFailure = { error -> Log.w(TAG, "Accessibility recovery request failed", error) }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        requestRecovery("connected")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        requestRecovery("task_removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun requestRecovery(reason: String) {
        serviceScope.launch {
            val result = recoveryCoordinator.requestRecovery()
            Log.d(TAG, "Accessibility recovery $reason: $result")
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SleepWatchAccessibility"
    }
}
```

- [ ] **Step 4: Add the privacy-minimized XML configuration**

Create `app/src/main/res/xml/sleepwatch_accessibility_service.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="false"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_service_description"
    android:isAccessibilityTool="false"
    android:notificationTimeout="100"
    android:packageNames="com.sleepwatch" />
```

Add these resources to `strings.xml`:

```xml
<!-- Optional accessibility background recovery -->
<string name="accessibility_service_label">SleepWatch 后台增强</string>
<string name="accessibility_service_description">仅用于在 SleepWatch 被划出最近任务后恢复睡眠监测；不会读取或操作屏幕内容</string>
```

- [ ] **Step 5: Register the service using the system binding contract**

Add this service inside `<application>` in `AndroidManifest.xml`, next to `MonitorService`:

```xml
<service
    android:name=".service.accessibility.SleepWatchAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:stopWithTask="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/sleepwatch_accessibility_service" />
</service>
```

`exported="true"` is required for the Android system to discover and bind an accessibility service. `BIND_ACCESSIBILITY_SERVICE` restricts binding to the system; do not change either value.

- [ ] **Step 6: Compile both app and instrumentation sources**

Run:

```bash
sh gradlew compileDebugKotlin compileDebugAndroidTestSources
```

Expected: both tasks succeed. Running the manifest assertion itself is deferred to the connected-device verification task.

- [ ] **Step 7: Commit the system service declaration**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/sleepwatch/service/accessibility/SleepWatchAccessibilityService.kt app/src/main/res/xml/sleepwatch_accessibility_service.xml app/src/main/res/values/strings.xml app/src/androidTest/java/com/sleepwatch/service/accessibility/AccessibilityServiceDeclarationTest.kt
git commit -m "feat: add optional accessibility recovery service"
```

## Task 4: Read the real accessibility authorization state

**Files:**

- Create: `app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusCheckerTest.kt`
- Create: `app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusChecker.kt`

- [ ] **Step 1: Write the failing component matching tests**

```kotlin
package com.sleepwatch.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStatusCheckerTest {
    private val sleepWatch = AccessibilityServiceId(
        packageName = "com.sleepwatch",
        className = "com.sleepwatch.service.accessibility.SleepWatchAccessibilityService"
    )

    @Test
    fun `exact service component is reported enabled`() {
        assertTrue(isExpectedServiceEnabled(sleepWatch, listOf(sleepWatch)))
    }

    @Test
    fun `same class name in a different package is not accepted`() {
        val otherPackage = sleepWatch.copy(packageName = "com.example.other")

        assertFalse(isExpectedServiceEnabled(sleepWatch, listOf(otherPackage)))
    }

    @Test
    fun `empty enabled service list is disabled`() {
        assertFalse(isExpectedServiceEnabled(sleepWatch, emptyList()))
    }
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.service.accessibility.AccessibilityServiceStatusCheckerTest
```

Expected: compilation fails because the status model and checker function do not exist.

- [ ] **Step 3: Implement the status checker without persisted authorization state**

Create `AccessibilityServiceStatusChecker.kt`:

```kotlin
package com.sleepwatch.service.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AccessibilityServiceId(
    val packageName: String,
    val className: String
)

fun isExpectedServiceEnabled(
    expected: AccessibilityServiceId,
    enabledServices: List<AccessibilityServiceId>
): Boolean = expected in enabledServices

@Singleton
class AccessibilityServiceStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val enabledServices = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .map { info ->
                AccessibilityServiceId(
                    packageName = info.resolveInfo.serviceInfo.packageName,
                    className = info.resolveInfo.serviceInfo.name
                )
            }
        val expected = AccessibilityServiceId(
            packageName = context.packageName,
            className = SleepWatchAccessibilityService::class.java.name
        )
        return isExpectedServiceEnabled(expected, enabledServices)
    }
}
```

- [ ] **Step 4: Run the focused test and confirm it passes**

Run the same focused Gradle command. Expected: three tests pass.

- [ ] **Step 5: Commit the authorization checker**

```bash
git add app/src/main/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusChecker.kt app/src/test/java/com/sleepwatch/service/accessibility/AccessibilityServiceStatusCheckerTest.kt
git commit -m "feat: report accessibility recovery authorization"
```

## Task 5: Add the optional permission card and disclosure

**Files:**

- Create: `app/src/test/java/com/sleepwatch/ui/setup/SetupPermissionModelsTest.kt`
- Create: `app/src/main/java/com/sleepwatch/ui/setup/SetupPermissionModels.kt`
- Modify: `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`
- Modify: `app/src/main/java/com/sleepwatch/ui/setup/SetupScreen.kt`

- [ ] **Step 1: Write the failing optional-card presentation tests**

```kotlin
package com.sleepwatch.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupPermissionModelsTest {
    @Test
    fun `accessibility recovery is optional and explains its disabled state`() {
        val item = accessibilityPermissionItem(isEnabled = false, onRequest = {})

        assertFalse(item.isRequired)
        assertFalse(item.isGranted)
        assertEquals("未开启（可选）", item.missingStatus)
        assertEquals("用于在最近任务中划掉 SleepWatch 后恢复睡眠监测", item.description)
    }

    @Test
    fun `enabled accessibility card uses enabled wording and forwards request`() {
        var requested = false
        val item = accessibilityPermissionItem(
            isEnabled = true,
            onRequest = { requested = true }
        )

        assertEquals("已开启", item.grantedStatus)
        item.action()
        assertTrue(requested)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.ui.setup.SetupPermissionModelsTest
```

Expected: compilation fails because `accessibilityPermissionItem` and the expanded `PermissionItem` model do not exist.

- [ ] **Step 3: Extract the permission model and implement the accessibility factory**

Create `SetupPermissionModels.kt`:

```kotlin
package com.sleepwatch.ui.setup

data class PermissionItem(
    val name: String,
    val description: String,
    val isRequired: Boolean,
    val isGranted: Boolean,
    val grantedStatus: String = "已授权",
    val missingStatus: String = if (isRequired) "未授权（必需）" else "未设置（推荐）",
    val action: () -> Unit
)

fun accessibilityPermissionItem(
    isEnabled: Boolean,
    onRequest: () -> Unit
): PermissionItem = PermissionItem(
    name = "后台增强（无障碍服务）",
    description = "用于在最近任务中划掉 SleepWatch 后恢复睡眠监测",
    isRequired = false,
    isGranted = isEnabled,
    grantedStatus = "已开启",
    missingStatus = "未开启（可选）",
    action = onRequest
)
```

Remove the old `PermissionItem` declaration from the bottom of `SetupViewModel.kt`.

- [ ] **Step 4: Add the system-state check and disclosure state to `SetupViewModel`**

Add the checker import:

```kotlin
import com.sleepwatch.service.accessibility.AccessibilityServiceStatusChecker
```

Inject `AccessibilityServiceStatusChecker`:

```kotlin
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accessibilityStatusChecker: AccessibilityServiceStatusChecker
) : ViewModel() {
```

Add the disclosure state:

```kotlin
private val _accessibilityDisclosureVisible = MutableStateFlow(false)
val accessibilityDisclosureVisible: StateFlow<Boolean> = _accessibilityDisclosureVisible
```

At the end of `checkPermissions()`, before assigning `_permissions.value`, append:

```kotlin
items.add(
    accessibilityPermissionItem(
        isEnabled = accessibilityStatusChecker.isEnabled(),
        onRequest = { _accessibilityDisclosureVisible.value = true }
    )
)
```

Add these public actions and the private settings launcher:

```kotlin
fun confirmAccessibilityDisclosure() {
    _accessibilityDisclosureVisible.value = false
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun dismissAccessibilityDisclosure() {
    _accessibilityDisclosureVisible.value = false
}
```

Do not include the accessibility card in `allRequiredGranted()` beyond the existing `isRequired` filter; its factory fixes `isRequired` to `false`.

- [ ] **Step 5: Render the disclosure and explicit status copy in `SetupScreen`**

Collect the new state near the other flows:

```kotlin
val accessibilityDisclosureVisible by viewModel.accessibilityDisclosureVisible.collectAsState()
```

In `PermissionCard`, replace the status `Text` value with:

```kotlin
text = if (permission.isGranted) permission.grantedStatus else permission.missingStatus
```

After the `Scaffold`, add the disclosure dialog:

```kotlin
if (accessibilityDisclosureVisible) {
    AlertDialog(
        onDismissRequest = viewModel::dismissAccessibilityDisclosure,
        title = { Text("开启后台增强") },
        text = {
            Text(
                "SleepWatch 使用无障碍服务，仅用于在应用被划出最近任务后触发后台监测恢复。" +
                    "不会读取、记录、上传或操作屏幕内容。"
            )
        },
        confirmButton = {
            TextButton(onClick = viewModel::confirmAccessibilityDisclosure) {
                Text("前往开启")
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissAccessibilityDisclosure) {
                Text("取消")
            }
        }
    )
}
```

The existing `ON_RESUME` observer already calls `checkPermissions()`, so the card will refresh after the user returns from system settings.

- [ ] **Step 6: Run the focused model test and compile Compose**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.ui.setup.SetupPermissionModelsTest compileDebugKotlin
```

Expected: both tasks succeed.

- [ ] **Step 7: Commit the permission flow**

```bash
git add app/src/main/java/com/sleepwatch/ui/setup/SetupPermissionModels.kt app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt app/src/main/java/com/sleepwatch/ui/setup/SetupScreen.kt app/src/test/java/com/sleepwatch/ui/setup/SetupPermissionModelsTest.kt
git commit -m "feat: add accessibility recovery permission guidance"
```

## Task 6: Surface background-enhancement status in Settings

**Files:**

- Create: `app/src/test/java/com/sleepwatch/ui/settings/SettingsPermissionSubtitleTest.kt`
- Modify: `app/src/main/java/com/sleepwatch/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/sleepwatch/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Write the failing subtitle tests**

```kotlin
package com.sleepwatch.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPermissionSubtitleTest {
    @Test
    fun `enabled background enhancement is shown in permission subtitle`() {
        assertEquals(
            "检查所需权限；后台增强已开启",
            permissionGuideSubtitle(accessibilityEnabled = true)
        )
    }

    @Test
    fun `disabled background enhancement is shown in permission subtitle`() {
        assertEquals(
            "检查所需权限；后台增强未开启",
            permissionGuideSubtitle(accessibilityEnabled = false)
        )
    }
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.ui.settings.SettingsPermissionSubtitleTest
```

Expected: compilation fails because `permissionGuideSubtitle` does not exist.

- [ ] **Step 3: Add status state and copy to `SettingsViewModel`**

Add the checker import:

```kotlin
import com.sleepwatch.service.accessibility.AccessibilityServiceStatusChecker
```

Add this top-level function before the ViewModel class:

```kotlin
fun permissionGuideSubtitle(accessibilityEnabled: Boolean): String =
    if (accessibilityEnabled) {
        "检查所需权限；后台增强已开启"
    } else {
        "检查所需权限；后台增强未开启"
    }
```

Inject `AccessibilityServiceStatusChecker` immediately after `MonitoringPermissionChecker`:

```kotlin
private val accessibilityStatusChecker: AccessibilityServiceStatusChecker,
```

Add observable state and a refresh method:

```kotlin
private val _accessibilityEnabled = MutableStateFlow(false)
val accessibilityEnabled: StateFlow<Boolean> = _accessibilityEnabled.asStateFlow()

fun refreshAccessibilityStatus() {
    _accessibilityEnabled.value = accessibilityStatusChecker.isEnabled()
}
```

- [ ] **Step 4: Refresh and display status in `SettingsScreen`**

Add lifecycle imports matching `SetupScreen`:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
```

Collect status and obtain the lifecycle owner near the other state declarations:

```kotlin
val accessibilityEnabled by viewModel.accessibilityEnabled.collectAsState()
val lifecycleOwner = LocalLifecycleOwner.current
```

Add initial and resume refresh behavior before the main `Column`:

```kotlin
LaunchedEffect(Unit) {
    viewModel.refreshAccessibilityStatus()
}

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            viewModel.refreshAccessibilityStatus()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

Replace the existing permission-guide subtitle with:

```kotlin
subtitle = permissionGuideSubtitle(accessibilityEnabled)
```

- [ ] **Step 5: Run the focused test and compile the app**

Run:

```bash
sh gradlew testDebugUnitTest --tests com.sleepwatch.ui.settings.SettingsPermissionSubtitleTest compileDebugKotlin
```

Expected: test and compilation pass.

- [ ] **Step 6: Commit the settings status**

```bash
git add app/src/main/java/com/sleepwatch/ui/settings/SettingsViewModel.kt app/src/main/java/com/sleepwatch/ui/settings/SettingsScreen.kt app/src/test/java/com/sleepwatch/ui/settings/SettingsPermissionSubtitleTest.kt
git commit -m "feat: show accessibility recovery status"
```

## Task 7: Update reliability documentation and run full verification

**Files:**

- Modify: `docs/REQUIREMENTS.md`
- Modify: `docs/PROGRESS.md`

- [ ] **Step 1: Update the background-running contract in `REQUIREMENTS.md`**

Replace the existing statement that groups Recents removal with ordinary alarm recovery and the blanket `onTaskRemoved()` prohibition with explicit component boundaries:

```markdown
- `MonitorService` 保持 `START_NOT_STICKY`，且不使用普通服务 `onTaskRemoved()` 自启动；窗口内由前台服务运行，窗口外只保留下一次开始闹钟。
- 用户可选开启“后台增强（无障碍服务）”。该服务不读取或操作界面内容，只在系统连接或最近任务被划掉时安排一次即时状态对账闹钟；未开启时继续使用原有恢复机制。
- 系统回收、进程终止或划掉最近任务后可由无障碍增强或下一次闹钟恢复；厂商系统仍可能延迟或阻止恢复，因此该能力不作绝对存活保证。
- 用户“强行停止”会进入 Android stopped state，必须重新打开应用，普通应用不能绕过。
```

Keep the existing exact-alarm, foreground-notification, boot/time-change, Doze, and HyperOS battery guidance around these bullets.

- [ ] **Step 2: Record implementation and remaining device checks in `PROGRESS.md`**

Add to “已完成”:

```markdown
- [x] 可选无障碍后台增强：最小事件范围、无界面内容读取、最近任务划掉后即时对账、未授权时无功能退化。
```

Add to “自动化验证”:

```markdown
- [x] 后台增强仅在监测开启时恢复、精确闹钟失败的受限回退、动作映射去重和可选权限呈现。
```

Add to “仍需验收” without marking it complete until a real device passes:

```markdown
- [ ] HyperOS 2 / Android 15：开启后台增强后，在监测窗口内划掉最近任务，验证前台通知保持或恢复且下一次检测继续。
- [ ] HyperOS 2 / Android 15：非监测窗口划掉最近任务不常驻通知，但下一窗口仍能启动。
- [ ] 验证睡眠监测关闭但无障碍开启时，划掉最近任务不会启动监测。
```

Update the document date to `2026-08-26`.

- [ ] **Step 3: Run the complete JVM test suite**

```bash
sh gradlew testDebugUnitTest
```

Expected: all domain and new accessibility tests pass with no failures.

- [ ] **Step 4: Compile instrumentation tests and build the Debug APK**

```bash
sh gradlew compileDebugAndroidTestSources assembleDebug
```

Expected: both tasks succeed and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Run Android Lint**

```bash
sh gradlew lintDebug
```

Expected: build succeeds with zero lint errors. Inspect `app/build/reports/lint-results-debug.html` if warnings need explanation.

- [ ] **Step 6: Run connected instrumentation when a device is available**

Check:

```bash
adb devices
```

If at least one authorized device is listed, run:

```bash
sh gradlew connectedDebugAndroidTest
```

Expected: `AccessibilityServiceDeclarationTest` and existing instrumentation tests pass. If no authorized device is available, report this verification as pending rather than marking it successful.

- [ ] **Step 7: Perform HyperOS manual acceptance**

On HyperOS 2 / Android 15:

1. Install the Debug APK and grant notification, overlay, exact-alarm, and battery-unrestricted settings.
2. Open “设置 → 权限引导 → 后台增强（无障碍服务）”, confirm the disclosure, and enable SleepWatch in system accessibility settings.
3. Adjust the monitoring window so the current time is active, enable monitoring, and confirm the ongoing monitoring notification.
4. Swipe SleepWatch from Recents. Within a few seconds, confirm the notification remains or returns; leave the phone until the next configured check and verify a new check is persisted.
5. Move the monitoring window outside the current time, swipe from Recents, and confirm no monitoring notification remains while the next window alarm is still scheduled.
6. Disable sleep monitoring while leaving accessibility enabled, swipe from Recents, and confirm no monitoring notification or session starts.
7. Disable the accessibility enhancement, re-enable monitoring, and verify the original next-alarm recovery still works.
8. Force-stop SleepWatch in system settings and confirm it does not restart until manually opened.

- [ ] **Step 8: Commit documentation after automated checks pass**

```bash
git add docs/REQUIREMENTS.md docs/PROGRESS.md
git commit -m "docs: document accessibility background recovery"
```

- [ ] **Step 9: Review the final diff and finish the development branch**

Run:

```bash
git status --short
git diff --check HEAD~7..HEAD
git log --oneline --decorate -8
```

Expected: no unintended files are staged or modified; `.superpowers/` remains untouched if it is still present. Then invoke `superpowers:finishing-a-development-branch` to present merge, PR, or cleanup options.

## Requirement coverage checklist

- Optional rather than required: Task 5 factory and `allRequiredGranted()` behavior.
- Real system authorization state: Task 4 checker plus Task 5/6 resume refresh.
- Recovery after Recents removal: Task 3 `onTaskRemoved()` to Task 2 reconcile alarm.
- Existing architecture remains authoritative: Task 2 maps to `MonitorService.ACTION_RECONCILE`; no monitoring logic enters the accessibility service.
- Privacy minimization: Task 3 package-scoped event type, no content retrieval, no gestures, empty event handler.
- Exact-alarm uniqueness and preservation: Task 2 dedicated action/request code without adding it to schedule reconciliation lists.
- Restricted fallback: Task 1 exact-alarm `SecurityException` path requires battery unrestricted before direct start.
- No recovery when monitoring is disabled: Task 1 coordinator guard and tests.
- Strong-stop limitation and vendor caveat: Task 7 documentation and manual checks.
- Automated and device verification: Tasks 1–6 focused tests plus Task 7 full suite, lint, instrumentation, and HyperOS acceptance.
