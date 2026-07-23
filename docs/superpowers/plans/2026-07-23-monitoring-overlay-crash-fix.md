# Monitoring Overlay Crash Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent monitoring from crashing when its Compose alert overlay is attached directly to `WindowManager`.

**Architecture:** Extend the overlay's existing service-owned lifecycle object so it also owns a restored `SavedStateRegistry`. Install both owners on each `ComposeView` before window attachment, while preserving the current monitoring, overlay, fallback-notification, and cleanup flows.

**Tech Stack:** Kotlin, Android foreground services, Jetpack Compose, AndroidX Lifecycle 2.8.7, AndroidX SavedState 1.2.1, AndroidJUnit4, Gradle 8.9.

---

## File Map

- Create `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt`: reproduce the real `WindowManager` attachment and catch missing Compose view-tree owners.
- Modify `app/src/main/java/com/sleepwatch/service/ServiceLifecycleOwner.kt`: own and restore the saved-state registry for a non-Activity Compose host.
- Modify `app/src/main/java/com/sleepwatch/service/AlertOverlayController.kt`: propagate the saved-state owner to the overlay `ComposeView`.

### Task 1: Add the Device Regression Test

**Files:**
- Create: `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt`
- Test: `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt`

- [ ] **Step 1: Write the failing overlay attachment test**

Create `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt` with:

```kotlin
package com.sleepwatch.service

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sleepwatch.ui.alert.AlertInfo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlertOverlayControllerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var controller: AlertOverlayController? = null

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            controller?.hide()
            controller = null
        }
    }

    @Test
    fun show_attachesComposeOverlayWithoutMissingSavedStateOwnerCrash() {
        assumeTrue(Settings.canDrawOverlays(context))

        instrumentation.runOnMainSync {
            controller = AlertOverlayController(context)
            assertTrue(
                controller!!.show(
                    info = AlertInfo(
                        title = "测试提醒",
                        content = "测试悬浮窗生命周期",
                        healthTip = "",
                        level = 1,
                        totalLevels = 1
                    ),
                    onDismiss = {},
                    onSkip = {}
                )
            )
        }
        instrumentation.waitForIdleSync()
    }
}
```

- [ ] **Step 2: Build and install the app and test APKs**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew assembleDebug assembleDebugAndroidTest
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb shell appops set com.sleepwatch SYSTEM_ALERT_WINDOW allow
```

Expected: both Gradle tasks and both installs succeed; `appops` returns without an error.

- [ ] **Step 3: Run the test and verify the current code fails**

Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb logcat -c
/Users/ranran/Library/Android/sdk/platform-tools/adb shell am instrument -w -e class com.sleepwatch.service.AlertOverlayControllerTest com.sleepwatch.test/androidx.test.runner.AndroidJUnitRunner
```

Expected: the instrumentation process fails or crashes, and logcat reports:

```text
java.lang.IllegalStateException: Composed into the View which doesn't propagateViewTreeSavedStateRegistryOwner!
```

### Task 2: Supply the Missing Saved-State Owner

**Files:**
- Modify: `app/src/main/java/com/sleepwatch/service/ServiceLifecycleOwner.kt`
- Modify: `app/src/main/java/com/sleepwatch/service/AlertOverlayController.kt`
- Test: `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt`

- [ ] **Step 1: Extend the service lifecycle owner**

Replace `ServiceLifecycleOwner.kt` with:

```kotlin
package com.sleepwatch.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Independent lifecycle and saved-state owner for overlay windows in a Service.
 * Compose views attached through WindowManager cannot inherit these owners from
 * an Activity, so the overlay must provide them explicitly.
 */
class ServiceLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
}
```

- [ ] **Step 2: Propagate the owner before composing the overlay**

Add this import to `AlertOverlayController.kt`:

```kotlin
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
```

Change the `ComposeView` owner setup to:

```kotlin
val view = ComposeView(context).apply {
    setViewTreeLifecycleOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setContent {
        AlertScreen(
            info = info,
            onDismiss = onDismiss,
            onSkip = onSkip
        )
    }
}
```

- [ ] **Step 3: Rebuild and reinstall the fixed APKs**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew assembleDebug assembleDebugAndroidTest
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb shell appops set com.sleepwatch SYSTEM_ALERT_WINDOW allow
```

Expected: build and installation succeed.

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb logcat -c
/Users/ranran/Library/Android/sdk/platform-tools/adb shell am instrument -w -e class com.sleepwatch.service.AlertOverlayControllerTest com.sleepwatch.test/androidx.test.runner.AndroidJUnitRunner
```

Expected:

```text
OK (1 test)
```

Also run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb logcat -d -v threadtime AndroidRuntime:E MonitorService:E '*:S'
```

Expected: no `FATAL EXCEPTION` and no missing `ViewTreeSavedStateRegistryOwner` exception.

### Task 3: Run Full Verification and Commit

**Files:**
- Verify: `app/src/main/java/com/sleepwatch/service/ServiceLifecycleOwner.kt`
- Verify: `app/src/main/java/com/sleepwatch/service/AlertOverlayController.kt`
- Verify: `app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt`

- [ ] **Step 1: Run the project verification suite**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest assembleDebug lintDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Reinstall the verified APK without clearing user data**

Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb shell am force-stop com.sleepwatch
/Users/ranran/Library/Android/sdk/platform-tools/adb shell monkey -p com.sleepwatch -c android.intent.category.LAUNCHER 1
```

Expected: the existing app data and monitoring setting are retained, `MainActivity` opens, and the app process remains alive. If the configured monitoring window is currently active and the phone is unlocked, the Compose reminder overlay appears without a restart loop.

- [ ] **Step 3: Check the final diff**

Run:

```bash
git diff --check
git status --short
git diff -- app/src/main/java/com/sleepwatch/service/ServiceLifecycleOwner.kt app/src/main/java/com/sleepwatch/service/AlertOverlayController.kt app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt
```

Expected: no whitespace errors and only the three planned files are modified or added.

- [ ] **Step 4: Commit the verified fix**

Run:

```bash
git add app/src/main/java/com/sleepwatch/service/ServiceLifecycleOwner.kt app/src/main/java/com/sleepwatch/service/AlertOverlayController.kt app/src/androidTest/java/com/sleepwatch/service/AlertOverlayControllerTest.kt
git commit -m "fix: provide saved state owner to monitoring overlay"
```

Expected: one commit containing the lifecycle fix and its device regression test.
