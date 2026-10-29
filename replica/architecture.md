# CrazeKeyboard Architecture Plan

## Decision

Retain the existing Java/Android IME architecture in CrazeStudio/CrazeKeyboard-V2 for the first implementation pass. The repository already has a Gradle standalone Android build, Java source sets, native ndk-build integration, a registered `LatinIME` service, settings/setup activities, dictionaries and theme resources. A wholesale rewrite into Kotlin/Compose would add risk without helping the core keyboard reliability goals.

Use the existing code only under its applicable upstream license and NOTICE requirements. This is a product/UX recreation of Gboard behavior, not a copy of Gboard implementation. Audit provenance for each dictionary, native library and resource before redistributing. Keep CrazeKeyboard name, original Craze assets and UI identity.

## Stack

- Android application / IME: existing Java codebase and Android `InputMethodService` lifecycle.
- UI: existing Android Views/resources first; improve component separation incrementally.
- Text input: Android `InputConnection`, composing text, editor actions and `EditorInfo`.
- Prediction: existing offline dictionary/native suggestion engine after source/license and runtime audit; do not introduce a network dependency for core typing.
- Preferences: existing Android preference storage unless audit shows it is broken.
- Build: Gradle 8.7 + AGP 8.6.1 + JDK 17 + compileSdk 35 + NDK 26.1.10909125 as declared by the current project. Confirm by CI before changing versions.
- CI: existing `.github/workflows/android.yml` builds `assembleDebug` and uploads the APK artifact.

## Architecture boundaries

1. IME lifecycle/service: registration, view creation, editor info, input connections, show/hide, subtype switching.
2. Keyboard rendering: key rows, dimensions, touch regions, toolbar and candidate strip. Keep suggestions and toolbar in a predictable separate layer; avoid overlapping layout containers.
3. Input actions: one dispatch path for tap, repeat, long-press, alternate character, Shift, Backspace, Enter and editor actions.
4. Prediction: dictionary-backed candidates and correction. Gate learning/suggestions off for password and other sensitive fields.
5. Tools: clipboard and text editing operate against the current InputConnection and platform clipboard policies; no clipboard harvesting or logging.
6. Settings: preferences and theme values; validate size/spacing ranges and persist changes.
7. Optional integrations: voice, GIF/sticker, handwriting, glide decoding and translation are separate adapters and must not block offline typing.

## Data and privacy

No server or database is required for the core loop. Use local preferences, approved offline dictionaries and ephemeral composing state. Never log or persist password contents. Learned words should be local, opt-in where appropriate and clearable. Keep any optional network feature disabled by default and disclose when it sends data off-device.

## Routes / screens

Android activities and IME surfaces (actual classes should be confirmed during code audit):
- IME onboarding / enable and select keyboard.
- Main keyboard surface: alpha, numeric/symbol and special layouts.
- Suggestion strip and expanded toolbar.
- Text editor and clipboard tools.
- Emoji picker.
- Settings: preferences, languages/layouts, theme/layout, dictionary/privacy/about.
- Error / permission / unavailable feature states.

## Implementation milestones

### M0 — Baseline and provenance
- Verify repository NOTICE and licenses for Java, native code, dictionaries, and bundled assets.
- Run CI build; save exact failure/success logs.
- Inspect IME service, layout creation, toolbar dispatch, suggestion view, key dimensions and gesture handling.
- Capture baseline screenshots in portrait and landscape; test tap, backspace, long press, cursor edits and password field behavior.

### M1 — Core typing and layout (first code milestone)
- Fix any key insertion/deletion/cursor regressions.
- Fix suggestion strip visibility and ensure it does not collapse or overlap the toolbar.
- Normalize key widths, row heights, inter-key gaps, bottom inset and landscape layout.
- Ensure long press produces one intended result and does not also commit the base key.
- Respect `EditorInfo` for numeric, phone, email, URL, password and action keys.

### M2 — Toolbar vertical slice
- Wire the toolbar/menu to working actions.
- Implement text editing: cursor movement, selection, select-all, cut/copy/paste.
- Implement clipboard view and insertion with empty/permission/error states.
- Preserve the current input state when opening/closing tools.

### M3 — Suggestions and preferences
- Validate dictionary loading and suggestions offline.
- Add autocorrection with an explicit/reversible candidate selection path.
- Verify settings persist and key height/spacing controls update live and within safe bounds.
- Add English/Bengali support only after script/layout and dictionary licensing checks.

### M4 — Appearance and language
- Craze-branded themes, contrast, key borders, number row, long-press symbols, compact/one-handed mode.
- Language/subtype switching and mixed-language input.
- Accessibility labels and touch target review.

### M5 — Optional parity
- Emoji search/recents.
- Glide typing only if the decoder and model are appropriately licensed.
- Voice via Android speech APIs with explicit user action and permissions.
- GIF/sticker insertion only where the host app supports content commits.
- Handwriting/translation only as independently sourced modules.

## Acceptance tests

- Clean `./gradlew --no-daemon clean assembleDebug` in CI.
- Install and enable IME on Android 12+.
- Type and edit text in Settings, a browser/search field, a messaging field, email, numeric/phone fields and password fields.
- Confirm Shift, symbols, long press, delete-repeat, emoji, space and Enter behave correctly.
- Confirm suggestion strip and toolbar are visible, tappable and do not cause excessive key gaps.
- Rotate portrait/landscape and test narrow phone width.
- Verify clipboard and text editor at cursor start/middle/end and with selections.
- Verify password input is not displayed in candidate UI or learned/persisted.
- Confirm offline core typing works with network disabled.
- Track each feature in `replica/features.csv` as `yes`, `partial` or `no` only after actual verification.

## Known risks / not yet verified

- The repository's build workflow may need SDK/NDK and native dependency fixes; no build has been run in this recon step.
- Existing app is a LatinIME-derived project and not a blank clean-room implementation. Preserve applicable upstream notices and do not misrepresent the code's origin.
- A full Gboard-like feature set is XL scope. The first shippable milestone is reliable typing, toolbar/text editor/clipboard and dictionary-backed offline suggestions, not voice/translation/media parity.
