# CrazeKeyboard clean-room recon

Reference product: Gboard, researched only through public product behavior and help material. This is an independent implementation, not Google's code or brand.

## Core screens
- S01 Keyboard: QWERTY, shift, delete, space, enter, symbols.
- S02 Symbols/numbers: digits and common punctuation.
- S03 Suggestions: local dictionary predictions (planned).
- S04 Toolbar: clipboard, text editing, emoji, settings (planned).
- S05 Text editing tools (planned).
- S06 Clipboard manager (planned).
- S07 Emoji picker (initial emoji key inserts a default emoji; picker planned).
- S08 Settings home: enable/select keyboard, privacy explanation.
- S09 Layout/theme preferences (planned).
- S10 Languages and layouts (English first; Bengali planned).
- S11 Diagnostics and help (planned).

## Core flow
Install -> open app -> enable input method -> select keyboard -> type into an editor -> switch symbols -> return to letters -> delete/space/enter -> switch away.

## Requirements
- Android InputMethodService and InputConnection; no cloud dependency for core typing.
- Password fields must not learn or display suggestions.
- Keep labels and implementation original; do not reuse proprietary assets or source.
- Keyboard must resize to portrait/landscape and respect Android editor action options.
- No Internet permission in the initial build.
