# Build log

## 2026-10-29 — Native IME and app shell
- Added the Android InputMethodService, QWERTY/symbol layers, shift, backspace, space, enter/editor actions, and setup screen.
- Manifest intentionally has no Internet permission.

## 2026-10-29 — Offline suggestions and editing toolbar
- Added a small offline word-list suggestion strip, clipboard paste, copy/cut selected text, select-all, emoji insertion, and settings shortcut.
- Suggestions are hidden for password fields, numeric fields and Bengali layout; settings can disable suggestions.

## 2026-10-29 — Settings design pass (FUTO-inspired, independent implementation)
- Rebuilt the settings home with searchable categories, neutral cards, compact typography, and original CrazeKeyboard branding.
- Added separate Languages & models, Keyboard & typing, Text prediction, Voice input, Personal dictionary, Theme, Help & feedback, and Developer settings pages.
- Added height control and key-border/suggestion/dark-mode preferences. Haptic preference currently stores state but is not connected to vibration.
- Added design tokens, component specifications, and feature matrix updates.
- Known limitations: no glide typing, no offline voice model, no full emoji picker, no clipboard history, no personal dictionary storage, no phonetic Bengali typing, and no runtime device QA.
- Latest commit must pass CI before treating this design pass as build-verified.
