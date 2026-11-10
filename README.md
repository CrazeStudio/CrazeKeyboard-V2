# CrazeKeyboard V1

CrazeKeyboard is an offline-first Android IME by CrazeStudio.

**Application ID:** `com.crazestudio.crazekeyboard`

## V1 typing features

- English QWERTY keyboard with optional number row, symbols pages, shift/caps lock, and editor-aware Enter/Search/Send/Next/Done actions.
- Adaptive key sizing, left/right one-handed layouts, Light/Dark/Craze themes, haptic feedback, and a separate candidate strip.
- Local English dictionary loaded from `app/src/main/assets/main_en-US.dict`, with a small built-in fallback list.
- Frequency-ordered prefix completions, typo alternatives using bounded edit distance, and conservative automatic correction on space.
- Lightweight context-based next-word candidates for common preceding words.
- On-device learned words (up to 500), with a settings action to clear them.
- Double-space period, repeat backspace, long-press word deletion, and long-press accented character alternatives.
- Emoji categories and recents, plus optional local clipboard history.
- Suggestions, correction, and clipboard capture are suppressed in password and no-personalized-learning fields; clipboard capture only runs while the keyboard is visible.

## Privacy and offline behaviour

The keyboard does not request Internet permission and has no remote prediction endpoint. Dictionary lookup, spelling alternatives, contextual candidates, and learned words are processed locally. Clipboard history is opt-in and remains on-device. Do not enable clipboard history if you do not want copied text retained.

## Build and install

Open the project in Android Studio and build the `app` debug variant, or use the repository's GitHub Actions workflow. Install the APK, enable CrazeKeyboard in Android's keyboard settings, and then select it as the active input method.

## V1 boundaries

V1 is a practical offline foundation, not a Gboard clone. It does **not** include swipe/gesture typing, neural language models, voice dictation, cloud sync, translation, GIF/sticker search, or broad multilingual/Indic input layouts. The included English word list is a lightweight starter lexicon, not a full commercial dictionary. Accuracy and latency must still be validated on physical devices and across different editor apps.

## Dictionary and data licensing

The bundled word list is a curated starter list maintained in this repository. Any future replacement or expansion using third-party corpora must include its source, license, attribution, and redistribution terms before being bundled.
