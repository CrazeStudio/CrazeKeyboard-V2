package com.example.keyboard

import com.example.R

data class KeyboardToolItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val description: String
)

object KeyboardToolsCatalog {
    val TOOL_DEV_MODE = KeyboardToolItem(
        id = "DEV_MODE",
        title = "Dev Mode",
        iconRes = R.drawable.ic_terminal,
        description = "Terminal keys & modifiers"
    )
    val TOOL_CLIPBOARD = KeyboardToolItem(
        id = "CLIPBOARD",
        title = "Clipboard",
        iconRes = R.drawable.ic_clipboard,
        description = "Recent copied clips & pins"
    )
    val TOOL_CURSOR_NAV = KeyboardToolItem(
        id = "CURSOR_NAV",
        title = "Text Edit",
        iconRes = R.drawable.ic_cursor,
        description = "Arrow keys, select, cut, copy"
    )
    val TOOL_FONTS = KeyboardToolItem(
        id = "FONTS",
        title = "Fonts",
        iconRes = R.drawable.ic_fonts,
        description = "Stylized fonts & cool typography"
    )
    val TOOL_LANGUAGE = KeyboardToolItem(
        id = "LANGUAGE",
        title = "Language",
        iconRes = R.drawable.ic_globe,
        description = "Switch language & layout"
    )
    val TOOL_THEME = KeyboardToolItem(
        id = "THEME",
        title = "Theme",
        iconRes = R.drawable.ic_palette,
        description = "Color schemes & styles"
    )
    val TOOL_SOUND = KeyboardToolItem(
        id = "SOUND",
        title = "Haptics",
        iconRes = R.drawable.ic_sound,
        description = "Toggle sound & vibration"
    )
    val TOOL_NUMBER_ROW = KeyboardToolItem(
        id = "NUMBER_ROW",
        title = "Number Row",
        iconRes = R.drawable.ic_sparkle,
        description = "Show/hide dedicated number row"
    )
    val TOOL_SETTINGS = KeyboardToolItem(
        id = "SETTINGS",
        title = "Settings",
        iconRes = R.drawable.ic_settings,
        description = "Keyboard preferences"
    )
    
    val TOOL_ONE_HANDED = KeyboardToolItem(
        id = "ONE_HANDED",
        title = "One Hand",
        iconRes = R.drawable.ic_tune,
        description = "One-handed mode"
    )

    val TOOL_FLOATING = KeyboardToolItem(
        id = "FLOATING",
        title = "Floating",
        iconRes = R.drawable.ic_tune,
        description = "Floating keyboard mode"
    )
    val TOOL_VOICE = KeyboardToolItem(
        id = "VOICE",
        title = "Voice",
        iconRes = R.drawable.ic_mic,
        description = "Voice input"
    )

    val TOOL_GIF = KeyboardToolItem(
        id = "GIF",
        title = "GIFs",
        iconRes = R.drawable.ic_gif,
        description = "Trending GIFs"
    )

    val TOOL_TRANSLATE = KeyboardToolItem(
        id = "TRANSLATE",
        title = "Translate",
        iconRes = R.drawable.ic_translate,
        description = "Live translation in 50+ languages"
    )

    val TOOL_SPEED_TEST = KeyboardToolItem(
        id = "SPEED_TEST",
        title = "Speed Test",
        iconRes = R.drawable.ic_speed,
        description = "Typing speed & WPM test"
    )

    val TOOL_AI = KeyboardToolItem(
        id = "AI",
        title = "AI Chat",
        iconRes = R.drawable.ic_ai,
        description = "Gemma 4 AI Assistant"
    )

    val allTools: List<KeyboardToolItem> = listOf(
        TOOL_AI,
        TOOL_TRANSLATE,
        TOOL_GIF,
        TOOL_CLIPBOARD,
        TOOL_CURSOR_NAV,
        TOOL_FONTS,
        TOOL_LANGUAGE,
        TOOL_DEV_MODE,
        TOOL_THEME,
        TOOL_SOUND,
        TOOL_NUMBER_ROW,
        TOOL_SETTINGS,
        TOOL_VOICE
    )

    val defaultHotbarIds: List<String> = listOf(
        "AI",
        "TRANSLATE",
        "GIF",
        "CLIPBOARD",
        "CURSOR_NAV",
        "FONTS",
        "LANGUAGE"
    )

    fun getToolById(id: String): KeyboardToolItem? {
        return allTools.firstOrNull { it.id == id }
    }
}
