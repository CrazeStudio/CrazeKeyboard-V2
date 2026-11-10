# CrazeKeyboard

CrazeKeyboard is an offline-first Android keyboard by CrazeStudio (`com.crazestudio.crazekeyboard`).

## Current features

- Responsive keyboard panel with a fixed, preference-aware height and one-handed layouts.
- English QWERTY layout, optional number row, symbols, shift/caps lock, and editor-aware action key.
- Local suggestions and on-device learned words; no remote dictionary or network service.
- Emoji picker with category navigation and recent emoji.
- Local clipboard history, accent long-press alternatives, repeat backspace, auto-capitalization, and double-space period.
- Light, dark, and Craze themes; adjustable key height and haptics.
- Scalable Android VectorDrawable icons for keyboard controls instead of emoji glyphs.
- Password and no-suggestions editor types suppress suggestions.

## Offline and privacy

The keyboard has no Internet permission and does not send typed text to a server. Suggestions and learned words are stored locally. Voice dictation is intentionally not included in this offline build because Android's speech recognizer may use an external service.

## Build

Open the project in Android Studio and run the app on an Android device. Enable CrazeKeyboard in system keyboard settings, then select it as the active input method. GitHub Actions builds the debug APK on pushes to `main`.

## Current scope

Glide typing, multilingual layouts, GIF/sticker search, translation, and a larger downloadable dictionary are not implemented. The built-in English word list is local and intentionally lightweight.
