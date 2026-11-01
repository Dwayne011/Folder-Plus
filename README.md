# Widgets Plus

A widget pack for Android: drop a widget on the home screen and long-press it
for its settings. The app icon opens the same folder screens (and makes the app
easy to uninstall). Works on any launcher that supports widgets.

- **Folder** widgets, inspired by Action Launcher's "Covers": a folder that
  **launches an app when tapped** and still opens the full folder through a
  second gesture.
- **Agenda** widget: a full-width card stack of your upcoming days, driven by
  the phone's calendars.

## Features

| Feature | How it works |
| --- | --- |
| Folder widget look | Agenda style: the agenda's see-through plate, 8dp border and pale panel, with a mosaic of the folder's apps (their own icons) that fills whatever size the widget is: a 2×2 mini mosaic on a single cell, otherwise as many 34dp icons as fit, with "+N" for the rest. The folder's name shows only in the open folder. |
| Taps | **Bigger than one cell**: tap an app to open it; tap "+N", a gap or **double tap** to open the folder. **Single cell**: the folder's own single tap (launch its *default app*, open the folder or show its widget) and optional double tap; a small corner badge opens the folder if neither does. |
| Show a widget | Each folder can host one app widget from any installed app (like Action Launcher "Shutters"). The **Show widget** action opens it live in the popup; the popup toggles between the widget and the app grid. The first time, Android asks you to allow Widgets Plus to display widgets. |
| Shortcuts | **Add shortcut** (Apps & categories) lists the shortcuts installed apps offer through Android's shortcut picker, grouped by app and searchable, kept apart from the app list. The chosen shortcut sits in the folder like an app, with its own name and icon. Shortcuts already on the home screen can't be moved in: Android gives them only to the home-screen app. Apps that hand shortcuts only to the home-screen app, or shortcuts this app isn't allowed to open, are refused with an explanation. Shortcuts are kept in the app's settings (covered by Google backup) and in JSON exports. |
| Categories | Folders contain categories (tabs inside the open folder). Each category can override the folder's sort order. The Categories page (from Apps & categories) reorders every tab, "All" included, and holds the switch that hides "All" (apps without a category then get an "Other" tab, last). |
| Widget size | Four widget providers appear in the launcher picker (1×1, 1×2, 2×2, 4×1), every one of them resizable. The folder's *Widget size* setting picks which one "Add to home screen" uses, and the mosaic adapts to the space the launcher gives it. |
| Sorting | Manual, name A–Z / Z–A, most used, recently used, recently added. For a manual order, press and hold an app in the open folder and drag it; in a category tab this rearranges that category's apps. In Apps & categories, drag an app by its handle, icon or name (or press and hold anywhere on its row): a buzz when it's picked up, a tick as it passes each app, and the list scrolls when you drag near its top or bottom edge. Dragging in a folder sorted another way switches it to Manual. |
| Lock | Per folder: off, PIN, biometrics, or PIN **or** biometrics. One app-wide PIN, stored as a salted PBKDF2 hash. Biometrics use the system `BiometricPrompt`. |
| Agenda widget | Full width (5×3, resizable to 5×4): a see-through plate with an 8dp border holding a card stack (Android `StackView`), one card per day. StackView keeps the next days' cards just behind the front one; on Android 12+ the stack is made about four card-heights tall so those cards wait above the widget, out of sight (Android 8 to 11 fall back to opaque cards on a solid plate). Swipe down for the next day, up for the previous one, anywhere on the widget except the bottom border strip. The date card has a month pill at the top, which opens a "Next 30 days" sheet (scrolls by finger or a hold-and-drag scrubber; tap a day to show it on the widget, an event to open it), and a "Go to Today" pill at the bottom of every other day's card that jumps back to today. The widget holds 30 days with the date on the left and compact event rows (time, title, location) on the right. The launcher animates the swipe between days. Tap the date to open Calendar on that day, tap an event to open it, tap **+N more** for a centred day sheet that lists every event with time range, location and description and is swiped down to close; in the sheet the location chip opens a meeting link or searches Maps. Long-press → settings chooses which calendars are shown. Material You colours, blur behind the sheet on Android 12+. |
| Connected devices widget | Agenda style (the agenda's see-through plate and colours, 8dp border), the same width as the agenda: the battery of each connected Bluetooth device that reports one, up to three side by side; one device sits beside this phone, and with nothing connected only this phone is shown. This phone, earbuds, headphones, watches, keyboards, mice, game controllers and speakers each get a two-tone drawing with their level and a bar; any other device gets a ring with its icon. What a device is comes from its name first (common speakers such as Ultimate Ears BOOM, JBL, Bose SoundLink, Sonos, Marshall, Beats Pill and Sony SRS/ULT; Sony WH- headphones and WF-/LinkBuds earbuds), then the type it announces over Bluetooth; the "Audio device type" chosen in Android's Bluetooth settings can't be read by apps. If one is still wrong, the widget's settings list your paired devices under Device types: tap one to choose what it is (or Automatic). The choice is kept for that device (by its Bluetooth address) and included in backup. At 15% or less the level turns red. Android shares one level per device (`BluetoothDevice.getBatteryLevel()`, a system API allowed for apps on Android 12–16, plus the `BATTERY_LEVEL_CHANGED` broadcast); separate earbud left/right/case levels need a privileged permission, and watches and tags only appear if they report a level. Updates on connect, disconnect and battery broadcasts, and every 15 minutes for this phone. Needs the Nearby devices permission (asked when the widget is added). |
| Backup | Android's automatic Google-account backup covers the app's data (folders, categories, PIN). The Folders screen's menu (folder settings → back) also exports/imports a plain JSON backup you can keep in Drive; backups written under the old "Folder Plus" name still import. Home-screen widget links and hosted widget ids are device-specific and are re-linked after a restore. |
| Material You | Dynamic colours on Android 12+. Widget colours are resources in the layouts (rings, bars and drawings are white shapes tinted there), so they follow the phone's theme and wallpaper colours as soon as they change. |

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

Everything starts from the launcher's widget picker, under **Widgets Plus**;
the app icon is a shortcut to the same folder settings.

1. Drop a **Folder** widget (1×1, 2×2 or 4×1; all resizable). The launcher opens
   the setup screen: pick an existing folder or create a new one, then add apps
   and, optionally, categories.
2. Pick a default app and choose what single tap / double tap do.
3. Long-press the widget and tap the launcher's **settings** button to come
   back to that folder's settings at any time. Every widget showing the same
   folder updates when you edit it; **Add to home screen** in the settings
   places an additional one.
4. In a folder's app list, use the **Select** action to move or remove several
   apps at once.
5. Optional: turn on a PIN or biometric lock in the folder's Security section.
6. Drop the **Agenda** widget. The first tap asks for calendar access; long-press
   → settings picks which calendars it shows.

## Known limitations / to verify on device

- Double tap relies on the launcher delivering widget clicks promptly; the
  350 ms window may need tuning per launcher.
- "Widget size" is a request: Android launchers decide the final cell size, so
  the widget adapts to whatever size it is given.
