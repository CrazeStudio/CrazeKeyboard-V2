# CrazeKeyboard-V2

CrazeKeyboard is an offline-first Android keyboard with a local next-word model, dictionary packs, a built-in editor, emoji search and privacy-aware personalization.

**Application ID:** `com.crazestudio.crazekeyboard`

## Keyboard and tools

- English QWERTY input with number row, symbols, shift/caps lock, editor-aware Enter/Search/Send/Next/Done actions, haptics and optional key sounds.
- Grouped tools menu with All, Typing, Editing and Layout tabs.
- Adjustable keyboard height, one-handed mode and an independently sized floating keyboard. Resizing persists after the drag rather than writing preferences on every movement.
- An in-keyboard live text editor, clipboard tools, theme chooser and quick settings access.
- Searchable Settings page with category navigation for languages, setup, AI, typing, toolbar, privacy, appearance, layout and keyboard behaviour.

## Emoji catalogue

The keyboard uses its bundled emoji inventory and offline CLDR English annotations. All 1,940 emoji sequences in the inventory have a human-readable name and search terms, including modifier and combined sequences. Emoji names are for English search; they do not imply localized names in every language. CLDR data is Unicode-3.0 licensed.

## Language packs

The app reads the language catalogue from CrazePredictionAPI. The catalogue contains 44 wordfreq-supported language codes. A language is listed separately from pack availability, suggestions and trained-model availability.

- English uses the current CrazeLM next-word model.
- Bengali and Hindi can use downloaded vocabulary prefix suggestions when their packs are installed.
- Other catalogue languages initially provide downloadable vocabulary only. Suggestions and models are disabled until appropriate ranking models and physical keyboard layouts are validated.
- Selecting a dictionary does not change the QWERTY key layout or provide transliteration. This is not yet a full 44-language keyboard.

Vocabulary archives are downloaded on demand, SHA-256 checked against the server manifest, checked against their declared entry count, and converted into private local prefix indexes.

## Local prediction and AI

CrazeLM is an experimental 30M-parameter English next-word model with a matching SentencePiece tokenizer. The model and tokenizer are installed on first use into app-private storage, checksum-verified and then used offline. CI no longer copies the full model files into the APK, reducing the release package; first-time model use therefore requires a download.

The model is a next-word predictor, not a full-text rewrite model. Quick polish remains a deterministic local formatter for spacing, punctuation and capitalization. No unvalidated quantized model or other-language model is shipped.

## Swipe typing

Swipe typing is an opt-in experimental feature and is disabled by default. It captures the path across QWERTY keys and ranks local dictionary candidates with a lightweight edit-distance heuristic. It is not a trained neural swipe decoder and its accuracy has not been validated on physical devices; it is restricted to the English dictionary for now.

## Privacy

Personalized prediction and word learning operate locally and respect the keyboard's incognito/private-field gates. Password and no-personalized-learning fields suppress prediction/personalization as configured by the IME. Swipe learning is disabled in private fields and follows the personalization preference. Clipboard history is opt-in and stored on-device. Voice typing uses the Android-selected speech-recognition service, which may send audio to its provider.

## Build

Build the debug APK with Android Studio or use the repository's GitHub Actions workflow (JDK 17, Gradle 8.7). CI compiles the app and uploads the APK. It does not package model weights into the APK.

## Validation status

The automated build checks compilation. Actual IME layout behaviour, resize interaction, key-hit accuracy, memory use, language input methods and swipe accuracy still require physical-device testing across Android versions and target apps. Do not treat experimental swipe or vocabulary-only languages as production-grade prediction support.
