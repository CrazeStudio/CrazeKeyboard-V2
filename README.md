# CrazeKeyboard V2

CrazeKeyboard is a native Android input-method project inspired by the CrazeKeyboard Figma concept.

## Implemented in this build

- Dark setup/home screen with keyboard enable and picker shortcuts
- Four theme choices: Midnight Glow, Arctic Glass, Berry Pop, Forest Calm
- Typing settings for suggestions, autocorrection preference, swipe-typing preference, haptics and key sounds
- Keyboard UI with letter and symbol layouts, shift, backspace, space, punctuation and editor action/enter
- Basic Bengali character layout toggle
- Local preferences and no network permission
- GitHub Actions debug APK build and artifact upload

## Current limitations

This is a native starter implementation, not a complete Gboard replacement. Autocorrection is currently a saved preference only; no dictionary-based correction engine is included. Swipe typing has a saved preference but no gesture decoder. The suggestion strip is a lightweight local UI and is not a language-model prediction engine. Emoji support is currently a quick emoji insert, not a full emoji picker. Bengali input currently provides a basic character layout, not phonetic transliteration or a complete Bengali keyboard. GIF and advanced toolbar tools are not integrated with online providers.

## Build

GitHub Actions runs Gradle assembleDebug with JDK 17 and Gradle 8.11.1. Download the CrazeKeyboard-debug artifact from the Actions run after the workflow completes.

## Privacy

The app does not declare Internet permission in this build. Keyboard input is processed through Android's InputConnection; users should only enable keyboards they trust.
