# HyperOS Battery Settings Navigation Design

## Problem

SleepWatch exposes two battery-setting entry points:

- the "电池优化白名单" permission card;
- the "前往电池设置" button in the HyperOS guidance card.

They currently use different navigation logic. The permission card opens
`Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, which resolves to
Android's generic app battery usage page on the connected HyperOS 3 / Android 16
device. The HyperOS guidance button targets
`com.miui.powerkeeper.ui.HiddenAppsConfigActivity`, which is not present on that
device.

Device package resolution confirms that
`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with
`package:com.sleepwatch` resolves to HyperOS
`com.miui.powercenter.legacypowerrank.PowerDetailActivity`, the per-app power
saving policy page the user expects.

## Scope

Unify both entry points behind one battery-settings navigator. Preserve the
existing permission detection, permission importance, setup UI, and non-HyperOS
fallback behavior.

No new manifest permission will be requested. In particular, the design does
not add `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Navigation Design

`HyperOSHelper.openBatterySettings(context)` becomes the only battery-settings
navigation entry point.

It uses this ordered fallback chain:

1. On HyperOS, create an implicit
   `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent with
   `package:<applicationId>`. Launch it only when it resolves to a Xiaomi or MIUI
   system package.
2. Try the legacy
   `com.miui.powerkeeper.ui.HiddenAppsConfigActivity` component with the current
   package name and application label extras for older MIUI/HyperOS releases.
3. Fall back to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`.
4. If even the generic battery optimization page is unavailable, fall back to
   `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for SleepWatch.

Each candidate is resolved before launch. Launch failures, including
`ActivityNotFoundException` and `SecurityException`, advance to the next
candidate instead of terminating the app.

## Entry-Point Unification

`SetupViewModel.requestBatteryOptimization()` delegates to
`HyperOSHelper.openBatterySettings(context)`.

The HyperOS guidance card already calls the same helper, so both buttons use the
identical navigation chain without duplicating Intent construction.

## Permission State

Permission state remains based on:

```kotlin
PowerManager.isIgnoringBatteryOptimizations(context.packageName)
```

`SetupScreen` already rechecks permissions on `ON_RESUME`, so returning from the
system settings page refreshes the displayed status without additional state or
callbacks.

## Verification

- Add unit coverage for candidate ordering and Xiaomi/MIUI resolver acceptance
  without starting external activities.
- Run the JVM test suite, Debug and AndroidTest compilation, and Debug Lint.
- Install the Debug APK on the connected HyperOS 3 / Android 16 device.
- Open each battery-setting entry point and confirm both reach
  `com.miui.securitycenter/com.miui.powercenter.legacypowerrank.PowerDetailActivity`.
- Return to SleepWatch and confirm the existing battery allowlist status is
  refreshed and remains correctly detected.
- Confirm fallback candidates do not crash when a vendor component is absent.
