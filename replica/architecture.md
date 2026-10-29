# Architecture: CrazeKeyboard (a rebuild of Gboard's core features, Android)

Reads `recon.md` and `features.csv` (66 rows: 20 must, 19 should, 19 could, 8 skip). This is an Android input method, not a web app, so the web template is adapted: no backend, no payments, no email, no hosting. The brief is offline-first, which removes whole layers (see "Cut because of offline").

This matches the architecture already in the repo's `replica/architecture.md` (Java 17, Android Views, InputMethodService, SharedPreferences, no Internet permission). Nothing here contradicts it; it adds the local database, the engines and the milestones.

## Stack

| layer | choice | why |
| --- | --- | --- |
| platform | native Android, Java 17, min SDK 23, compile SDK 35 | already the repo's base; IMEs need native APIs |
| keyboard UI | custom-drawn View in `InputMethodService` | lowest latency; full control of keys, trail, popups |
| app UI (settings, setup) | Android Views, settings home plus detail screens | matches the repo; S21-S32 are plain lists and toggles |
| local database | SQLite through Room | learned words, bigrams, clips, shortcuts need queries and indexes |
| scalar prefs | SharedPreferences | read on every key press; already used |
| suggestions | bundled trie per language (memory-mapped) plus user tables | fast prefix lookup, no network |
| glide decoder | path-to-word matching against the trie (SHARK2-style shape matching, a published academic method) | no model needed for a first version |
| emoji data | bundled Unicode CLDR annotations (open licence) | search without network |
| voice (offline) | open speech model such as Vosk or whisper.cpp, loaded from bundled or user-imported file | Google's models are off limits; no network allowed |
| background work | WorkManager | clip expiry and dictionary maintenance |
| auth, payments, email, hosting | none | no accounts, no money, no server |
| build | Gradle 8.9, AGP 8.6.1, GitHub Actions `assembleDebug` (add `assembleRelease` plus signing later) | already the repo's setup |

## Cut because of offline

- GIF search (needs a provider API): dropped from the build. Local sticker packs only.
- Translate as you type: could-row, only with an on-device translation model; deferred.
- Cloud sync: already `skip`. Offer dictionary export/import to a file instead.
- AI writing tools: could-row, only with a small local model; deferred to the last milestone.

## Schema

Tables: 9 (`languages`, `learned_words`, `user_bigrams`, `shortcuts`, `clipboard_items`, `recent_emoji`, `toolbar_items`, `themes`, `voice_models`). Full SQL in `schema.sql`; it was loaded and exercised in SQLite (constraints, the clipboard cap trigger and cascade deletes all behave).

Access rules: the database lives in app-private storage, no exported ContentProvider, `allowBackup` off or backup rules that exclude the database and clip images. There is no owner column because there is one user per install. Data-layer checks, not RLS: only the IME process and the settings activity open the database, through one repository class.

Hard constraints in the schema:
- one word per language (`unique (language_id, word)`), one shortcut trigger per language
- clip rows must be a text or an image, never both (check constraint)
- unpinned clips capped at 50 by trigger; pinned clips never expire
- deleting a language cascades to its words, bigrams, shortcuts and voice model

Layouts are not in the database. They are JSON files in `assets/layouts/`, referenced by `languages.layout_id`.

## Component contracts (the "API")

There is no HTTP API. The contracts are the Android entry points and the internal services. One row per flow step.

| entry point / service | does | called by | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `SetupActivity` | opens system IME settings and the picker | user | none | enabled and selected state | F01 |
| `CrazeInputMethodService.onStartInputView` | reads `EditorInfo`, picks layout, action key, learning on/off | Android | `EditorInfo` | configured keyboard | F01, F02 |
| `KeyView.onTouchEvent` | resolves key, long press, repeat, glide start | touch | motion events | key events | F02, F03 |
| `InputController.commit / setComposing / delete` | writes text through `InputConnection` | key handler | text, cursor | editor change | F02 |
| `SuggestionEngine.suggest(prefix, prevWords, lang)` | ranked candidates from trie plus user tables, off the main thread | InputController | prefix, context | candidate list | F02 |
| `AutoCorrector.correctOnBoundary` | replaces the composing word on space or punctuation, allows undo on backspace | InputController | composing word | corrected word | F02 |
| `LearningStore.record(word, prev)` | upserts into `learned_words`, `user_bigrams`; batched on `onFinishInput` | InputController | word pair | none | F02, F12 |
| `GlideDecoder.decode(points, layout, lang)` | candidate words from the finger path | KeyView | point list | ranked words | F03 |
| `LanguageManager.add / remove / switch` | edits `languages`, loads layout and trie | settings, globe key | language id | active language | F04 |
| `EmojiRepository.search / recents / record` | CLDR search, `recent_emoji` | emoji panel | query | emoji list | F05 |
| `ClipboardRepository` (listener on `ClipboardManager`) | stores clips while this IME is the default, pin, delete, expire | system, clip panel | clip data | list | F06 |
| `TextEditController` | arrow keys, select word/all, cut/copy/paste via key events and `performContextMenuAction` | edit panel | action | editor change | F07 |
| `VoiceController` | offline recognition to text, mic permission flow | mic key | audio | text | F08 |
| `ThemeManager` | applies theme tokens, follows system dark mode | settings, service | theme id | colours | F09 |
| `ModeController` | one-handed, floating, split geometry | toolbar | mode | resized keyboard | F10 |
| `WritingToolsController` | proofread, rephrase, tone with a local model | strip button | text | replacement text | F11 |
| `DictionaryScreen` + `UserDictionaryRepository` | list, add shortcut, delete learned word, export/import | settings | CRUD | updated tables | F12 |
| `ToolbarConfigRepository` | order and visibility of toolbar shortcuts | settings, toolbar | list | list | F05-F10 |

Webhooks in: none. Webhooks out: none. Official APIs used: Android `InputMethodService`, `InputConnection`, `ClipboardManager`, `InputConnectionCompat.commitContent` for stickers. Nothing from Gboard's app or services.

Jobs (WorkManager):
- `ClipboardSweep`: deletes expired unpinned clips and orphan image files; on every insert plus daily.
- `DictionaryMaintenance`: decays old frequencies and prunes rare bigrams; weekly, while idle and charging.

## The parts that bite

- Composing text vs commit: other apps can move the cursor or reset the field. Track `onUpdateSelection` and drop the composing state when it diverges, or you get doubled or lost words.
- Latency: key down to pixel under ~16 ms. No allocation in `onDraw`, no database call on the main thread, suggestions computed on a worker with a generation counter so stale results are discarded.
- Privacy fields: password, email and `IME_FLAG_NO_PERSONALIZED_LEARNING` fields must disable suggestions, learning and clip capture. Never log typed text. Respect the sensitive-clip flag on Android 13+ (do not store those clips).
- Clipboard: only readable while this IME is the active keyboard on newer Android. Clips can hold passwords: expire by default, let the user turn history off, keep image files out of backup.
- Direct boot and storage: decide whether the keyboard must work before first unlock; credential-encrypted storage is unavailable then, and device-protected storage needs API 24 or higher (min SDK is 23).
- Idempotency and races: learning writes come from the IME process while settings can delete words. Use one writer thread and Room transactions; the unique constraints make repeated records safe.
- Time zones: not relevant; all timestamps are epoch milliseconds in UTC.
- Indic and RTL text: Bengali conjuncts and composing need their own input logic and testing on real fonts; ordinary key-per-letter assumptions break.
- Dictionary data: word lists and n-grams must come from openly licensed sources with the licence kept in the repo. Size and APK budget grow per language.
- Offline voice: model size (tens to hundreds of MB), RAM use inside the IME process, and accuracy on low-end phones. Prefer an optional importable model over bundling.
- Rich content: stickers only appear if the target app accepts images (`EditorInfo` content MIME types); always fall back gracefully.
- Accessibility: key labels for TalkBack, minimum touch target sizes, high-contrast themes.
- Search: emoji search is an in-memory index built from bundled data at startup; no full-text engine needed.
- Deletion (GDPR-style): "delete learned words" must wipe `learned_words`, `user_bigrams` and `shortcuts` rows and vacuum; no remote copy exists.

## Build order

Status of the repo (from its README): app shell, setup flow, QWERTY, shift, symbols, space, enter and password safeguards exist. Suggestions are not implemented. So milestone 0 is done.

0. **Done in repo:** S01, S02, S03, minimal S21; no tables.
1. **Vertical slice (next):** S04 suggestion strip, F02 end to end. Type, see a suggestion, accept or autocorrect, and the word is learned and offered next time. Tables: `languages` (one row, `en-US`), `learned_words`, `user_bigrams`. Services: `SuggestionEngine` over a bundled word list, `AutoCorrector`, `LearningStore`. Routes: the rows for F02.
2. **Must-haves** (20 rows):
   - Layout depth: long-press popups S06, symbol hints, per-field action key, backspace repeat.
   - Languages: S08, S22, multilingual suggestions, F04. Tables: `languages` complete, layout JSON assets.
   - Toolbar S05 with `toolbar_items`.
   - Emoji panel and search S09 with `recent_emoji`.
   - Glide typing S07 with `GlideDecoder`.
   - Offline voice typing S14, F08 with `voice_models`.
   - Settings S21-S23, S27, S28, S31 and the privacy rules.
3. **Should-haves** (19 rows): clipboard history and pins S12, S30 (`clipboard_items`, `ClipboardSweep`); text editing panel S13; one-handed S17; themes S24 (`themes`); personal dictionary with shortcuts S28 (`shortcuts`); number row, key sounds and haptics, keyboard height; emoji suggestions; offline voice improvements.
4. **Could-haves** (19 rows): handwriting S16, floating S18, split S19, own-art emoji mashups, local translation S15, local AI writing tools S20, Morse input, Wear OS (separate project).
5. **Fixes from replica-entrepreneur**, once it has run.

Each milestone ends with a device test pass (editor types, orientation, TalkBack, a low-end phone) before the next starts.


## Build notes (2026-10-29)

Changes from the plan above, made while building:
- Learned data lives in one text file per language (`files/dictionary_<lang>.txt`) instead of Room/SQLite. Same entities (words, pairs, shortcuts), no new dependency, testable with a plain JDK. `schema.sql` stays as the target for when clipboard history and themes need real queries.
- The dictionary starts empty. No word list is bundled; suggestions, autocorrect and glide all read from what the person has typed. Trade-off: first days feel sparse, which is why autocorrect is cautious and glide shows a one-time hint.
- Core logic is in `studio.craze.keyboard.core` with no Android imports so it can be tested with `run-core-tests.sh`.
