# Folder Plus

Smart home-screen folders for Android, inspired by Action Launcher's "Covers":
a folder widget that **launches an app when tapped** and still opens the full
folder through a second gesture. Works on any launcher that supports widgets.

## Features

| Feature | How it works |
| --- | --- |
| Tap to launch | Each folder has a *default app*. A single tap on the widget launches it (or opens the folder, your choice). |
| Second gesture | A **double tap** can run a second action (open folder / launch default app). A small **corner badge** always opens the folder when the main tap launches an app. |
| Categories | Folders contain categories (tabs inside the popup). Each category can override the folder's sort order. |
| Widget size | Three widget providers appear in the launcher picker (1×1, 2×2, 4×1), every one of them resizable. The folder's *Widget size* setting picks which one "Add to home screen" uses, and the widget adapts its layout (icon only → icon + label → quick-launch strip) to the space the launcher gives it. |
| Sorting | Manual (long-press and drag), name A–Z / Z–A, most used, recently used, recently added. |
| Lock | Per folder: off, PIN, biometrics, or PIN **or** biometrics. One app-wide PIN, stored as a salted PBKDF2 hash. Biometrics use the system `BiometricPrompt`. |
| Material You | Dynamic colours on Android 12+, light/dark widgets. |

### Why double tap and a badge rather than swipe or long press?

Home-screen widgets only receive *click* events. The launcher owns long press
(move/resize) and swipes (page scrolling), so no third-party widget can
implement them. The two gestures that remain are a tap and a timed double tap,
which `WidgetTapReceiver` tells apart using a 350 ms window
(`core/.../gesture/TapResolver.kt`). When a double-tap action is configured a
single tap is therefore delayed by that window; when it is not, taps fire
immediately with no delay.

Launching *another app's widget* from a folder is likewise outside what a widget
can do; that would require the app to host widgets itself (`AppWidgetHost`),
which is a possible later addition inside the folder popup.

## Project layout

```
core/   Pure Kotlin/JVM: models, sorting, PIN hashing, tap-gesture resolver,
        widget layout policy. Unit-tested with JUnit (`./gradlew :core:test`).
app/    Android app (Kotlin, Jetpack Compose, Room, androidx.biometric).
  data/      Room entities, DAOs, FolderRepository
  apps/      Installed-app discovery and icon cache
  widget/    AppWidgetProviders, RemoteViews renderer, tap receiver, pinning, config
  ui/        Folder list, editor, app management (drag reorder), picker,
             folder popup + lock gate
  security/  LockManager (PIN storage + biometric availability)
```

`settings.gradle.kts` only includes `:app` when an Android SDK is available
(`local.properties`, `ANDROID_HOME` or `ANDROID_SDK_ROOT`), so `:core` can be
built and tested on machines without one. Pass `-PincludeApp=true` to force it.

## Building

Requirements: JDK 17+, Android SDK with platform 35 (Android Studio installs it).

```bash
./gradlew :core:test            # logic tests, no SDK needed
./gradlew :app:assembleDebug    # debug APK → app/build/outputs/apk/debug/
```

Minimum Android version: 8.0 (API 26). Target: Android 15 (API 35).

## Using it

1. Open Folder Plus, create a folder, add apps, optionally add categories.
2. Pick a default app and choose what single tap / double tap do.
3. Tap **Add to home screen** (or add a "Folder Plus" widget from your
   launcher's widget picker and choose the folder).
4. Optional: turn on a PIN or biometric lock in the folder's Security section.

## Known limitations / to verify on device

- Double tap relies on the launcher delivering widget clicks promptly; the
  350 ms window may need tuning per launcher.
- Starting the folder popup from the tap receiver depends on the launcher
  granting background-activity-start permission to widget click intents (stock
  launchers do). If a launcher does not, set double tap to *Nothing*: single
  taps then use direct activity intents, which always work.
- "Widget size" is a request: Android launchers decide the final cell size, so
  the widget adapts to whatever size it is given.
