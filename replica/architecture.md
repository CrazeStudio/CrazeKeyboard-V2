# Architecture

- Platform: native Android.
- Language: Java 17.
- App UI: Android Views; scrollable settings home and category detail screens.
- Keyboard: custom-drawn Android View hosted by InputMethodService.\n- Icons: original SVG source assets with Android VectorDrawable XML equivalents; native Android rendering avoids an SVG runtime dependency.
- Input: EditorInfo and InputConnection.
- State: SharedPreferences for theme, keyboard height, selected language, key borders, and suggestion visibility.
- Suggestions: small local static English list; no cloud calls or personal learning.
- Network: none; manifest intentionally omits Internet permission.
- Build: Gradle 8.9 via CI, Android Gradle Plugin 8.6.1, compile SDK 35, min SDK 23.

## Build order
1. App shell and IME activation flow.
2. Core text insertion/deletion, shift, symbols, space and editor action.
3. Separate suggestion strip and offline word list.
4. Toolbar, clipboard and text editing.
5. FUTO-inspired settings information architecture with original CrazeKeyboard visuals.
6. Preferences, sizing, theme and direct Bengali character layout.
7. QA across editor types, orientation, contrast, accessibility and devices.

## Database/API decision\n- No remote backend, database schema, or app API is required for the current offline personal-project scope. Local preferences use SharedPreferences. Adding cloud sync would change the privacy model and require a separate architecture decision.\n\n## Known limitations
- No glide typing, offline voice model, full emoji picker, clipboard history, personal dictionary, phonetic Bengali input, or full theme editor.
- Haptic preference is not wired to device vibration.
- Runtime QA on physical Android devices is still required.
