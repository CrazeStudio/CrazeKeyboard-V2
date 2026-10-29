# CrazeKeyboard bug and QA tracker

## Open / to verify
- QA-001 — S2 — Physical-device runtime verification is pending. Steps: install latest debug APK on Android 12 device, enable IME, type into common apps, rotate, test TalkBack. Expected: no crashes, correct resizing, accessible controls. Status: to check; not reproduced.
- QA-002 — S2 — Suggestions and editor behaviour need a password/numeric/editorType matrix. Expected: no suggestions in sensitive fields and no stale candidates after switching apps. Status: to check.
- QA-003 — S3 — Haptic feedback setting is stored but not connected to vibration. Expected: either implement feedback or disable/label the preference as unavailable. Status: known limitation.
- QA-004 — S3 — Personal dictionary screen is informational only; Save word does not persist data. Expected: implement local storage or make the control clearly unavailable. Status: known limitation.
- QA-005 — S3 — Theme key-border and height controls require physical-device visual verification. Status: to check.
- QA-006 — S3 — Toolbar icon assets are original local SVG source plus native Android VectorDrawable equivalents; confirm stroke visibility and touch target spacing on low-density displays. Status: to check.

## Fixed
- None recorded in this QA cycle. Build verification is recorded in GitHub Actions, not treated as a runtime bug fix.
