# Build log

## 2026-10-29 — S01/S02 keyboard and S08 setup shell
- Status: implemented in source; CI verification of latest commit pending.
- Native Android InputMethodService with QWERTY and symbols, shift, backspace, space and editor action.
- Main activity provides enable/select IME shortcuts and privacy notes.
- No Internet permission or cloud service.

## 2026-10-29 — S03/S04/S05/S06/S07 first-pass toolbar slice
- Status: partial implementation; runtime QA pending.
- Added a separate suggestion strip with a small offline word list; suggestions are disabled for password/numeric fields.
- Added toolbar actions for clipboard paste, editing tools, emoji insertion and opening settings.
- Editing tools support select-all, copy/cut selected text, and paste.
- Added explicit measured height for keyboard surface.
- Known limitations: not a full language model or dictionary, emoji picker is a single emoji insert, clipboard history is not stored, settings screen is still basic, Bengali layout and themes are pending.
- Next: check latest CI result, fix any compile/runtime failures, then implement settings and add tests.
