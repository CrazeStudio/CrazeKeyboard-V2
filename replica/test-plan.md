# CrazeKeyboard test plan

## Scope
Native Android InputMethodService, setup activity, settings categories, vector icons, text insertion and offline suggestions. The original FUTO app is not automated or stress-tested; tests apply only to CrazeKeyboard.

## Core flow F01 — Enable and type
- F01-H1: Open app → tap Enable keyboard → Android IME settings opens.
- F01-H2: Return → Choose keyboard → system IME picker appears.
- F01-H3: Select CrazeKeyboard in a plain text field; type letters, space, punctuation and enter.
- F01-E1: Rotate screen and verify keyboard remains usable.
- F01-E2: Test long editor content and Unicode/emoji deletion.
- F01-N1: Open a password field; suggestion strip must not expose text suggestions.

## F02 — Symbols and editing
- F02-H1: Switch ?123 → insert digits and punctuation → return to ABC.
- F02-H2: Select text in host editor → open edit toolbar → copy/cut/paste/select all.
- F02-E1: Empty clipboard; paste should not crash or insert unrelated content.
- F02-E2: Selected text contains emoji and accented characters.
- F02-N1: Editor is read-only or InputConnection is unavailable; no crash.

## F03 — Suggestions
- F03-H1: Type a known prefix such as `hel`; check the local candidate strip.
- F03-H2: Tap a candidate and verify word replacement plus trailing space.
- F03-E1: Suggestions disabled in settings; strip should remain empty.
- F03-E2: Switch to Bengali or numeric mode; suggestions remain suppressed.
- F03-N1: Password field; suggestions remain suppressed.

## F04 — Preferences and settings
- F04-H1: Search category names and open each settings page.
- F04-H2: Change language, theme, keyboard height, key borders and suggestions; relaunch and check persistence.
- F04-E1: Search query has no matches; helpful empty state appears.
- F04-E2: Long Bengali subtitle wraps without clipping.
- F04-N1: Repeatedly open/back across settings pages; no crash or stale state.

## F05 — SVG / VectorDrawable assets
- F05-H1: Open settings home; all eight category icons and chevrons render.
- F05-H2: Open keyboard; clipboard, edit, emoji and settings toolbar icons render without SVG runtime dependencies.
- F05-E1: Verify icons on light/dark keyboard and at different screen densities.
- F05-N1: Verify no missing-resource build errors; all XML vector paths compile.

## F06 — Privacy and permissions
- F06-H1: Inspect merged manifest; INTERNET permission absent.
- F06-N1: Airplane mode enabled; typing and settings still function.

## Automated verification
- GitHub Actions runs `gradle assembleDebug` and uploads `crazekeyboard-debug`.
- Manual device checks are still pending; CI build success is not equivalent to runtime QA.
