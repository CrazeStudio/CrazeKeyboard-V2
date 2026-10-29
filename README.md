# CrazeKeyboard

CrazeKeyboard is an independent, offline-first Android keyboard project by CrazeStudio.

## Current build

The first clean-room implementation provides:
- An Android settings/launcher screen with keyboard setup shortcuts
- A working custom QWERTY input method with letters, shift, backspace, space, enter, and a symbols layer
- Local-only typing; no network permission or cloud dependency
- Password-field safeguards that disable suggestions (suggestions are not implemented yet)

## Build

Open this repository in Android Studio with Android SDK 35 and JDK 17. Sync the Gradle project and run the `app` configuration. The GitHub Actions workflow runs `assembleDebug`.

## Install and enable

1. Install the debug APK on an Android device.
2. Open CrazeKeyboard and tap **Enable keyboard**.
3. Select **CrazeKeyboard** in Android's input-method settings.
4. Tap **Choose keyboard** and select it as the current input method.

## Privacy

The app requests no Internet permission. Text is sent only to the focused editor through Android's `InputConnection`. This initial build does not include cloud sync, telemetry, or online suggestions.

## Project direction

The implementation will grow screen-by-screen from the feature plan in `replica/recon.md`. CrazeKeyboard is independent and is not affiliated with Google or Gboard.

## FUTO Keyboard source snapshot

An unmodified source-tree snapshot of FUTO Keyboard is kept in `futo-keyboard/` as a separate Android project. It is not wired into the root CrazeKeyboard build and does not replace the existing app. See [`FUTO_KEYBOARD_IMPORT.md`](FUTO_KEYBOARD_IMPORT.md) for upstream revision and license notes.
