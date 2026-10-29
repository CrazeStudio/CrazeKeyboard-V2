# Build log

One line per item: ID, date, status, what is missing, what was harder than expected.

| ID | date | status | missing | notes |
| --- | --- | --- | --- | --- |
| core | 2026-10-29 | done | n/a | Pure-Java dictionary core (no Android imports): word learning, next-word pairs, shortcuts, file storage, suggestions, autocorrect, glide decoder. 88 checks pass in `run-core-tests.sh`. |
| S04 Suggestion strip | 2026-10-29 | done, not device-tested | strip does not show a highlighted best match | Old hard-coded 110-word list removed. Suggestions now computed after each key press instead of inside `onDraw`, which also fixes redrawing cost. |
| F02 Type with autocorrect | 2026-10-29 | done, not device-tested | caps lock, auto-capital after question marks | Autocorrect only uses words typed 4+ times, skips names typed mid-sentence, and Backspace right after a correction undoes it and teaches the word. |
| S28 Dictionary screen | 2026-10-29 | done, not device-tested | export/import file, per-word edit | Settings for learn, suggestions, autocorrect, glide; shortcuts list and add; search and delete words; delete all. Opened from the new toolbar button. |
| F12 Teach and clean dictionary | 2026-10-29 | done | n/a | Covered by the screen above and by `forget` / `clearWords` tests. |
| S07 / F03 Glide typing | 2026-10-29 | partial | delete-by-swipe, alternative words in the strip, tuning on real fingers | Decoder gets the right word on simulated noisy paths. Real thumb paths are messier, so thresholds (28dp to start, cost limit 1.0 key width) need tuning on a device. |
| S05 Toolbar | 2026-10-29 | partial | customise and reorder | Now five buttons (added Dictionary). |
| S08 / F04 Languages | 2026-10-29 | partial | in-app language list, blending across languages | One dictionary file per language code (`dictionary_en.txt`, `dictionary_bn.txt`). Bengali word parsing handles vowel signs and joiners (tested). |
| S12, S13, S06, S09-S11, S14-S20, S22-S27, S29-S32 | | not started | everything | Outside this build. Next: clipboard history, long-press popups, emoji panel and search, offline voice. |

## Harder than expected
- Room was planned in `architecture.md`. A plain text file store replaced it for now: no new Gradle dependency, easy to test without Android, and the same data (words, pairs, shortcuts). Room is still the target once clipboard history and themes need queries.
- Word boundaries are not just letters. Bengali vowel signs are combining marks, not letters, so a naive "is letter" check would cut words in half. The core treats marks and joiners as word characters and has tests for it.
- Autocorrect from an empty start is risky: it could change a new name into a similar learned word. The guard rails above (4+ uses, no mid-sentence capitals, undo teaches) are the answer; real-world use may need another notch of caution.
- No Android SDK was available in this session, so the Android classes (service, dictionary screen, holder) were type-checked against hand-written stubs only. The repo's GitHub Actions build is the real compile check.
