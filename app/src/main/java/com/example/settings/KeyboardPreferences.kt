package com.example.settings


import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class KeyboardPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("crazekeyboard_prefs", Context.MODE_PRIVATE)

    var themeId by mutableStateOf(prefs.getString("theme_id", "system") ?: "system")
        private set

    var keyHeightDp by mutableIntStateOf(prefs.getInt("key_height_dp", 50).let { if (it < 36) 50 else it })
        private set

    
    var layoutMode by mutableStateOf(KeyboardLayoutMode.valueOf(prefs.getString("layout_mode", "DEFAULT") ?: "DEFAULT"))
        private set
    var keySpacingDp by mutableIntStateOf(prefs.getInt("key_spacing_dp", 5))
        private set

    var keyCornerRadiusDp by mutableIntStateOf(prefs.getInt("key_corner_radius_dp", 8))
        private set

    var longPressDelayMs by mutableIntStateOf(prefs.getInt("long_press_delay_ms", 220).coerceIn(150, 500))
        private set

    var showKeyBorders by mutableStateOf(prefs.getBoolean("show_key_borders", true))
        private set

    var isHapticsEnabled by mutableStateOf(prefs.getBoolean("is_haptics_enabled", true))
        private set

    var hapticIntensityMs by mutableIntStateOf(prefs.getInt("haptic_intensity_ms", 15))
        private set

    var hapticProfile by mutableStateOf(prefs.getString("haptic_profile", "system") ?: "system")
        private set

    var hapticIntensityPercent by mutableIntStateOf(prefs.getInt("haptic_intensity_percent", 65).coerceIn(5, 100))
        private set

    var isStabilizerSimulationEnabled by mutableStateOf(prefs.getBoolean("is_stabilizer_sim_enabled", true))
        private set

    var isKeySpecificHapticsEnabled by mutableStateOf(prefs.getBoolean("is_key_specific_haptics_enabled", true))
        private set

    var isSoundEnabled by mutableStateOf(prefs.getBoolean("is_sound_enabled", true))
        private set

    var soundVolume by mutableIntStateOf(prefs.getInt("sound_volume", 80))
        private set

    var soundProfile by mutableStateOf(prefs.getString("sound_profile", "iphone") ?: "iphone")
        private set

    var isLocalAiEmojiEnabled by mutableStateOf(prefs.getBoolean("is_local_ai_emoji_enabled", true))
        private set

    var isDeveloperModeEnabled by mutableStateOf(prefs.getBoolean("is_dev_mode_enabled", false))
        private set

    var isNumberRowEnabled by mutableStateOf(prefs.getBoolean("is_number_row_enabled", false))
        private set

    var isSuggestionsEnabled by mutableStateOf(prefs.getBoolean("is_suggestions_enabled", true))
        private set

    var isAutoCorrectEnabled by mutableStateOf(prefs.getBoolean("is_auto_correct_enabled", true))
        private set

    var autoCorrectLevel by mutableStateOf(prefs.getString("auto_correct_level", "balanced") ?: "balanced")
        private set

    var isNextWordPredictionEnabled by mutableStateOf(prefs.getBoolean("is_next_word_pred_enabled", true))
        private set

    var isAutoCorrectAnimationEnabled by mutableStateOf(prefs.getBoolean("is_auto_correct_anim_enabled", true))
        private set

    var isAutoCapEnabled by mutableStateOf(prefs.getBoolean("is_auto_cap_enabled", true))
        private set

    var isDoubleSpacePeriodEnabled by mutableStateOf(prefs.getBoolean("is_double_space_period_enabled", true))
        private set

    var isSpaceSwipeCursorEnabled by mutableStateOf(prefs.getBoolean("is_space_swipe_cursor", true))
        private set

    var isBackspaceSwipeDeleteEnabled by mutableStateOf(prefs.getBoolean("is_backspace_swipe_delete", true))
        private set

    var isGlideTypingEnabled by mutableStateOf(prefs.getBoolean("is_glide_typing_enabled", true))
        private set

    var isPopupPreviewEnabled by mutableStateOf(prefs.getBoolean("is_popup_preview_enabled", true))
        private set

    var currentLanguage by mutableStateOf(prefs.getString("current_language", "en") ?: "en")
        private set

    var currentFontStyle by mutableStateOf(prefs.getString("current_font_style", "normal") ?: "normal")
        private set

    // Custom Theme Colors (Infinite Customization)
    var isCustomThemeEnabled by mutableStateOf(prefs.getBoolean("is_custom_theme_enabled", false))
        private set

    var customBgColorHex by mutableStateOf(prefs.getString("custom_bg_hex", "#1E1E2E") ?: "#1E1E2E")
        private set

    var customKeyColorHex by mutableStateOf(prefs.getString("custom_key_hex", "#313244") ?: "#313244")
        private set

    var customAccentColorHex by mutableStateOf(prefs.getString("custom_accent_hex", "#CBA6F7") ?: "#CBA6F7")
        private set

    var customTextColorHex by mutableStateOf(prefs.getString("custom_text_hex", "#CDD6F4") ?: "#CDD6F4")
        private set

    var clipboardUnpinnedLimit by mutableStateOf(prefs.getInt("clipboard_unpinned_limit", 10))
        private set

    var recentEmojis by mutableStateOf(
        prefs.getString("recent_emojis", "👍,❤️,😊,🔥,✨,🎉,🚀,💻,⚡,🙌,👏,💡,🤖,✅,😍,🤣,🎂,🥳,☕,🍕")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: listOf("👍", "❤️", "😊", "🔥", "✨", "🎉", "🚀", "💻", "⚡", "🙌", "👏", "💡", "🤖", "✅", "😍", "🤣", "🎂", "🥳", "☕", "🍕")
    )
        private set

    var hotbarTools by mutableStateOf(
        prefs.getString("hotbar_tools", "CLIPBOARD,CURSOR_NAV,FONTS,LANGUAGE,THEME,SETTINGS")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: listOf("CLIPBOARD", "CURSOR_NAV", "FONTS", "LANGUAGE", "THEME", "SETTINGS")
    )
        private set

    var isFirstRun by mutableStateOf(prefs.getBoolean("is_first_run", true))
        private set

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "is_first_run" -> isFirstRun = prefs.getBoolean("is_first_run", true)
            "theme_id" -> themeId = prefs.getString("theme_id", "system") ?: "system"
            "key_height_dp" -> keyHeightDp = prefs.getInt("key_height_dp", 50).let { if (it < 36) 50 else it }
            "key_spacing_dp" -> keySpacingDp = prefs.getInt("key_spacing_dp", 5)
            "key_corner_radius_dp" -> keyCornerRadiusDp = prefs.getInt("key_corner_radius_dp", 8)
            "long_press_delay_ms" -> longPressDelayMs = prefs.getInt("long_press_delay_ms", 220).coerceIn(150, 500)
            "show_key_borders" -> showKeyBorders = prefs.getBoolean("show_key_borders", true)
            "is_haptics_enabled" -> isHapticsEnabled = prefs.getBoolean("is_haptics_enabled", true)
            "haptic_intensity_ms" -> hapticIntensityMs = prefs.getInt("haptic_intensity_ms", 15)
            "is_sound_enabled" -> isSoundEnabled = prefs.getBoolean("is_sound_enabled", true)
            "sound_volume" -> soundVolume = prefs.getInt("sound_volume", 80)
            "sound_profile" -> soundProfile = prefs.getString("sound_profile", "iphone") ?: "iphone"
            "is_local_ai_emoji_enabled" -> isLocalAiEmojiEnabled = prefs.getBoolean("is_local_ai_emoji_enabled", true)
            "is_dev_mode_enabled" -> isDeveloperModeEnabled = prefs.getBoolean("is_dev_mode_enabled", false)
            "is_number_row_enabled" -> isNumberRowEnabled = prefs.getBoolean("is_number_row_enabled", false)
            "is_suggestions_enabled" -> isSuggestionsEnabled = prefs.getBoolean("is_suggestions_enabled", true)
            "is_auto_correct_enabled" -> isAutoCorrectEnabled = prefs.getBoolean("is_auto_correct_enabled", true)
            "auto_correct_level" -> autoCorrectLevel = prefs.getString("auto_correct_level", "balanced") ?: "balanced"
            "is_next_word_pred_enabled" -> isNextWordPredictionEnabled = prefs.getBoolean("is_next_word_pred_enabled", true)
            "is_auto_correct_anim_enabled" -> isAutoCorrectAnimationEnabled = prefs.getBoolean("is_auto_correct_anim_enabled", true)
            "is_auto_cap_enabled" -> isAutoCapEnabled = prefs.getBoolean("is_auto_cap_enabled", true)
            "is_double_space_period_enabled" -> isDoubleSpacePeriodEnabled = prefs.getBoolean("is_double_space_period_enabled", true)
            "is_space_swipe_cursor" -> isSpaceSwipeCursorEnabled = prefs.getBoolean("is_space_swipe_cursor", true)
            "is_backspace_swipe_delete" -> isBackspaceSwipeDeleteEnabled = prefs.getBoolean("is_backspace_swipe_delete", true)
            "is_glide_typing_enabled" -> isGlideTypingEnabled = prefs.getBoolean("is_glide_typing_enabled", true)
            "is_popup_preview_enabled" -> isPopupPreviewEnabled = prefs.getBoolean("is_popup_preview_enabled", true)
            "current_language" -> currentLanguage = prefs.getString("current_language", "en") ?: "en"
            "current_font_style" -> currentFontStyle = prefs.getString("current_font_style", "normal") ?: "normal"
            "is_custom_theme_enabled" -> isCustomThemeEnabled = prefs.getBoolean("is_custom_theme_enabled", false)
            "custom_bg_hex" -> customBgColorHex = prefs.getString("custom_bg_hex", "#1E1E2E") ?: "#1E1E2E"
            "custom_key_hex" -> customKeyColorHex = prefs.getString("custom_key_hex", "#313244") ?: "#313244"
            "custom_accent_hex" -> customAccentColorHex = prefs.getString("custom_accent_hex", "#CBA6F7") ?: "#CBA6F7"
            "custom_text_hex" -> customTextColorHex = prefs.getString("custom_text_hex", "#CDD6F4") ?: "#CDD6F4"
            "recent_emojis" -> {
                recentEmojis = prefs.getString("recent_emojis", "👍,❤️,😊,🔥,✨,🎉,🚀,💻,⚡,🙌,👏,💡,🤖,✅,😍,🤣,🎂,🥳,☕,🍕")
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: listOf("👍", "❤️", "😊", "🔥", "✨", "🎉", "🚀", "💻", "⚡", "🙌", "👏", "💡", "🤖", "✅", "😍", "🤣", "🎂", "🥳", "☕", "🍕")
            }
            "hotbar_tools" -> {
                hotbarTools = prefs.getString("hotbar_tools", "CLIPBOARD,CURSOR_NAV,FONTS,LANGUAGE,THEME,SETTINGS")
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?: listOf("CLIPBOARD", "CURSOR_NAV", "FONTS", "LANGUAGE", "THEME", "SETTINGS")
            }
            "clipboard_unpinned_limit" -> clipboardUnpinnedLimit = prefs.getInt("clipboard_unpinned_limit", 10)
            "haptic_profile" -> hapticProfile = prefs.getString("haptic_profile", "system") ?: "system"
            "haptic_intensity_percent" -> hapticIntensityPercent = prefs.getInt("haptic_intensity_percent", 65).coerceIn(5, 100)
            "is_stabilizer_sim_enabled" -> isStabilizerSimulationEnabled = prefs.getBoolean("is_stabilizer_sim_enabled", true)
            "is_key_specific_haptics_enabled" -> isKeySpecificHapticsEnabled = prefs.getBoolean("is_key_specific_haptics_enabled", true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }


    fun updateLayoutMode(mode: KeyboardLayoutMode) {
        layoutMode = mode
        prefs.edit().putString("layout_mode", mode.name).apply()
    }

    fun reloadAll() {
        isFirstRun = prefs.getBoolean("is_first_run", true)
        layoutMode = KeyboardLayoutMode.valueOf(prefs.getString("layout_mode", "DEFAULT") ?: "DEFAULT")
        themeId = prefs.getString("theme_id", "system") ?: "system"
        keyHeightDp = prefs.getInt("key_height_dp", 50).let { if (it < 36) 50 else it }
        keySpacingDp = prefs.getInt("key_spacing_dp", 5)
        keyCornerRadiusDp = prefs.getInt("key_corner_radius_dp", 8)
        longPressDelayMs = prefs.getInt("long_press_delay_ms", 220).coerceIn(150, 500)
        showKeyBorders = prefs.getBoolean("show_key_borders", true)
        isHapticsEnabled = prefs.getBoolean("is_haptics_enabled", true)
        hapticIntensityMs = prefs.getInt("haptic_intensity_ms", 15)
        isSoundEnabled = prefs.getBoolean("is_sound_enabled", true)
        soundVolume = prefs.getInt("sound_volume", 80)
        soundProfile = prefs.getString("sound_profile", "iphone") ?: "iphone"
        isLocalAiEmojiEnabled = prefs.getBoolean("is_local_ai_emoji_enabled", true)
        isDeveloperModeEnabled = prefs.getBoolean("is_dev_mode_enabled", false)
        isNumberRowEnabled = prefs.getBoolean("is_number_row_enabled", false)
        isSuggestionsEnabled = prefs.getBoolean("is_suggestions_enabled", true)
        isAutoCorrectEnabled = prefs.getBoolean("is_auto_correct_enabled", true)
        autoCorrectLevel = prefs.getString("auto_correct_level", "balanced") ?: "balanced"
        isNextWordPredictionEnabled = prefs.getBoolean("is_next_word_pred_enabled", true)
        isAutoCorrectAnimationEnabled = prefs.getBoolean("is_auto_correct_anim_enabled", true)
        isAutoCapEnabled = prefs.getBoolean("is_auto_cap_enabled", true)
        isDoubleSpacePeriodEnabled = prefs.getBoolean("is_double_space_period_enabled", true)
        isSpaceSwipeCursorEnabled = prefs.getBoolean("is_space_swipe_cursor", true)
        isBackspaceSwipeDeleteEnabled = prefs.getBoolean("is_backspace_swipe_delete", true)
        isGlideTypingEnabled = prefs.getBoolean("is_glide_typing_enabled", true)
        isPopupPreviewEnabled = prefs.getBoolean("is_popup_preview_enabled", true)
        currentLanguage = prefs.getString("current_language", "en") ?: "en"
        currentFontStyle = prefs.getString("current_font_style", "normal") ?: "normal"
        isCustomThemeEnabled = prefs.getBoolean("is_custom_theme_enabled", false)
        customBgColorHex = prefs.getString("custom_bg_hex", "#1E1E2E") ?: "#1E1E2E"
        customKeyColorHex = prefs.getString("custom_key_hex", "#313244") ?: "#313244"
        customAccentColorHex = prefs.getString("custom_accent_hex", "#CBA6F7") ?: "#CBA6F7"
        customTextColorHex = prefs.getString("custom_text_hex", "#CDD6F4") ?: "#CDD6F4"
        clipboardUnpinnedLimit = prefs.getInt("clipboard_unpinned_limit", 10)
        hapticProfile = prefs.getString("haptic_profile", "system") ?: "system"
        hapticIntensityPercent = prefs.getInt("haptic_intensity_percent", 65).coerceIn(5, 100)
        isStabilizerSimulationEnabled = prefs.getBoolean("is_stabilizer_sim_enabled", true)
        isKeySpecificHapticsEnabled = prefs.getBoolean("is_key_specific_haptics_enabled", true)
        recentEmojis = prefs.getString("recent_emojis", "👍,❤️,😊,🔥,✨,🎉,🚀,💻,⚡,🙌,👏,💡,🤖,✅,😍,🤣,🎂,🥳,☕,🍕")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: listOf("👍", "❤️", "😊", "🔥", "✨", "🎉", "🚀", "💻", "⚡", "🙌", "👏", "💡", "🤖", "✅", "😍", "🤣", "🎂", "🥳", "☕", "🍕")
        hotbarTools = prefs.getString("hotbar_tools", "CLIPBOARD,CURSOR_NAV,FONTS,LANGUAGE,THEME,SETTINGS")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: listOf("CLIPBOARD", "CURSOR_NAV", "FONTS", "LANGUAGE", "THEME", "SETTINGS")
    }

    fun restoreToDefaults() {
        prefs.edit().clear().apply()
        reloadAll()
    }

    fun updateFirstRun(isFirstRun: Boolean) {
        this.isFirstRun = isFirstRun
        prefs.edit().putBoolean("is_first_run", isFirstRun).apply()
    }

    fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val current = recentEmojis.toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        val updated = current.take(36)
        recentEmojis = updated
        prefs.edit().putString("recent_emojis", updated.joinToString(",")).apply()
    }

    fun setTheme(id: String) {
        layoutMode = KeyboardLayoutMode.valueOf(prefs.getString("layout_mode", "DEFAULT") ?: "DEFAULT")
        themeId = id
        isCustomThemeEnabled = false
        prefs.edit()
            .putString("theme_id", id)
            .putBoolean("is_custom_theme_enabled", false)
            .apply()
    }

    fun setCustomThemeColors(bgHex: String, keyHex: String, accentHex: String, textHex: String) {
        customBgColorHex = bgHex
        customKeyColorHex = keyHex
        customAccentColorHex = accentHex
        customTextColorHex = textHex
        isCustomThemeEnabled = true
        layoutMode = KeyboardLayoutMode.valueOf(prefs.getString("layout_mode", "DEFAULT") ?: "DEFAULT")
        themeId = "custom"
        prefs.edit()
            .putString("custom_bg_hex", bgHex)
            .putString("custom_key_hex", keyHex)
            .putString("custom_accent_hex", accentHex)
            .putString("custom_text_hex", textHex)
            .putBoolean("is_custom_theme_enabled", true)
            .putString("theme_id", "custom")
            .apply()
    }

    fun setKeyHeight(height: Int) {
        keyHeightDp = height.coerceIn(36, 72)
        prefs.edit().putInt("key_height_dp", keyHeightDp).apply()
    }

    fun setKeySpacing(spacing: Int) {
        keySpacingDp = spacing.coerceIn(0, 8)
        prefs.edit().putInt("key_spacing_dp", keySpacingDp).apply()
    }

    fun setKeyCornerRadius(radius: Int) {
        keyCornerRadiusDp = radius.coerceIn(0, 20)
        prefs.edit().putInt("key_corner_radius_dp", keyCornerRadiusDp).apply()
    }

    fun setLongPressDelay(delay: Int) {
        longPressDelayMs = delay.coerceIn(150, 500)
        prefs.edit().putInt("long_press_delay_ms", longPressDelayMs).apply()
    }

    fun setBorders(enabled: Boolean) {
        showKeyBorders = enabled
        prefs.edit().putBoolean("show_key_borders", enabled).apply()
    }

    fun setHaptics(enabled: Boolean) {
        isHapticsEnabled = enabled
        prefs.edit().putBoolean("is_haptics_enabled", enabled).apply()
    }

    fun setHapticIntensity(ms: Int) {
        hapticIntensityMs = ms.coerceIn(5, 50)
        hapticIntensityPercent = (ms.coerceIn(5, 50) * 2).coerceIn(10, 100)
        prefs.edit()
            .putInt("haptic_intensity_ms", hapticIntensityMs)
            .putInt("haptic_intensity_percent", hapticIntensityPercent)
            .apply()
    }

    fun updateHapticProfile(profile: String) {
        hapticProfile = profile
        prefs.edit().putString("haptic_profile", profile).apply()
    }

    fun updateHapticIntensityPercent(percent: Int) {
        hapticIntensityPercent = percent.coerceIn(5, 100)
        hapticIntensityMs = (percent / 2).coerceIn(5, 50)
        prefs.edit()
            .putInt("haptic_intensity_percent", hapticIntensityPercent)
            .putInt("haptic_intensity_ms", hapticIntensityMs)
            .apply()
    }

    fun setStabilizerSimulation(enabled: Boolean) {
        isStabilizerSimulationEnabled = enabled
        prefs.edit().putBoolean("is_stabilizer_sim_enabled", enabled).apply()
    }

    fun setKeySpecificHaptics(enabled: Boolean) {
        isKeySpecificHapticsEnabled = enabled
        prefs.edit().putBoolean("is_key_specific_haptics_enabled", enabled).apply()
    }

    fun setSound(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs.edit().putBoolean("is_sound_enabled", enabled).apply()
    }

    fun updateSoundVolume(volume: Int) {
        soundVolume = volume.coerceIn(0, 100)
        prefs.edit().putInt("sound_volume", soundVolume).apply()
    }

    fun updateSoundProfile(profile: String) {
        soundProfile = profile
        prefs.edit().putString("sound_profile", profile).apply()
    }

    fun setLocalAiEmoji(enabled: Boolean) {
        isLocalAiEmojiEnabled = enabled
        prefs.edit().putBoolean("is_local_ai_emoji_enabled", enabled).apply()
    }

    fun toggleDeveloperMode(): Boolean {
        val newState = !isDeveloperModeEnabled
        setDeveloperMode(newState)
        return newState
    }

    fun setDeveloperMode(enabled: Boolean) {
        isDeveloperModeEnabled = enabled
        prefs.edit().putBoolean("is_dev_mode_enabled", enabled).apply()
    }

    fun setNumberRow(enabled: Boolean) {
        isNumberRowEnabled = enabled
        prefs.edit().putBoolean("is_number_row_enabled", enabled).apply()
    }

    fun setSuggestions(enabled: Boolean) {
        isSuggestionsEnabled = enabled
        prefs.edit().putBoolean("is_suggestions_enabled", enabled).apply()
    }

    fun setAutoCorrect(enabled: Boolean) {
        isAutoCorrectEnabled = enabled
        prefs.edit().putBoolean("is_auto_correct_enabled", enabled).apply()
    }

    fun setAutoCorrectionLevel(level: String) {
        autoCorrectLevel = level
        prefs.edit().putString("auto_correct_level", level).apply()
    }

    fun setNextWordPrediction(enabled: Boolean) {
        isNextWordPredictionEnabled = enabled
        prefs.edit().putBoolean("is_next_word_pred_enabled", enabled).apply()
    }

    fun setAutoCorrectAnimation(enabled: Boolean) {
        isAutoCorrectAnimationEnabled = enabled
        prefs.edit().putBoolean("is_auto_correct_anim_enabled", enabled).apply()
    }

    fun setAutoCap(enabled: Boolean) {
        isAutoCapEnabled = enabled
        prefs.edit().putBoolean("is_auto_cap_enabled", enabled).apply()
    }

    fun setDoubleSpacePeriod(enabled: Boolean) {
        isDoubleSpacePeriodEnabled = enabled
        prefs.edit().putBoolean("is_double_space_period_enabled", enabled).apply()
    }

    fun setSpaceSwipeCursor(enabled: Boolean) {
        isSpaceSwipeCursorEnabled = enabled
        prefs.edit().putBoolean("is_space_swipe_cursor", enabled).apply()
    }

    fun setBackspaceSwipeDelete(enabled: Boolean) {
        isBackspaceSwipeDeleteEnabled = enabled
        prefs.edit().putBoolean("is_backspace_swipe_delete", enabled).apply()
    }

    fun setGlideTyping(enabled: Boolean) {
        isGlideTypingEnabled = enabled
        prefs.edit().putBoolean("is_glide_typing_enabled", enabled).apply()
    }

    fun setPopupPreview(enabled: Boolean) {
        isPopupPreviewEnabled = enabled
        prefs.edit().putBoolean("is_popup_preview_enabled", enabled).apply()
    }

    fun setLanguage(code: String) {
        currentLanguage = code
        prefs.edit().putString("current_language", code).apply()
    }

    fun setFontStyle(styleId: String) {
        currentFontStyle = styleId
        prefs.edit().putString("current_font_style", styleId).apply()
    }

    fun updateHotbarTools(tools: List<String>) {
        hotbarTools = tools
        prefs.edit().putString("hotbar_tools", tools.joinToString(",")).apply()
    }

    fun addHotbarTool(toolId: String) {
        if (!hotbarTools.contains(toolId)) {
            val newList = hotbarTools.toMutableList().apply { add(toolId) }
            updateHotbarTools(newList)
        }
    }

    fun removeHotbarTool(toolId: String) {
        if (hotbarTools.contains(toolId)) {
            val newList = hotbarTools.toMutableList().apply { remove(toolId) }
            updateHotbarTools(newList)
        }
    }

    fun toggleHotbarTool(toolId: String) {
        if (hotbarTools.contains(toolId)) {
            removeHotbarTool(toolId)
        } else {
            addHotbarTool(toolId)
        }
    }

    fun moveHotbarTool(fromIndex: Int, toIndex: Int) {
        if (fromIndex in hotbarTools.indices && toIndex in hotbarTools.indices && fromIndex != toIndex) {
            val newList = hotbarTools.toMutableList()
            val item = newList.removeAt(fromIndex)
            newList.add(toIndex, item)
            updateHotbarTools(newList)
        }
    }

    fun resetHotbarTools() {
        val defaultList = listOf("CLIPBOARD", "CURSOR_NAV", "FONTS", "LANGUAGE", "THEME", "SETTINGS")
        updateHotbarTools(defaultList)
    }

    fun updateClipboardUnpinnedLimit(limit: Int) {
        val clamped = limit.coerceIn(5, 50)
        clipboardUnpinnedLimit = clamped
        prefs.edit().putInt("clipboard_unpinned_limit", clamped).apply()
    }

    companion object {
        @Volatile
        private var instance: KeyboardPreferences? = null

        fun getInstance(context: Context): KeyboardPreferences {
            return instance ?: synchronized(this) {
                instance ?: KeyboardPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}

enum class KeyboardLayoutMode {
    DEFAULT, ONE_HANDED_LEFT, ONE_HANDED_RIGHT, FLOATING
}
