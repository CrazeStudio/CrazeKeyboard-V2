# Gboard-Style Unified Toolbar, Seamless Suggestions & Dark Slate Theme

Transform CrazeKeyboard's top strip and visual presentation into an authentic Gboard replica featuring a unified bar that seamlessly toggles between word suggestions and quick tools, borderless text candidate suggestions separated by subtle vertical dividers, and the official Material You Dark Slate aesthetic with a vibrant Google Blue pill action/Enter key.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions and UX behaviors were confirmed based on user feedback:

- **Unified Header Strip**: Merges the separate toolbar and suggestion row into a single 40dp Gboard-style bar. A left chevron/tools icon toggles the row between quick tools (Emoji, Clipboard, Text Editor, Theme, Settings) and word candidate suggestions. Typing automatically activates candidate suggestions.
- **Seamless Candidate Word Suggestions**: Candidate words are displayed as clean, borderless text elements directly on the bar surface (eliminating bulky bubble pills), separated by subtle vertical divider lines (`|`), with the primary auto-complete candidate emphasized in bold high-contrast white.
- **Material You Dark Slate Color Scheme**: The default keyboard theme is updated to authentic Gboard Dark Slate (`#1B1C20` base canvas, `#2C2D33` keycaps, `#212226` functional keys, `#FFFFFF` crisp primary text, and `#1A73E8` vibrant Google Blue action/Enter key).
- **Single Download Folder for APK**: Preserves user's preference to maintain clean builds in the download directory.

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the redundant stacked toolbar and suggestions rows with a single, highly responsive Gboard top bar. While typing, users receive instantaneous word predictions separated by hairline dividers; when idle or tapped, the bar reveals essential tools (Clipboard, Cursor Navigation, Theme Switcher, Emoji).
- **Target Audience**: Users seeking an authentic, ultra-responsive Gboard-like experience on Android with zero input lag, intuitive layout navigation, and a modern Material You dark aesthetic.
- **Key Value**: Drastically increases screen real estate for the keyboard keys by collapsing two rows into one, eliminates visual clutter, and delivers instant, tactile feedback during fast multi-touch typing.

---

## 2. User Experience & Visual Design

### Unified Toolbar & Suggestion Strip Lifecycle

1. **Typing State (Default when text exists or is entered)**:
   - Far-Left: A compact chevron/grid icon (`>`) to expand quick tools on demand.
   - Center Area: Three candidate words dynamically calculated by the dictionary based on current prefix (`[ previous ] | [ Primary (Bold) ] | [ next ]`). Tapping commits the suggestion with auto-spacing.
   - Far-Right: Voice/Mic or expand button icon aligned with Gboard.
2. **Quick Tools State (When tapped or when cursor is at empty space)**:
   - Displays 5 evenly spaced, sleek icon buttons:
     - **Emoji / Emoticons**: Opens the bottom-tabbed emoji drawer.
     - **Clipboard Manager**: Opens instant-paste copied snippets.
     - **Text Editing Pad ("3")**: Opens directional D-pad with Select All, Cut, Copy, Paste.
     - **Theme Palette**: Cycles through curated natural themes.
     - **Settings**: Opens keyboard preference settings.
   - Any keypress instantly transitions the strip back to suggestions mode.

### Material You Dark Slate Visual Tokens

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI Element                  Color Token        Hex Value               │
├────────────────────────────────────────────────────────────────────────┤
│ Keyboard Canvas / Background bg_slate          #1B1C20                 │
│ Top Strip Background        strip_bg          #1B1C20                 │
│ Primary Keycap Surface      key_surface       #2C2D33                 │
│ Functional Keys (Shift/Del) key_functional    #212226                 │
│ Action / Enter Key Pill     accent_google_blue#1A73E8                 │
│ Key Press Feedback State    key_pressed       #3C3E47                 │
│ Primary Character Text      text_primary      #FFFFFF                 │
│ Secondary / Symbol Hint     text_secondary    #9E A3 B0               │
│ Candidate Divider Line      divider_color     #34363E                 │
└────────────────────────────────────────────────────────────────────────┘
```

### Visual Polish & Spatial Precision
- **Keycap Geometry**: Rounded rectangular keys with 8dp corner radius and smooth padding, ensuring no cramped or misaligned rows on alphanumeric or ?123 pages.
- **Action Key**: Distinctive rounded pill in Google Blue (`#1A73E8`) with crisp white vector action icon (Search, Done, Send, or Enter return arrow).
- **Divider Lines**: 1dp wide subtle hairline dividers centered vertically between suggestion slots.

---

## 3. Key Product Decisions & Trade-Offs

- **Unified Strip vs. Stacked Rows**:
  - *Chosen Approach*: Single 40dp strip with mode switching (Quick Tools vs. Suggestions).
  - *Why*: Reduces keyboard vertical footprint by 36dp, matches Gboard layout 1:1, and keeps the screen open for reading messages and documents.
  - *Alternative Considered*: Keeping two stacked rows was rejected because it wastes vertical screen space and feels unpolished.
- **Seamless Text Suggestions vs. Rounded Pill Chips**:
  - *Chosen Approach*: Borderless text candidate words separated by subtle vertical dividers, center word in bold white.
  - *Why*: Direct user selection; matches official Gboard design, providing a cleaner, more readable interface without button border clutter.
- **Fast Non-Blocking Event Dispatch**:
  - *Chosen Approach*: Retain `splitMotionEvents = true` with direct view canvas drawing for low-latency touch dispatch, avoiding slow view recreation during rapid text updates.

---

## 4. Technical Architecture & Component Structure

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        CrazeInputMethodService                            │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Unified Top Strip (Height: 40dp)                                    │  │
│  │ ┌───────┐ ┌───────────────────────────────────────────────┐ ┌─────┐ │  │
│  │ │ [ > ] │ │  secondary  │   Primary Word (Bold)   │ next  │ │ [︙] │ │  │
│  │ └───────┘ └───────────────────────────────────────────────┘ └─────┘ │  │
│  │   ▲                                                           ▲     │  │
│  │   └────── Toggles Quick Tools: [Emoji][Clip][Edit][Theme][Set] ┘     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Dynamic Panel Container (Swappable Content)                         │  │
│  │   • LatinKeyboardView (QWERTY / ?123 / =<\\ layouts)                │  │
│  │   • EmojiPanelView (Bottom-tabbed category drawer)                  │  │
│  │   • ClipboardPanelView (Recent copied chips)                        │  │
│  │   • TextEditPanelView (D-Pad cursor navigation & clipboard actions) │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

### Component & State Logic Mapping

1. **Unified Top Strip Controller**:
   - Manages an internal state `StripMode.SUGGESTIONS` vs `StripMode.TOOLS`.
   - In `SUGGESTIONS` mode: Hosts a left toggle button, 3 equal-width candidate `TextView` items with 2 vertical hairline divider views, and an optional right quick action.
   - In `TOOLS` mode: Replaces candidate slots with 5 functional tool buttons.
   - Intercepts text commits and keypresses to automatically revert to `SUGGESTIONS` when the user types.
2. **Material You Dark Slate Palette**:
   - Configures "Material You Dark Slate" as the primary default theme across `CrazeInputMethodService`, `LatinKeyboardView`, and companion preview in `MainActivity`.
   - Key borders, surfaces, text, and enter key colors align with the specified palette tokens.
3. **Punctuation & ?123 Layout Polish**:
   - Clean spacing and proportions on the `?123` symbol and more-symbols pages to eliminate awkward button stretching.
   - Multi-touch and rapid word deletion with backspace acceleration remain smooth and non-blocking.
4. **Verification & Single APK Output**:
   - Compile and verify using `compile_applet`.
   - Export single APK file to `/download/CrazeKeyboard.apk`.
