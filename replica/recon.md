# CrazeKeyboard clean-room recon

Reference product: FUTO Keyboard, researched through public product pages, settings documentation, and feature documentation. This is an independent implementation of common keyboard behaviours and settings patterns. It does not reuse FUTO code, logos, icons, fonts, illustrations, or product copy.

## Reference UX observations
- Privacy-first positioning and offline operation are prominent.
- Settings are grouped into Languages & Models, Keyboard & Typing, Text Prediction, Voice Input, Personal Dictionary, Theme, Help & Feedback, Developer Settings, and Custom Layouts.
- Theme options include dark/light appearance and key borders; public docs also describe background-image customization.
- Toolbar actions include emoji, voice, language switch, text editor, clipboard manager, paste, cut, copy, and theme switcher.
- The product is actively developed; not every feature or language is available in every build.

## CrazeKeyboard screen inventory
- S01 Keyboard: QWERTY, shift, delete, space, enter, symbols.
- S02 Symbols/numbers: digits and common punctuation.
- S03 Suggestions: local word candidates; currently a small static English list.
- S04 Toolbar: clipboard paste, text editing, emoji insert, settings.
- S05 Text editing tools: select all, copy, cut, paste.
- S06 Clipboard actions: paste from Android clipboard; clipboard history is not yet implemented.
- S07 Emoji: one default emoji insert; full picker planned.
- S08 Setup/home: enable/select keyboard, privacy note, searchable settings list.
- S09 Languages & Models: English/Bengali layout selection and model status.
- S10 Keyboard & Typing: height and key borders; haptics preference currently not connected.
- S11 Text Prediction: enable/disable local suggestions and disclose model limitations.
- S12 Voice Input: unavailable-state explanation.
- S13 Personal Dictionary: unavailable-state explanation.
- S14 Theme: dark mode and key borders.
- S15 Help & Feedback: privacy notes and known limitations.
- S16 Developer Settings: build/network info.
- S17 Editor variants: password/numeric fields suppress suggestions; runtime matrix testing pending.

## Core flow
Install -> open CrazeKeyboard -> enable input method -> choose keyboard -> type into an editor -> switch symbols/language -> use toolbar -> change preferences -> return to typing.

## Requirements
- Android InputMethodService and InputConnection; no cloud dependency for core typing.
- Password fields must not learn or display suggestions.
- Use original copy, palette and assets; reproduce common interaction patterns rather than branded artwork.
- Keyboard should resize to portrait/landscape and respect Android editor actions.
- No Internet permission in the initial build.
- Do not claim parity for unavailable voice, swipe, dictionary, or emoji features.
