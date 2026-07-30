# Compact Material 3 Time Picker

## Goal

Replace the custom wheel used for monitoring start time, monitoring end time,
and target bedtime with a reliable 24-hour Material 3 dial. The picker must
persist boundary values such as `00:00` and remain usable on narrow portrait
phones and short landscape screens.

## Design

- Use `rememberTimePickerState` with `is24Hour = true`.
- Keep the existing dialog title and Chinese confirm/cancel actions.
- Constrain the dialog to a maximum width of 320dp.
- Put the dial in a 270dp-high box and scale the visual picker to 78% so it
  does not dominate a phone screen.
- Clamp incoming initial hour/minute values to valid ranges before creating the
  state; pass `state.hour` and `state.minute` unchanged on confirmation so zero
  remains a valid value.

## Scope and validation

Only the three time-setting dialogs and their shared picker implementation are
changed. Build the debug APK after the change and verify the resulting dialog
layout compiles for the existing minSdk/Compose versions.
