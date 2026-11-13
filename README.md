# CrazeKeyboard V2 — Phase Two

CrazeKeyboard is an offline-first Android IME by CrazeStudio.

**Application ID:** `com.crazestudio.crazekeyboard`

## Phase-two capabilities

- Adaptive toolbar: quick tools before typing, live word suggestions after typing, and an expanded tool menu.
- Customisable toolbar shortcuts with enable/disable controls and user-defined ordering.
- Built-in full-screen text editor with selection editing, insert-at-cursor, undo/redo, select-all, copy, cut, paste, clear, and find/replace.
- Simplified CrazeKeyboard launcher icon and a consistent keyboard identity.
- Predictor index warmed off the main thread and a shorter debounced suggestion refresh to reduce first-use and typing pauses.

## Phase-one capabilities

- English QWERTY keyboard with optional number row, symbols pages, shift/caps lock, and editor-aware Enter/Search/Send/Next/Done actions.
- Adaptive key sizing with a live graphical width/height resizer, left/right one-handed layouts, Light/Dark/Craze themes, configurable key-click sounds and haptic feedback, and a separate candidate strip.
- A draggable floating keyboard layer with an independent touch region, a move grip, and a live horizontal resize grip.
- Local English dictionary loaded from `app/src/main/assets/main_en-US.dict`, with a small built-in fallback list.
- CrazeLM-only next-word suggestions using an on-device TensorFlow Lite model and the matching 100k SentencePiece tokenizer. Legacy dictionary, phrase n-gram, learned-word ranking, and remote suggestion sources are not mixed into the suggestion strip.
- AI assets download on first keyboard use into app-private storage, are SHA-256 verified, and then run offline. The current CrazeLM is a next-word model; full-text AI rewriting is not yet supported.
- An in-keyboard live text editor that mirrors the focused app field, supports native long-press selection and selection actions, and writes edits back immediately.
- Contextual emoji recommendations from CrazeLM's predicted words and the local emoji alias index.
- Double-space period, repeat backspace, long-press word deletion, and long-press accented character alternatives.
- Emoji categories and recents, plus optional local clipboard history.
- Suggestions, correction, and clipboard capture are suppressed in password and no-personalized-learning fields; clipboard capture only runs while the keyboard is visible.

## Privacy and offline behaviour

By default, text prediction is local. The CrazeLM model and matching tokenizer are downloaded on first use, verified, and saved in app-private storage. After installation, CrazeLM next-word prediction and contextual emoji recommendations run locally. The live editor edits the active app field directly; its experimental AI-polish entry remains informational until a rewrite-capable model is integrated. The default keyboard theme follows Android system colours. Optional online prefix suggestions are disabled by default; when explicitly enabled, only the current word prefix and language are sent, never the surrounding sentence. Clipboard history is opt-in and remains on-device. Voice typing is different: it uses the Android-selected speech recognition service, which may send audio to its provider for recognition. Microphone access is permission-gated and voice input is suppressed in password and restricted fields.

## Build and install

Open the project in Android Studio and build the `app` debug variant, or use the repository's GitHub Actions workflow. Install the APK, enable CrazeKeyboard in Android's keyboard settings, and then select it as the active input method.

## Current scope

Phase two adds an adaptive, customisable toolbar and a companion text editor while keeping the offline-first keyboard foundation. It is not a Gboard clone. It does **not** include swipe/gesture typing, voice dictation, cloud sync, translation, GIF/sticker search, or broad multilingual/Indic input layouts. CrazeLM is an experimental 30M model; its word ranking and latency still need evaluation on physical devices. The included English word list is a lightweight starter lexicon, not a full commercial dictionary. Accuracy, editor hand-off, and latency must still be validated on physical devices and across different editor apps.

## Dictionary and data licensing

The bundled word list is a curated starter list maintained in this repository. Any future replacement or expansion using third-party corpora must include its source, license, attribution, and redistribution terms before being bundled.
