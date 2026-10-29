# Build log

## 2026-10-29 — Native IME and app shell
- Added Android InputMethodService, QWERTY/symbol layers, shift, backspace, space, enter/editor actions, and setup screen.
- Manifest intentionally has no Internet permission.

## 2026-10-29 — Offline suggestions and editing toolbar
- Added a small offline word-list suggestion strip, clipboard paste, copy/cut selected text, select-all, emoji insertion, and settings shortcut.
- Suggestions are hidden for password fields, numeric fields and Bengali layout; settings can disable suggestions.

## 2026-10-29 — FUTO-inspired settings and design tokens
- Rebuilt settings home with searchable categories, neutral cards, compact typography and original CrazeKeyboard branding.
- Added category detail screens for languages, keyboard/typing, prediction, voice, personal dictionary, theme, help and developer settings.
- Added design tokens and component specs.

## 2026-10-29 — SVG icon system
- Added original local SVG source icons plus Android VectorDrawable XML equivalents for settings categories and toolbar actions.
- Replaced settings category Unicode glyphs and chevron with native vector assets; no runtime SVG dependency or network fetch.
- Build and test verification tracked in GitHub Actions and replica/test-plan.md.
- Known limitations remain: no glide typing, offline voice model, full emoji picker, clipboard history, personal dictionary storage, phonetic Bengali typing, or physical-device QA.
