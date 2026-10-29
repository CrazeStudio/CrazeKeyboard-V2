# CrazeKeyboard UI component spec — SVG icon pass

## Icon system
- Source assets: original, locally authored SVGs in `replica/design/icons/`.
- Android runtime assets: matching Android VectorDrawable XMLs in `app/src/main/res/drawable/`. Android Views render these vector resources natively; no SVG parser, external CDN, or network access is required.
- Icon names: language, keyboard, prediction, voice, dictionary, theme, help, developer, chevron, clipboard, edit, emoji, settings.
- Icon canvas: 24 × 24; settings category tiles render at 42dp; icons are padded to 24dp internal area.
- Style: open-stroke outline icons, consistent 1.8dp stroke, rounded caps/joins, Craze violet for categories and neutral ink for toolbar actions.
- Accessibility: category icons have content descriptions; the row itself is the clickable target. Decorative icon descriptions should not duplicate the full row label in TalkBack if later converted to compound accessibility nodes.

## Settings category row
- Layout: 42dp icon tile, title, one-line explanatory subtitle, 24dp chevron.
- States: default, pressed, keyboard focus, filtered out by search, empty search result.
- Entire row clickable and focusable.

## Buttons
- Primary: 48dp high, violet background, white label, rounded 13dp.
- Secondary: 48dp high, pale surface and dark label.
- Back navigation stays text-based for explicit clarity.

## Keyboard
- Keyboard view uses a cool-grey surface, white keycaps, violet-tinted action keys, rounded 7dp corners.
- Main toolbar and edit toolbar remain custom Canvas-drawn. SVG source assets are included for clipboard/edit/emoji/settings so the next UI rendering pass can migrate these to vector icons without adding dependencies.
- Password/numeric fields must not expose suggestions.

## Settings detail
- Scrollable detail screen with title, short description, grouped cards and native preference controls.
- Local-only settings stored in SharedPreferences.
