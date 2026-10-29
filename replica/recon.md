# CrazeKeyboard Gboard-Parity Recon

- Target: Android IME, CrazeKeyboard-V2
- Reference: Gboard public product listing and public Gboard Help pages
- Goal: personal project; functional parity where feasible, with CrazeKeyboard branding and original UI assets
- Method: clean-room product/UX research. Do not copy Gboard source, binaries, logos, icons, proprietary models, private APIs, or verbatim copy.
- Repository observation (2026-10-29): CrazeStudio/CrazeKeyboard-V2 is public, default branch main, Java repository. Its tree includes an Android manifest, Gradle files, Java IME sources, dictionaries, theme resources and NOTICE files. This looks like an existing LatinIME-derived codebase rather than a blank Android app. Audit provenance, license, NOTICE and current behavior before modifying source. Keep upstream attribution and license obligations intact; do not assume every bundled dictionary/model/resource has the same license.
- Known project constraints from prior work: prioritize a working toolbar, correct keyboard positioning, reduced excessive gaps between keys, suggestion strip as a separate layer, clipboard/text editor toolbar actions, dictionary-backed suggestions, reliable tap/long-press behavior, responsive layout, and Android-phone-first development.

## Public sources

| Source | URL | Evidence |
|---|---|---|
| Google Play Gboard listing | https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin | Public feature inventory: Glide Typing, voice typing, handwriting, emoji/GIF search, multilingual typing, translation, one-handed mode, themes |
| Gboard setup help | https://support.google.com/gboard/answer/6380730 | Settings, languages/layouts, glide typing, text correction, voice typing |
| Glide typing help | https://support.google.com/gboard/answer/2811346 | Gesture typing flow |
| Gboard text editing / Indic tools announcement | https://blog.google/products-and-platforms/products/search/gboard-android-gets-new-languages-and-tools/ | Text editing, cursor movement, cut/copy/paste, resize/reposition, Indic language/transliteration examples |
| Android InputMethodService guide | https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method | IME lifecycle, input/candidates views, manifest/service registration, InputConnection, subtypes, input type handling |

## Screen inventory

IDs are stable; clone implementation status begins as not implemented / unverified until code and device tests confirm it.

| ID | Screen / state | Entry | Purpose / key components | Important states |
|---|---|---|---|---|
| S01 | Main keyboard: alphabet | Focus a text field | QWERTY rows, Shift, delete, space, enter, symbols, suggestions, toolbar | first show, normal, composing, loading suggestions, no suggestions, error, portrait, landscape |
| S02 | Number/symbol layout | ?123 / symbol toggle | Numbers, punctuation, alternate symbols, return-to-alpha | normal, long-press alternate |
| S03 | Suggestion strip | Typing a word | Candidate words, correction, toolbar affordance | empty, 1-3 candidates, accepted correction, disabled in sensitive fields |
| S04 | Toolbar / expanded tools | Toolbar/menu tap | Clipboard, text editing, emoji, settings, theme, one-handed/layout tools | collapsed, expanded, each tool opened |
| S05 | Text editor | Text editor toolbar action | Cursor arrows, select, select-all, cut, copy, paste, undo where feasible | selection empty/nonempty, clipboard unavailable, cursor boundaries |
| S06 | Clipboard | Clipboard toolbar action | Recent clipboard items, paste, pin/delete/clear if implemented | empty, populated, permission/privacy restrictions |
| S07 | Emoji picker | Emoji key/tool | Categories, recents, search, insert emoji | empty search, results, no results, unsupported recipient |
| S08 | GIF/sticker search | Media tool | Search and share via Android commit-content APIs when target supports it | loading, results, no results, unsupported app, offline |
| S09 | Settings home | App launcher or toolbar | Preferences, languages, dictionary, appearance, privacy, about | defaults, changed settings, reset |
| S10 | Preferences | Settings | Number row, key borders, key height/gaps, vibration/sound, long-press, autocorrect | saved/reopened, invalid bounds |
| S11 | Languages & layouts | Settings | Enabled languages/layouts, switching, locale subtypes | one language, multiple languages, unsupported locale |
| S12 | Theme & layout | Settings | Light/dark/custom themes, keyboard height, one-handed/compact mode | preview, apply, reset, landscape |
| S13 | Dictionary / personalization | Settings | Offline wordlist, learned words, personal dictionary controls | no words, added/removed words, clear data |
| S14 | Voice input | Mic action | Optional OS speech recognizer integration; explicit consent and clear provider behavior | permission denied, unavailable, listening, error |
| S15 | Handwriting / glide typing | Gesture or mode switch | Stroke capture and decoding | supported/unsupported language, cancel, candidate selection |
| S16 | Onboarding / enable keyboard | First launch | Explain enable/select IME, privacy, permissions | not enabled, enabled-not-selected, selected |
| S17 | Input-type variants | Focus field of specific type | Numeric, phone, email, URL, password, multiline/search action layouts | each EditorInfo type; password privacy |
| S18 | Language / IME switch | Globe/system switcher | Switch subtype or another installed keyboard | only one subtype, multiple subtypes, system switch unavailable |
| S19 | Error / diagnostics | Failure from feature | Explain unavailable feature without blocking typing | recover, retry, return to keyboard |

## User flows

- F01 Type text: S16 onboarding -> S01 -> S03 candidate selection -> commit through InputConnection -> field contains correct text.
- F02 Correct a word: S01 type word -> S03 choose candidate -> replace composing/current word without duplicate spaces or lost cursor.
- F03 Use toolbar: S01 -> S04 -> open S05/S06/S07 -> perform action -> return to same keyboard state.
- F04 Clipboard paste: copy text in host app -> S06 -> select item -> paste into current InputConnection; never expose clipboard content in logs.
- F05 Change appearance: S09 -> S12 -> preview/apply -> reopen keyboard and verify theme, height, spacing persist.
- F06 Enable languages: S09 -> S11 -> add language/layout -> S18 switch -> type mixed English/Bengali text.
- F07 Use sensitive fields: host field indicates password -> suppress learned suggestions/personalization and never store password text; preserve reliable symbols/input.
- F08 Handle keyboard actions: inspect EditorInfo -> choose suitable layout/action label -> invoke correct editor action or newline.
- F09 Long-press: key down -> popup/alternate selection -> release -> commit exactly one expected character; cancellation must not duplicate a tap.
- F10 Landscape/compact: change orientation or one-handed mode -> remeasure keyboard -> no overflow, clipped toolbar, huge gaps, or off-screen controls.

## Component inventory

KeyButton (normal, special, pressed, disabled, popup); KeyRow; SuggestionStrip (separate stable layout layer); ToolbarButton; ExpandableToolPanel; EmojiGrid; SearchField; ClipboardItem; TextEditingPad; ThemePreview; LanguageRow; TogglePreference; SliderPreference; Permission/IME setup card; Snackbar/inline error; keyboard-height/layout container.

## Inferred data model

- KeyboardPreferences: layout, numberRow, keyBorders, keyHeightScale, rowSpacing, keySpacing, haptics, sound, longPressSymbols, autocorrect, suggestions, themeId, oneHandedMode, toolbarOrder.
- EnabledLanguage: localeTag, layoutId, enabled, subtypeId.
- DictionaryEntry: localeTag, token, frequency/source metadata, userAdded flag; use licensed wordlists only.
- LearnedWord: token, localeTag, usage metadata; opt-in, local-only, clearable. Never learn from password/sensitive fields.
- ClipboardEntry: platform-provided clip content and timestamp, only as allowed by Android clipboard policy; no custom background harvesting.
- Theme: id, role-based colors, border and shape settings, contrast-validated.
- SessionState (ephemeral): inputType, editorAction, composingText, candidates, keyboardMode, toolbarMode; sensitive text must not be persisted or logged.

## Feature priorities

Must: reliable IME registration/enablement, correct character insertion/deletion/cursor handling, input-type-specific layouts, Shift/caps, symbols, stable suggestion strip, functional toolbar/menu, clipboard/text editor, settings persistence, responsive key sizing/spacing, landscape, privacy-safe password behavior, English offline dictionary suggestions.
Should: autocorrection with undo/revert, Bengali + English layout support, themes, number row, long-press symbols, one-handed/height settings, emoji search/recent, glide typing if decoding engine and licenses permit.
Could: GIF/sticker sharing, handwriting, voice input, translation, advanced next-word prediction, multi-language glide, cloud search.
Out of scope for a clean-room personal recreation: Google branding, Gboard binaries/source or private endpoints, Google-owned model assets, replicating Google account/cloud services, or claiming parity for features requiring proprietary services. Use independently licensed components and public Android APIs only.

## Risk / sizing

Overall size: XL for broad Gboard parity. A useful first milestone is M: reliable offline English typing + separate suggestion strip + toolbar/menu + text editor + clipboard + settings, all verified on-device. Highest risks: candidate engine quality/licensing; clipboard/editor behavior across host apps; touch/long-press/layout regressions; voice/media integrations and target-app support.

## Build order / acceptance

1. Audit existing repository provenance, license/NOTICE, Gradle build and IME manifest before code changes.
2. Establish a clean baseline build and install workflow; record current behavior and screenshots.
3. Fix core input correctness and sizing/positioning, especially toolbar, suggestion strip, key gaps, taps and long-press.
4. Deliver toolbar -> text editor / clipboard as a complete vertical slice.
5. Add offline suggestions/autocorrect with licensed dictionary and sensitive-field protections.
6. Add settings persistence, language/layout handling and themes.
7. Add emoji, then optional media/voice/glide features.
8. Test portrait and landscape, 390dp-class narrow layout and tablet/wide layout, multiple input types, cursor editing, and representative apps. Do not mark features complete until tested on device.

## Status note

Recon is based on public product documentation plus repository metadata/tree inspection. Exact runtime behavior, screen count in the existing app, build success, dependency health and current feature completeness remain unverified until source/build/test audit.
