# HyperOS Battery Settings Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make both battery-setting entry points open the current app's HyperOS power-saving policy details page with safe fallbacks.

**Architecture:** A pure Kotlin route planner owns candidate ordering and trusted OEM-handler rules. `HyperOSHelper` converts each route into an Android `Intent`, resolves it before launch, and advances through the ordered fallback chain on missing or failing activities; `SetupViewModel` delegates its permission-card action to that helper.

**Tech Stack:** Kotlin 2.0.21, Android Settings Intents, Android PackageManager, JUnit 4, Gradle 8.9.

---

## File Map

- Create `app/src/main/java/com/sleepwatch/util/BatterySettingsRoutePlanner.kt`: define route ordering and trusted HyperOS handler rules without Android runtime dependencies.
- Create `app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt`: cover HyperOS/non-HyperOS ordering and OEM trust checks.
- Modify `app/src/main/java/com/sleepwatch/util/HyperOSHelper.kt`: build, resolve, and launch the ordered battery-settings candidates.
- Modify `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`: delegate the permission-card action to the shared helper.

### Task 1: Define and Test Battery Settings Route Policy

**Files:**
- Create: `app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt`
- Create: `app/src/main/java/com/sleepwatch/util/BatterySettingsRoutePlanner.kt`

- [ ] **Step 1: Write the failing route-policy tests**

Create `app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt`:

```kotlin
package com.sleepwatch.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySettingsRoutePlannerTest {
    @Test
    fun hyperOsRoutes_prioritizeAppDetailThenFallbacks() {
        assertEquals(
            listOf(
                BatterySettingsRoute.HYPER_OS_APP_DETAIL,
                BatterySettingsRoute.LEGACY_HYPER_OS_APP_DETAIL,
                BatterySettingsRoute.GENERIC_BATTERY_OPTIMIZATION,
                BatterySettingsRoute.APPLICATION_DETAILS
            ),
            BatterySettingsRoutePlanner.routes(isHyperOS = true)
        )
    }

    @Test
    fun nonHyperOsRoutes_skipVendorSpecificEntries() {
        assertEquals(
            listOf(
                BatterySettingsRoute.GENERIC_BATTERY_OPTIMIZATION,
                BatterySettingsRoute.APPLICATION_DETAILS
            ),
            BatterySettingsRoutePlanner.routes(isHyperOS = false)
        )
    }

    @Test
    fun trustedHandler_requiresSystemMiuiOrXiaomiPackage() {
        assertTrue(
            BatterySettingsRoutePlanner.isTrustedHyperOsHandler(
                packageName = "com.miui.securitycenter",
                isSystemApp = true
            )
        )
        assertTrue(
            BatterySettingsRoutePlanner.isTrustedHyperOsHandler(
                packageName = "com.xiaomi.misettings",
                isSystemApp = true
            )
        )
        assertFalse(
            BatterySettingsRoutePlanner.isTrustedHyperOsHandler(
                packageName = "com.miui.securitycenter",
                isSystemApp = false
            )
        )
        assertFalse(
            BatterySettingsRoutePlanner.isTrustedHyperOsHandler(
                packageName = "example.com.miui.fake",
                isSystemApp = true
            )
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest --tests com.sleepwatch.util.BatterySettingsRoutePlannerTest
```

Expected: Kotlin test compilation fails because `BatterySettingsRoute`,
`BatterySettingsRoutePlanner.routes`, and
`BatterySettingsRoutePlanner.isTrustedHyperOsHandler` do not exist.

- [ ] **Step 3: Implement the minimal pure route planner**

Create `app/src/main/java/com/sleepwatch/util/BatterySettingsRoutePlanner.kt`:

```kotlin
package com.sleepwatch.util

internal enum class BatterySettingsRoute {
    HYPER_OS_APP_DETAIL,
    LEGACY_HYPER_OS_APP_DETAIL,
    GENERIC_BATTERY_OPTIMIZATION,
    APPLICATION_DETAILS
}

internal object BatterySettingsRoutePlanner {
    fun routes(isHyperOS: Boolean): List<BatterySettingsRoute> =
        if (isHyperOS) {
            listOf(
                BatterySettingsRoute.HYPER_OS_APP_DETAIL,
                BatterySettingsRoute.LEGACY_HYPER_OS_APP_DETAIL,
                BatterySettingsRoute.GENERIC_BATTERY_OPTIMIZATION,
                BatterySettingsRoute.APPLICATION_DETAILS
            )
        } else {
            listOf(
                BatterySettingsRoute.GENERIC_BATTERY_OPTIMIZATION,
                BatterySettingsRoute.APPLICATION_DETAILS
            )
        }

    fun isTrustedHyperOsHandler(
        packageName: String,
        isSystemApp: Boolean
    ): Boolean =
        isSystemApp &&
            (packageName.startsWith("com.miui.") || packageName.startsWith("com.xiaomi."))
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest --tests com.sleepwatch.util.BatterySettingsRoutePlannerTest
```

Expected:

```text
BUILD SUCCESSFUL
```

### Task 2: Centralize Intent Resolution and Entry-Point Delegation

**Files:**
- Modify: `app/src/main/java/com/sleepwatch/util/HyperOSHelper.kt`
- Modify: `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`
- Test: `app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt`

- [ ] **Step 1: Replace the battery navigation implementation**

Replace `app/src/main/java/com/sleepwatch/util/HyperOSHelper.kt` with:

```kotlin
package com.sleepwatch.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object HyperOSHelper {

    fun isHyperOS(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.miui.ui.version.name") as? String
            version != null && version.isNotEmpty()
        } catch (_: Exception) {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        }
    }

    fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun openBatterySettings(context: Context) {
        BatterySettingsRoutePlanner.routes(isHyperOS()).firstOrNull { route ->
            val intent = batterySettingsIntent(context, route)
            val resolvedActivity = context.packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            ) ?: return@firstOrNull false

            if (route == BatterySettingsRoute.HYPER_OS_APP_DETAIL) {
                val applicationInfo = resolvedActivity.activityInfo.applicationInfo
                val isSystemApp = applicationInfo.flags and (
                    ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                ) != 0
                if (!BatterySettingsRoutePlanner.isTrustedHyperOsHandler(
                        packageName = resolvedActivity.activityInfo.packageName,
                        isSystemApp = isSystemApp
                    )
                ) {
                    return@firstOrNull false
                }
            }

            runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }

    private fun batterySettingsIntent(
        context: Context,
        route: BatterySettingsRoute
    ): Intent = when (route) {
        BatterySettingsRoute.HYPER_OS_APP_DETAIL ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        BatterySettingsRoute.LEGACY_HYPER_OS_APP_DETAIL ->
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra(
                    "package_label",
                    context.applicationInfo.loadLabel(context.packageManager).toString()
                )
            }
        BatterySettingsRoute.GENERIC_BATTERY_OPTIMIZATION ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        BatterySettingsRoute.APPLICATION_DETAILS ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun getAutoStartGuideText(): String {
        return if (isHyperOS()) {
            "请在 设置 → 应用管理 → SleepWatch → 自启动 中开启自启动权限"
        } else {
            "请在系统设置中允许 SleepWatch 自启动"
        }
    }

    fun getBackgroundGuideText(): String {
        return if (isHyperOS()) {
            "请在最近任务列表中下拉锁定 SleepWatch，并将省电策略设为\"无限制\""
        } else {
            "请在系统设置中关闭 SleepWatch 的电池优化"
        }
    }
}
```

- [ ] **Step 2: Delegate the permission-card action**

Add this import to `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`:

```kotlin
import com.sleepwatch.util.HyperOSHelper
```

Replace `requestBatteryOptimization()` with:

```kotlin
private fun requestBatteryOptimization() {
    HyperOSHelper.openBatterySettings(context)
}
```

- [ ] **Step 3: Run focused and full JVM tests**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest --tests com.sleepwatch.util.BatterySettingsRoutePlannerTest
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the tested navigation implementation**

Run:

```bash
git add app/src/main/java/com/sleepwatch/util/BatterySettingsRoutePlanner.kt app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt app/src/main/java/com/sleepwatch/util/HyperOSHelper.kt app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt
git commit -m "fix: open HyperOS app battery policy details"
```

Expected: one commit containing the route policy, tests, shared navigator, and permission-card delegation.

### Task 3: Build and Verify on HyperOS

**Files:**
- Verify: `app/src/main/java/com/sleepwatch/util/BatterySettingsRoutePlanner.kt`
- Verify: `app/src/main/java/com/sleepwatch/util/HyperOSHelper.kt`
- Verify: `app/src/main/java/com/sleepwatch/ui/setup/SetupViewModel.kt`
- Verify: `app/src/test/java/com/sleepwatch/util/BatterySettingsRoutePlannerTest.kt`

- [ ] **Step 1: Run the complete build verification**

Run:

```bash
ANDROID_HOME=/Users/ranran/Library/Android/sdk sh gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: Install the Debug APK without clearing user data**

Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
/Users/ranran/Library/Android/sdk/platform-tools/adb shell am force-stop com.sleepwatch
/Users/ranran/Library/Android/sdk/platform-tools/adb shell am start -W -n com.sleepwatch/.MainActivity
```

Expected: installation and launch succeed while preserving the existing battery allowlist state.

- [ ] **Step 3: Verify the HyperOS resolver target**

Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb shell cmd package resolve-activity --brief -a android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -d package:com.sleepwatch
```

Expected output includes:

```text
com.miui.securitycenter/com.miui.powercenter.legacypowerrank.PowerDetailActivity
```

- [ ] **Step 4: Verify both app entry points**

On the connected unlocked device:

1. Open SleepWatch's permission guide and tap "前往电池设置".
2. Run:

```bash
/Users/ranran/Library/Android/sdk/platform-tools/adb shell dumpsys activity activities | rg -m 1 'topResumedActivity'
```

Expected output includes:

```text
com.miui.securitycenter/com.miui.powercenter.legacypowerrank.PowerDetailActivity
```

3. Return to SleepWatch. If "电池优化白名单" is already shown as authorized,
   set the HyperOS policy to a restricted mode temporarily so the permission
   card's "授权" button becomes visible.
4. Tap the permission card's "授权" button and run the same `dumpsys` command.
5. Confirm it reaches the same `PowerDetailActivity`, restore "无限制", return to
   SleepWatch, and confirm the card refreshes to "已授权".

- [ ] **Step 5: Check the final repository state**

Run:

```bash
git diff --check
git status --short
git log -3 --oneline --decorate
```

Expected: no whitespace errors, no uncommitted source changes, and the navigation fix commit is at `HEAD`.
