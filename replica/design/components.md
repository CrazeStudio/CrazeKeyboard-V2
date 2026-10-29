# Component specs

Source: the component list in `../recon.md`. Values come from `tokens.json` (light) and `tokens.dark.json` (dark). Sizes are dp (sp for text). All labels and empty-state text are written fresh; icons come from an open set (Tabler, MIT) or are drawn in-house. Nothing here is traced from the original.

Global rules
- Touch targets are at least 48dp, even when the drawn shape is smaller (hit area padding).
- Every interactive element has a content description. Keys announce the character (and "capital A" under shift).
- A visible focus ring (2dp, `accent`) shows for keyboard/D-pad and switch-access navigation.
- Pressed feedback is immediate (0ms) and never depends on colour alone: pressed keys also show the key popup or a shape change.
- Reduced motion: all transitions drop to 0ms.
- Light and dark come from the same roles; never read a raw hex in component code.

```
Key
  variants  letter, modifier (shift, symbols, globe), action (enter), space, backspace
  sizes     height key-row 52dp x scale (0.8 to 1.3, user setting); gap 4dp; width by layout weight
  states    default, pressed, long-press (popup open), repeat (backspace), shift-on, caps-lock, disabled
  tokens    letter: surface + text, radius key 8dp, type key 22sp
            modifier: surface-modifier + text, type key-small 16sp
            action: accent + on-accent
            pressed: surface-pressed
            hint (corner symbol): text-hint, type key-hint 11sp
  a11y      description = character; shift announces on/off/caps lock; backspace repeat gives a tick
            haptic and sound follow user settings
  used on   S02, S03

Key popup
  variants  preview (above the pressed key), long-press tray (row of alternates)
  states    shown, hidden, alternate-highlighted, clipped-at-screen-edge (shifts inward)
  tokens    surface + text, radius md 12dp, elevation popup 8dp, type key 22sp
  a11y      tray options are reachable by slide; each announces the character
  used on   S02, S06

Suggestion chip (strip)
  variants  word, correction (highlighted), emoji, placeholder-empty
  sizes     strip height 48dp; up to 3 chips; dividers 1dp border
  states    default, pressed, highlighted (best match), disabled (password field: strip hidden)
  tokens    bg strip, text, highlighted uses accent text on surface, type strip 16sp
  a11y      each chip: "Suggestion: <word>"; correction chip says "Correction"
  used on   S04

Toolbar button
  variants  icon only; icon with active dot (clipboard on, voice listening)
  sizes     48dp target, 24dp icon
  states    default, pressed, active, disabled, overflow (menu)
  tokens    text-muted icon, accent when active, surface-pressed on press, radius pill
  a11y      real labels ("Clipboard"); overflow button announces hidden count
  used on   S05

Panel header and tabs
  variants  emoji / stickers / search, scrollable category row
  sizes     tab 48dp tall
  states    default, selected (2dp accent underline + text), pressed, disabled
  tokens    text-muted, text for selected, accent underline, type caption 12sp
  a11y      tab role, selected state announced, left/right swipe changes tab
  used on   S09, S10

Search field
  variants  emoji search, sticker search, dictionary search
  sizes     height 40dp inside a 48dp row, radius pill
  states    empty (placeholder), typing, has-text (clear button), no-results, disabled
  tokens    surface, border-input (3:1 required), text, placeholder text-muted
  a11y      labelled; clear button labelled; result count announced
  used on   S09, S10, S28

Grid cell
  variants  emoji, sticker, theme colour swatch
  sizes     48dp target; emoji glyph 28sp
  states    default, pressed, selected, loading (placeholder block), unsupported (greyed with note)
  tokens    surface-pressed on press, radius md
  a11y      emoji name announced; stickers need a text label
  used on   S09, S10

List row
  variants  language, clipboard item, dictionary word, settings entry
  sizes     min height 56dp; two-line variant 72dp
  states    default, pressed, selected, pinned (pin icon), swipe-to-delete revealing danger action, disabled
  tokens    text + text-muted secondary line, border divider, danger for delete
  a11y      row announces title, secondary text and state; delete and pin have accessible custom actions
  used on   S12, S22, S28, S21-S31

Toggle and slider
  variants  switch, slider with value label, segmented choice
  sizes     48dp row target
  states    off, on, disabled, focus, dragging
  tokens    accent when on, border-input track off (3:1), on-accent thumb on accent
  a11y      switch role, value announced as a percentage for sliders; step buttons for fine control
  used on   S21-S31

Theme tile
  variants  built-in, custom (add tile)
  sizes     tile shows a mini keyboard preview, 96dp x 72dp
  states    default, selected (accent border 2dp and check), pressed
  tokens    radius lg 16dp, border, accent for selected
  a11y      "Theme <name>, selected"; the preview is decorative
  used on   S24

Trail overlay (glide)
  variants  single trail
  states    drawing (fades after 300ms), committed
  tokens    accent at 80% opacity, 4dp stroke, round caps
  a11y      decorative; the committed word is announced after the gesture
  used on   S07

Mic indicator
  variants  idle, listening, processing, error
  states    listening shows an animated level ring (static ring under reduced motion)
  tokens    accent ring, danger on error, on-accent icon
  a11y      state changes announced ("Listening", "Stopped", "Microphone permission needed")
  used on   S14

Dialog / consent sheet
  variants  confirm, permission explainer, destructive confirm
  sizes     width 90% up to 360dp
  states    shown, loading, dismissed
  tokens    surface, radius lg 16dp, elevation popup, primary button accent, destructive button danger
  a11y      focus trapped, title read first, back dismisses non-destructive dialogs
  used on   S14, S20, S28 (delete learned words)

Resize handle (floating mode)
  variants  corner drag, drag bar
  sizes     24dp visible, 48dp target
  states    default, dragging (shows size outline), disabled when docked
  tokens    border-input on surface, radius pill
  a11y      move and resize also available as buttons for non-drag users
  used on   S18
```

Layout rules
- Keyboard width: full screen width on phones, capped at 600dp on tablets or when split.
- Row order top to bottom: suggestion strip (48dp), key rows (4 or 5 x 52dp x scale), bottom row (symbols, globe, comma, space, period, enter).
- One-handed mode: keyboard width 80% of screen, side buttons 48dp wide.
- Settings screens: single column, 16dp side padding, 56dp rows, headline 28sp collapsing to title 22sp.
- Breakpoints: compact under 600dp, medium 600 to 840dp (centre keyboard, cap width), expanded above that (offer split or floating).

Original assets
- Icons: Tabler (MIT) or in-house vector drawables; no icons copied from Gboard.
- Fonts: system sans; no proprietary typeface bundled.
- Colour: roles recorded; values are placeholders until `/replica-brand`.
