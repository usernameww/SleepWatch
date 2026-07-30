# Compact Cupertino-style Wheel Time Picker

## Goal

Replace the oversized dial with a compact two-column wheel for monitoring start
time, monitoring end time, and target bedtime. The picker must persist boundary
values such as `00:00` and remain usable on narrow portrait phones and short
landscape screens.

## Design

- Use two compact, five-row lazy wheels: hours `00`–`23` and minutes `00`–`59`.
- Keep the existing dialog title and Chinese confirm/cancel actions.
- Constrain the dialog to a maximum width of 320dp and each wheel to 78dp.
- Highlight only the centered row with a light rounded surface so the picker
  stays visually light on a phone.
- Determine the selected row from the actual pixel center and snap only when
  needed; tapping a row updates it immediately, including index 0.
- Clamp incoming hour/minute values to valid ranges before displaying them and
  write the selected integers unchanged on confirmation so zero remains valid.

## Scope and validation

Only the three time-setting dialogs and their shared picker implementation are
changed. Build the debug APK after the change and verify the resulting dialog
layout compiles for the existing minSdk/Compose versions.
