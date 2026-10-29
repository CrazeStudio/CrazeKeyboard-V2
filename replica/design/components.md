# CrazeKeyboard design system

Independent native Android design system informed by public FUTO Keyboard user documentation. It uses familiar keyboard and settings conventions but original CrazeKeyboard labels, shapes, palette, and glyph choices.

## Visual direction
- Calm, neutral surfaces with a restrained violet accent; no brand assets copied from FUTO.
- Rounded settings rows, a compact app header, short descriptions, and one obvious action per row.
- Keyboard surface is a light cool grey with white letter keys and lightly tinted action keys; dark mode uses charcoal surfaces.
- Native sans-serif typography; no proprietary font assets.
- 4dp spacing base; settings rows use 16dp interior padding and 8–10dp vertical separation.

## Components
### Settings category row
- Layout: 42dp original text-glyph tile, title, one-line explanatory subtitle, chevron.
- States: default, pressed, keyboard-focus, filtered-out by search, empty search result.
- Accessibility: whole row clickable/focusable; title is the primary label; subtitle describes function.

### Primary button
- Height: 48dp; accent background, white text, 13dp radius.
- Used for enabling keyboard and committing primary actions.
- State: pressed feedback supplied by Android; avoid all-caps.

### Secondary button
- Height: 48dp; pale neutral/violet surface, dark text.
- Used for input method picker and secondary actions.

### Setting toggle
- Native Android Switch with title and subtitle; persists in SharedPreferences.
- States: checked, unchecked, disabled/not available.
- The UI must disclose when a preference is stored but the corresponding engine is not yet wired.

### Keyboard key
- Rounded rectangle, minimum 32dp key height; 7–9dp corner radius.
- Letter keys white in light mode; actions use a soft violet tint.
- Shift, delete, symbols, enter and space have dedicated hit regions.
- Key borders are an optional preference; haptic preference is currently stored but haptics are not implemented.

### Suggestion strip
- Separate horizontal strip above the keys, three equally divided candidates.
- Suppressed for password fields, numeric fields, Bengali mode, or when suggestions are disabled.
- Empty state is a blank neutral strip; never show suggestion data in password fields.

### Settings detail page
- Back control, large section title, short description, grouped cards, vertically scrollable content.
- Language, theme, keyboard height, and suggestion settings persist locally.
- Voice input and personal dictionary are explicitly marked unavailable/planned rather than presented as functional.

## Accessibility and responsive behaviour
- Native Android controls preserve platform focus and touch handling.
- Screen is vertically scrollable and uses available width; long labels wrap.
- Minimum action heights are 42–48dp. Full TalkBack and contrast verification on device remain required.
