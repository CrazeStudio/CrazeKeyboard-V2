# Build log

## 2026-10-29 — Native IME and app shell
- Added the Android InputMethodService, QWERTY/symbol layers, shift, backspace, space, enter/editor actions, and setup screen.
- Manifest intentionally has no Internet permission.

## 2026-10-29 — Offline suggestions and editing toolbar
- Added a small offline word list suggestion strip, clipboard paste, copy/cut selected text, select-all, emoji insertion, and settings shortcut.
- CI passed for commit 77efdc7 (APK artifact uploaded). Later settings commits are being rebuilt by CI.

## 2026-10-29 — Preferences and Bengali layout
- Added local light/dark keyboard appearance, adjustable keyboard height, and a switchable direct Bengali character layout.
- Bengali mode uses character keys rather than phonetic transliteration; dictionary suggestions are currently English-only.
- Remaining: full dictionary/learning, clipboard history, full emoji picker, theme choices beyond light/dark, device/runtime testing, and accessibility polish.
