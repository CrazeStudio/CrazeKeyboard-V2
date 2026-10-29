# Build log

## 2026-10-29 — S01/S02 keyboard and S08 setup shell
- Status: implemented in source; CI verification pending.
- Native Android InputMethodService with QWERTY and symbols, shift, backspace, space and editor action.
- Main activity provides enable/select IME shortcuts and privacy notes.
- No Internet permission or cloud service.

## 2026-10-29 — S03/S04/S05/S06/S07 first-pass toolbar slice
- Status: implemented in source; runtime QA pending.
- Added separate suggestion strip with a small offline word list; predictions are disabled for password/numeric fields.
- Added toolbar actions for clipboard paste, editing tools, emoji insertion and opening app settings.
- Editing tools support select-all, copy/cut selected text, and paste.

## 2026-10-29 — S09 first-pass appearance settings
- Status: implemented in source; CI/runtime QA pending.
- Added light/dark keyboard theme and adjustable keyboard height.
- Settings are stored locally in SharedPreferences.
- Known limitations: no full dictionary or word learning, no clipboard history, emoji picker is not a full grid, Bengali layout not implemented.
- Next: verify latest CI, fix any build/runtime errors, then expand language support and automated tests.
