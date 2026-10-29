# CrazeKeyboard parity review

Date: 2026-10-29
Reference: FUTO Keyboard public UX / feature documentation. Clean-room implementation; this is a functionality gap review, not a pixel-copy target.

## Summary
- Native app and core typing: implemented.
- Settings information architecture: implemented at a preview level.
- SVG icon pipeline: source SVGs and Android VectorDrawable runtime equivalents are implemented for settings categories and the main keyboard toolbar.
- Build: latest icon toolbar commit is pending CI at time of report; prior settings and vector category build passed CI.
- Runtime/device parity: not measured because physical-device screenshots and manual QA results are not available.

## Feature assessment
- Must-have core input: mostly implemented (typing, deletion, shift, symbols, editor actions); device QA pending.
- Privacy-first/offline: implemented in manifest and local suggestion engine; verify merged manifest during release.
- Settings: searchable categories and local preferences; some controls are informational or partially wired.
- Toolbar: paste/edit/emoji/settings icons and actions; no clipboard history or full emoji picker.
- Language: English and direct Bengali layout; no phonetic transliteration or broad language models.
- Prediction: small static English word list; no autocorrection, next-word prediction or local learning.
- Missing high-value features: glide typing, offline voice, personal dictionary storage, clipboard history, comprehensive emoji picker, broader language models, full theme customization.

## Verdict
Preview only; not feature-parity complete or release-ready. Next steps: confirm latest CI, install on an Android device, run replica/test-plan.md, fix S1/S2 issues, and only then compare screenshots and behaviour.
