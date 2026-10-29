# CrazeKeyboard build: dictionary that learns, wired into every typing feature

Date: 2026-10-29. Package `studio.craze.keyboard`. Min SDK 23, Java 17, no new dependencies, no Internet permission.

## What this adds

A dictionary that **starts empty and learns from typing**, so there is no word list to bundle. Everything that needs words reads from it:

| feature | how it uses the dictionary |
| --- | --- |
| Suggestion strip | completions for the word you are typing, corrections for typos, next-word predictions after a space |
| Autocorrect | swaps a typo for a learned word you have typed 4+ times; Backspace right after undoes it and teaches the word |
| Glide typing | matches your finger path to the shape of learned words |
| Text shortcuts | `omw` + space becomes `On my way!` (your own list) |
| Bengali and other scripts | word parsing handles vowel signs and joiners; one dictionary file per language |
| Privacy | no learning in password fields or fields that ask for no personalised learning; data stays in private app storage; no network code |

Also new: a **Dictionary screen** (toolbar button) with on/off switches for learning, suggestions, autocorrect and glide, a shortcut list, word search and delete, and delete-all.

## What is in this zip

```
app/src/main/java/studio/craze/keyboard/
  core/                      pure Java, no Android imports (new)
    WordChars, TextContext, EditDistance
    Dictionary, DictionaryStorage
    SuggestionEngine, AutoCorrector, GlideDecoder
  CrazeKeyboardService.java  REPLACES the repo's file (old hard-coded word list removed)
  DictionaryHolder.java      new
  DictionaryActivity.java    new
app/src/main/AndroidManifest.xml          REPLACES the repo's file (adds DictionaryActivity)
app/src/main/res/drawable/ic_dictionary.xml   new
core-tests/, run-core-tests.sh            tests for the core
replica/                     recon, features.csv, architecture, schema, design system, build log
```

## How to apply to the repo

1. Copy `app/` from this zip over the repo's `app/`. Two existing files are replaced: `CrazeKeyboardService.java` and `AndroidManifest.xml`. Every other file is new.
2. `replica/` already exists in the repo with some files of the same names (`recon.md`, `features.csv`, `architecture.md`). Compare before overwriting; this zip's versions are the ones from this session and include the design system and build log.
3. Push and let GitHub Actions build the debug APK. Nothing in `build.gradle` changes.

## Checked here, and not checked here

Checked:
- The core compiles for Java 8 and passes 88 checks (`sh run-core-tests.sh`, needs only a JDK): learning, case handling, Bengali words, file roundtrip, corrupt files, caps on size, suggestions, autocorrect rules, glide on noisy simulated paths, speed with 25,000 words.
- The Android classes type-check against hand-written stubs of the framework APIs.

Not checked (no Android SDK or device was available):
- A real compile against `android.jar`. The repo's CI is the first real compile; if it reports an error, it is most likely a small signature mismatch in `CrazeKeyboardService.java` or `DictionaryActivity.java`.
- Behaviour on a phone. Test list below.

## Device test list

1. Enable the keyboard, open any text field, type 6 to 8 sentences. Open Dictionary from the toolbar: words and pairs should be counting up.
2. Type a word you have used several times with one letter swapped (for example `teh`) then space: it should correct. Press Backspace right away: the original comes back and will not be corrected again.
3. After typing, tap the strip: typed word first, then completions. After a space, next-word predictions should appear.
4. Glide over a word you have already typed: it should insert the word and a space. On a fresh install with nothing learned, you should see the one-time hint.
5. Add a shortcut (`omw` to `On my way!`), type `omw` and space.
6. Password field: no suggestions, nothing learned (count does not rise).
7. Bengali: set language to `bn`, type a few words, confirm they are learned whole (vowel signs stay attached).
8. Dictionary screen: delete one word, delete all.

## Known limits

- Early on the dictionary is thin, so autocorrect does little and glide needs words you have typed. That is the trade for not bundling a word list. Typing normally fills it.
- Glide thresholds (28dp to start a gesture, match cost limit of 1.0 key width) are starting values; tune them on a real thumb.
- Per-language dictionaries do not blend yet: a bilingual typist gets suggestions for the selected language only.
- Not built in this pass: emoji panel and search, clipboard history, long-press popups, offline voice typing, themes, one-handed mode, number row (see `replica/features.csv` for the full list and order in `replica/architecture.md`).

## Clean room

Everything here was written from the recon map and the design specs. No code, assets, word lists or models from the original app are used.
