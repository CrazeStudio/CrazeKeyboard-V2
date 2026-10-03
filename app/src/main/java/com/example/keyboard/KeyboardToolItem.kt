package com.example.keyboard

import com.example.R

data class KeyboardToolItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val description: String
)

object KeyboardToolsCatalog {
    val TOOL_DEV_MODE = KeyboardToolItem("DEV_MODE", "Dev Mode", R.drawable.ic_terminal, "Terminal keys & modifiers")
    val TOOL_CLIPBOARD = KeyboardToolItem("CLIPBOARD", "Clipboard", R.drawable.ic_clipboard, "Recent copied clips & pins")
    val TOOL_CURSOR_NAV = KeyboardToolItem("CURSOR_NAV", "Text Edit", R.drawable.ic_cursor, "Arrow keys, select, cut, copy")
    val TOOL_FONTS = KeyboardToolItem("FONTS", "Fonts", R.drawable.ic_fonts, "Stylized fonts & cool typography")
    val TOOL_LANGUAGE = KeyboardToolItem("LANGUAGE", "Language", R.drawable.ic_globe, "Switch language & layout")
    val TOOL_THEME = KeyboardToolItem("THEME", "Theme", R.drawable.ic_palette, "Color schemes & styles")
    val TOOL_SOUND = KeyboardToolItem("SOUND", "Haptics", R.drawable.ic_sound, "Toggle sound & vibration")
    val TOOL_NUMBER_ROW = KeyboardToolItem("NUMBER_ROW", "Number Row", R.drawable.ic_sparkle, "Show/hide dedicated number row")
    val TOOL_SETTINGS = KeyboardToolItem("SETTINGS", "Settings", R.drawable.ic_settings, "Keyboard preferences")
    val TOOL_ONE_HANDED = KeyboardToolItem("ONE_HANDED", "One Hand", R.drawable.ic_tune, "One-handed mode")
    val TOOL_FLOATING = KeyboardToolItem("FLOATING", "Floating", R.drawable.ic_tune, "Floating keyboard mode")
    val TOOL_VOICE = KeyboardToolItem("VOICE", "Voice", R.drawable.ic_mic, "Voice input")
    val TOOL_SPEED_TEST = KeyboardToolItem("SPEED_TEST", "Speed Test", R.drawable.ic_speed, "Typing speed & WPM test")

    /**
     * Offline-only tool catalog. Online AI, live translation and remote GIF search
     * are intentionally not exposed from the keyboard hotbar.
     */
    val allTools: List<KeyboardToolItem> = listOf(
        TOOL_CLIPBOARD,
        TOOL_CURSOR_NAV,
        TOOL_FONTS,
        TOOL_LANGUAGE,
        TOOL_DEV_MODE,
        TOOL_THEME,
        TOOL_SOUND,
        TOOL_NUMBER_ROW,
        TOOL_SETTINGS,
        TOOL_ONE_HANDED,
        TOOL_FLOATING,
        TOOL_VOICE,
        TOOL_SPEED_TEST
    )

    val defaultHotbarIds: List<String> = listOf(
        "CLIPBOARD",
        "CURSOR_NAV",
        "FONTS",
        "LANGUAGE",
        "THEME",
        "SETTINGS"
    )

    fun getToolById(id: String): KeyboardToolItem? = allTools.firstOrNull { it.id == id }
}
