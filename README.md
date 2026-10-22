# Driver Hotspot (Android)

Standalone Android app for personal driver shift journaling.

## Features
- User-controlled foreground location logging during a shift (about every 60 seconds).
- Manual trip entry: zone, revenue, costs, battery before/after, notes.
- Net revenue per consumed battery percentage.
- Personal GPS point-density plot and trip history.
- CSV export to the app-specific external files directory.

## Privacy and scope
- No Accessibility service, screen capture, Xanh SM integration, order automation, or mock-location provider.
- Location logging is opt-in and requires Android location permission; Android may require background location permission depending on OS/device policy for uninterrupted tracking.
- Data is stored locally in app preferences; uninstalling the app may remove it. CSV export is available for trip records.
- The current plot visualizes GPS point density, not verified demand or actual trip frequency. Heat intensity by hour/weekday and mapped venue demand require more logged data and a later aggregation enhancement.

## Build
From this directory with JDK 17 and Android SDK installed:
`gradle testDebugUnitTest assembleDebug`
APK: `app/build/outputs/apk/debug/app-debug.apk`
