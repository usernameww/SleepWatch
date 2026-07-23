# Monitoring Overlay Crash Fix Design

## Problem

Enabling monitoring during an active monitoring window immediately displays the
Compose-based overlay. On the connected HyperOS device, the app repeatedly
crashes on the main thread with:

```text
java.lang.IllegalStateException:
Composed into the View which doesn't propagate ViewTreeSavedStateRegistryOwner
```

`AlertOverlayController` supplies a `LifecycleOwner` to its `ComposeView`, but
the view is attached directly through `WindowManager` and therefore does not
inherit the `SavedStateRegistryOwner` normally provided by an Activity.

## Scope

Keep the current foreground-service, monitoring, alert-selection, and overlay UI
behavior unchanged. Fix only the missing view-tree ownership required to host
Compose safely outside an Activity.

## Design

`ServiceLifecycleOwner` will also implement `SavedStateRegistryOwner`. It will
create a `SavedStateRegistryController`, attach and restore it before entering
the `CREATED` lifecycle state, and continue to expose the existing `start()` and
`destroy()` lifecycle transitions.

`AlertOverlayController` will install the same owner as both the
`ViewTreeLifecycleOwner` and `ViewTreeSavedStateRegistryOwner` on every new
`ComposeView` before the view is added to `WindowManager`. Overlay removal will
continue to destroy the owner and clear all retained references.

No Activity launch, native-view rewrite, monitoring-state change, database
change, or permission change is included.

## Data and Control Flow

1. `MonitorService` decides that an alert should be shown.
2. `AlertOverlayController.show()` creates a fresh `ServiceLifecycleOwner`.
3. The controller creates the `ComposeView` and installs both required view-tree
   owners before attachment.
4. `WindowManager.addView()` attaches and composes the alert without the missing
   owner exception.
5. Dismissal, skip, screen-off, or service shutdown removes the view and moves
   its owner to `DESTROYED`.

## Error Handling

The existing overlay creation guard remains in place: synchronous overlay
creation failures return `false`, allowing the caller to fall back to a
notification. The lifecycle fix prevents the previously asynchronous
`AndroidComposeView.onAttachedToWindow()` failure that escaped that guard and
terminated the process.

## Verification

- Run `testDebugUnitTest`, `assembleDebug`, and `lintDebug`.
- Install the Debug APK on the connected HyperOS device without clearing app
  data.
- Clear logcat, enable monitoring while the device is unlocked and within the
  active window, and confirm the overlay appears.
- Dismiss the overlay and enable/disable monitoring repeatedly.
- Confirm logcat contains no `FATAL EXCEPTION`, missing
  `ViewTreeSavedStateRegistryOwner`, or `com.sleepwatch` process restart loop.
