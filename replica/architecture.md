# Architecture

- Platform: native Android.
- Language: Java 17.
- UI: Android Views and a custom-drawn keyboard surface.
- IME: InputMethodService, EditorInfo and InputConnection.
- State: in-memory shift/symbol layer for the initial vertical slice.
- Network: none; manifest intentionally omits Internet permission.
- Build: Gradle 8.9 via CI, Android Gradle Plugin 8.6.1, compile SDK 35, min SDK 23.

## Build order
1. App shell and IME activation flow.
2. Core text insertion/deletion, shift, symbols, space and editor action.
3. Separate suggestion strip and offline dictionary.
4. Toolbar, clipboard and text editing.
5. Preferences, sizing, theme and language support.
6. QA across editor types, orientation and devices.
