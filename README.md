# Folder Plus

Smart home-screen folders for Android, inspired by Action Launcher's "Covers":
a folder widget that **launches an app when tapped** and still opens the full
folder through a second gesture. Works on any launcher that supports widgets.

## Features

| Feature | How it works |
| --- | --- |
| Tap to launch | Each folder has a *default app*. A single tap on the widget launches it (or opens the folder, your choice). |
| Second gesture | A **double tap** can run a second action (open folder / launch default app / show widget). A small **corner badge** always opens the folder when the main tap does something else. |
| Show a widget | Each folder can host one app widget from any installed app (like Action Launcher "Shutters"). The **Show widget** action opens it live in the popup; the popup toggles between the widget and the app grid. The first time, Android asks you to allow Folder Plus to display widgets. |
| Categories | Folders contain categories (tabs inside the popup). Each category can override the folder's sort order. |
| Widget size | Three widget providers appear in the launcher picker (1×1, 2×2, 4×1), every one of them resizable. The folder's *Widget size* setting picks which one "Add to home screen" uses, and the widget adapts its layout (icon only → icon + label → quick-launch strip) to the space the launcher gives it. |
| Sorting | Manual (long-press and drag), name A–Z / Z–A, most used, recently used, recently added. |
| Lock | Per folder: off, PIN, biometrics, or PIN **or** biometrics. One app-wide PIN, stored as a salted PBKDF2 hash. Biometrics use the system `BiometricPrompt`. |
| Agenda widget | A second widget in the pack: a frosted 4×3 agenda (resizable to 4×4) that scrolls natively, one row per day for the next two weeks with its date card on the left and up to three events on the right. Material You colours, calendar permission requested from the app's menu. |
| Backup | Android's automatic Google-account backup covers the app's data (folders, categories, PIN). The Folders screen's menu also exports/imports a plain JSON backup you can keep in Drive. Home-screen widget links and hosted widget ids are device-specific and are re-linked after a restore. |
| Material You | Dynamic colours on Android 12+, light/dark widgets. |

### Why double tap and a badge rather than swipe or long press?

Home-screen widgets only receive *click* events. The launcher owns long press
(move/resize) and swipes (page scrolling), so no third-party widget can
implement them. The two gestures that remain are a tap and a timed double tap.

When a folder has a double-tap action, its widget's main tap opens
`WidgetTapActivity`, an invisible foreground activity. A second tap within
about 400 ms either lands on that activity's transparent window or re-fires the
widget's intent into it; both count as a double tap. If nothing arrives, the
single-tap action runs. Doing this inside a foreground activity (rather than a
broadcast receiver) sidesteps Android's background-activity-start
restrictions, which silently block receivers from opening activities on many
launchers. When no double-tap action is set, taps use direct intents with no
delay.

Showing another app's widget is done by hosting it ourselves with
`AppWidgetHost` (`app/.../widget/HostedWidgets.kt`): the folder popup contains
a live `AppWidgetHostView`. Binding a widget needs a one-time permission that
the system asks for through `ACTION_APPWIDGET_BIND`.

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
3. Add a "Folder Plus" widget from your launcher's widget picker and choose
   (or create) the folder it shows, or tap **Add to home screen** in the
   folder's settings. Either way, every widget showing a folder updates when
   you edit that folder; "Add to home screen" always places an additional one.
4. In a folder's app list, use the **Select** action to move or remove several
   apps at once.
5. Optional: turn on a PIN or biometric lock in the folder's Security section.

## Known limitations / to verify on device

- Double tap relies on the launcher delivering widget clicks promptly; the
  350 ms window may need tuning per launcher.
- "Widget size" is a request: Android launchers decide the final cell size, so
  the widget adapts to whatever size it is given.
