# Recon map: Gboard (Android)

Scope: everything in Gboard on Android (typing, suggestions, voice, emoji/GIF/stickers, clipboard, editing, layouts and modes, themes, languages, settings, AI writing tools). Sized XL, so the build is phased below.
For: the user's own product / portfolio
Date: 2026-10-29

Method note: built from public pages only (help center, store listings, press, reviews). No APK was opened, no network calls were logged. The screen list is inferred from docs and articles, not from screenshots, so screen states are marked as seen-in-docs. Next to do: capture screenshots on your own device into `screens/`.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | help center: use your keyboard | https://support.google.com/gboard/answer/2842292 | languages, handwriting, settings entry points |
| 2 | help center: restore / privacy / delete words | https://support.google.com/gboard/answer/9058584 | what is and is not sent to Google |
| 3 | help center: feedback | https://support.google.com/gboard/answer/9430776 | comma long-press menu path |
| 4 | help center (nexus): keyboard | https://support.google.com/nexus/answer/6122956?hl=en-GB | add language, features menu |
| 5 | Play Store listing (via third-party mirror) | https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin | pitch, feature bullets, Wear OS |
| 6 | App Store listing (iOS, for pitch and privacy wording) | https://apps.apple.com/cm/app/gboard-the-google-keyboard/id1091700242?l=en-GB | privacy bullets |
| 7 | Wikipedia | https://en.wikipedia.org/wiki/Gboard | timeline of features |
| 8 | How-To Geek feature roundup | https://www.howtogeek.com/889328/gboard-keyboard-features-you-should-be-using-on-android/ | themes, clipboard, one-handed, autocorrect toggle |
| 9 | Yahoo Tech tips article | https://tech.yahoo.com/apps/articles/androids-gboard-tons-hidden-tricks-163015311.html | clipboard pins, Emoji Kitchen, split/floating |
| 10 | Android Ayuda: Gboard 6.2 | https://en.androidayuda.com/features-gboard-6-2/ | text editing, floating keyboard |
| 11 | 9to5Google teardown article | https://9to5google.com/2018/08/17/gboard-7-5-beta-apk-insight-teardown/ | toolbar access points (press coverage only) |
| 12 | Android Authority: Pixel 10 Gboard | https://www.androidauthority.com/pixel-10-gboard-writing-tool-3589264 | writing tools, voice editing |
| 13 | Android Authority: voice editing | https://www.androidauthority.com/gboard-ai-voice-typing-editing-3576706 | SmartEdit direction |
| 14 | blog, lower trust | https://justbeingresourceful.com/2026/09/10/gboard-just-got-a-lot-smarter-8-new-features-you-should-be-using/ | Rambler, AI stickers; treat as low confidence |
| 15 | community post, lower trust | https://community.iqoo.com/in/thread/109804 | flick keys, writing tools |

## Core loop

You tap or glide across keys in any app's text field, and the keyboard turns that into correct text fast using on-device prediction, with voice, emoji and clipboard one tap away.

## Screens

On Android a keyboard has panels instead of routes. "How to reach" is the interaction.

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Setup wizard | install, then enable and select as input method | get the IME active | step list, system dialogs | not enabled, enabled not selected, done |
| S02 | Letter keyboard | focus any text field | main typing | key grid, shift, strip, toolbar button | lowercase, shift, caps lock, number row on/off, per-field action key |
| S03 | Symbols / numbers | ?123 key | punctuation and digits | key grid, page toggle | page 1, page 2 |
| S04 | Suggestion strip | top of keyboard | predictions and corrections | word chips, emoji chip, mic | empty, 3 words, correction highlighted |
| S05 | Toolbar (features menu) | arrow/icon at strip's left | shortcut tray | icon buttons | collapsed, expanded, customise |
| S06 | Long-press popup | hold a key | accents, symbols | popup tray | open |
| S07 | Glide trail | drag across keys | swipe typing | trail overlay, preview word | drawing, committed |
| S08 | Language switcher | globe key or hold comma | change language or open settings | popup menu | 1 language, many |
| S09 | Emoji panel | emoji key | pick emoji | category tabs, grid, search, recents | recents empty, search results |
| S10 | Emoji mashup / stickers | emoji panel tab | sticker send | grid, tabs, search | loading, no results |
| S11 | GIF panel | emoji panel tab | search and send GIFs | search box, grid | loading, unsupported app |
| S12 | Clipboard panel | toolbar | recent and pinned clips | list, pin, toggle | off, empty, items, pinned |
| S13 | Text editing panel | toolbar | cursor and selection control | arrow keys, select, copy, cut, paste | nothing selected, selected |
| S14 | Voice typing | mic key | dictation | mic indicator, status | listening, offline, error, no permission |
| S15 | Translate panel | toolbar | translate as you type | language pair, result strip | idle, translating, error |
| S16 | Handwriting pad | handwriting keyboard | draw to enter text | canvas, candidates | blank, drawing |
| S17 | One-handed mode | toolbar or hold comma | shrink to one side | keyboard, side buttons | left, right |
| S18 | Floating keyboard | toolbar | movable resizable keyboard | drag handle, resize | docked, floating |
| S19 | Split keyboard | tablets/foldables | thumbs typing | two key halves | split |
| S20 | AI writing tools | sparkle/wand in strip | proofread, rephrase, tone | action chips, result sheet | unsupported device, working, result |
| S21 | Settings home | hold comma > settings, or app icon | entry to all settings | list of sections | n/a |
| S22 | Languages settings | settings | add and order languages and layouts | language list, layout picker | one, many |
| S23 | Preferences | settings | number row, sounds, haptics, height, long-press delay | toggles, sliders | n/a |
| S24 | Themes | settings | choose look | theme grid, custom option | selected |
| S25 | Glide typing settings | settings | glide toggles | toggles | n/a |
| S26 | Voice typing settings | settings | language, offline model, mode | toggles, language list | model not downloaded |
| S27 | Text correction | settings | autocorrect, suggestions toggles | toggles | n/a |
| S28 | Dictionary | settings | personal dictionary, delete learned words | word list, add | empty, filled |
| S29 | Emoji, stickers, GIFs settings | settings | emoji row, stickers toggles | toggles | n/a |
| S30 | Clipboard settings | settings or panel | enable, retention | toggle | n/a |
| S31 | Privacy / advanced | settings | usage stats, personalisation | toggles, links | n/a |
| S32 | Help and feedback | settings menu | support | links, feedback form | n/a |

## Flows

```
F01 First-time setup
    S01 setup wizard -> S02 keyboard in a field
    happy path clicks: ~4 (enable, select, allow, open a field)
    edge: IME disabled after an update, another keyboard auto-selected, no input permission

F02 Type a message with autocorrect
    S02 -> S04 strip -> accept suggestion or space
    happy path clicks: 1 per word (or 0 with autocorrect)
    edge: wrong correction, undo by backspace, learned word, password field disables learning

F03 Glide type a sentence
    S02 -> S07 trail -> lift finger -> S04
    happy path clicks: 1 gesture per word
    edge: ambiguous path, non-dictionary word, mixed languages

F04 Add a language and switch to it
    S21 settings -> S22 languages -> add -> S08 switcher
    happy path clicks: ~4 to add, 1-2 to switch
    edge: layout choice, language has no glide support

F05 Send an emoji or sticker
    S02 -> S09 emoji panel -> pick
    happy path clicks: 2
    edge: search with no results, app does not accept stickers/GIFs

F06 Paste from clipboard
    S05 toolbar -> S12 clipboard -> tap clip
    happy path clicks: 3
    edge: clipboard off, sensitive copied text, pinned item cap (unknown)

F07 Edit existing text
    S05 -> S13 editing panel -> move, select, copy/paste
    happy path clicks: 3+
    edge: field does not allow selection

F08 Dictate text
    S02 -> S14 voice typing -> speak
    happy path clicks: 1 to start, 1 to stop
    edge: offline model missing, mic permission denied, noisy audio

F09 Change theme
    S21 -> S24 themes -> pick
    happy path clicks: ~3

F10 Switch to one-handed or floating
    S05 -> S17 or S18
    happy path clicks: 2

F11 Rewrite text with AI
    S02 (text typed) -> S20 writing tools -> pick action -> accept
    happy path clicks: ~3
    edge: unsupported device, text too long

F12 Teach and clean the dictionary
    S28 dictionary -> add shortcut or delete learned word
    happy path clicks: ~3
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Key | letter, modifier (shift, symbols), action (enter), space, backspace | default, pressed, long-press, repeat, disabled | S02, S03 |
| Key popup | preview, long-press tray | shown, hidden | S02, S06 |
| Suggestion chip | word, emoji, correction | default, highlighted, pressed | S04 |
| Toolbar button | feature icon | default, active, overflow | S05 |
| Panel header / tabs | emoji, stickers, GIF | selected, unselected | S09-S11 |
| Search field | emoji, GIF, sticker | empty, typing, no results | S09-S11 |
| Grid cell | emoji, sticker, GIF | default, loading, selected | S09-S11 |
| List row | clipboard clip, dictionary word, language | default, pinned, swipe-delete | S12, S22, S28 |
| Toggle and slider | settings rows | on, off, disabled | S21-S31 |
| Theme tile | preset | selected | S24 |
| Trail overlay | glide path | drawing | S07 |
| Mic indicator | voice | listening, idle, error | S14 |
| Dialog / consent sheet | data-sent notice | shown | S20, S14 |
| Resize handle | floating | dragging | S18 |

## Inferred data model

All on-device. No server entities.

```
Language       id, name, enabled, order, layout_id, glide_supported
               evidence: S22, help center "Add a language"
               confidence: high
Layout         id, language_id, rows (keys, long-press map), number_row
               evidence: S02, S03, S06
               confidence: medium
LearnedWord    id, language_id, word, frequency, last_used
               evidence: help center "delete saved words"
               confidence: high
Shortcut       id, trigger, expansion
               evidence: S28, tips article
               confidence: medium
NgramModel     language_id, unigram/bigram counts (binary)
               evidence: suggestions behaviour
               confidence: guess (structure), high (existence)
ClipboardItem  id, type (text|image), content, pinned, created_at
               evidence: S12, Yahoo tips article
               confidence: high
RecentEmoji    emoji_id, last_used, count
               evidence: S09
               confidence: medium
Theme          id, name, colors, background (image|none), key_border
               evidence: S24
               confidence: medium
Pref           key, value (number row, haptics, sounds, height, mode, autocorrect...)
               evidence: S23, S25-S30
               confidence: high
ToolbarConfig  ordered list of shortcut ids, visible set
               evidence: toolbar customisation in articles
               confidence: medium
VoiceModel     language_id, downloaded, size
               evidence: S26, offline voice note on Wikipedia
               confidence: medium
```

Relationships: Language 1-n Layout, Language 1-n LearnedWord, Language 1-1 NgramModel, Language 1-1 VoiceModel, Theme n-1 Pref (selected), ToolbarConfig 1-1 Pref.

## Feature matrix

See `features.csv`. Must: 20, should: 19, could: 19, skip: 8.

## Out of scope (cannot or should not be cloned)

- Google's prediction, autocorrect and language models and its voice recognition models (proprietary). Use open word lists and your own n-gram model; an open speech model for voice.
- Emoji Kitchen artwork, Minis/Bitmoji partner stickers and Pixel Studio AI stickers.
- Gemini Nano writing tools; build with your own model if you want it.
- Google Search from the keyboard and cross-device cloud sync of learned words.
- The Gboard name, logo and icon artwork.
- Brand and identity are for the next skills; none of the original's assets carry over.

## Size

Screens 32, flows 12, entities 11. Hard parts: (1) prediction and autocorrect engine with multilingual models, (2) glide-typing path decoder, (3) on-device voice recognition and offline models, (4) IME lifecycle and per-app field behaviour (InputConnection, composing text, password fields, rich-content image insertion), (5) layouts and dictionaries for many languages, (6) responsive modes (one-handed, floating, split) and theming at low latency. Size: XL (rescope it).

Suggested phases so XL becomes three M builds:
1. Core typing: S01-S06, S21-S23, S27-S28, F01, F02, F04 (must rows)
2. Input extras: glide, emoji, clipboard, text editing, voice, themes, one-handed (should rows)
3. Advanced: handwriting, floating/split, translate, AI writing tools, GIF/stickers (could rows)
