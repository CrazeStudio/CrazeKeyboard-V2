package com.example.keyboard

import com.example.developer.DevKeyItem
import com.example.developer.ModifierState

enum class KeyboardMode {
    ALPHA, NUMBERS, SYMBOLS
}

enum class ShiftState {
    OFF, SHIFTED, CAPS_LOCK
}

enum class CursorAction {
    LEFT, RIGHT, UP, DOWN, HOME, END, SELECT_ALL, CUT, COPY, PASTE, DELETE, TOGGLE_SELECT, START, CLEAR
}

interface KeyboardActionListener {
    fun onText(text: String)
    fun onReplaceCurrentWord(replacement: String)
    fun onBackspace()
    fun onDeleteWord()
    fun onEnter()
    fun onMoveCursor(offset: Int)
    fun onSpecialKey(action: CursorAction)
    fun onDevKey(item: DevKeyItem, modifierState: ModifierState)
    fun onVoiceRequested()
    fun onSettingsRequested()
    fun onMedia(url: String)
    fun onAiPolishRequested()
    fun onAiChatRequested(prompt: String)
    fun getCurrentContextText(): String
    fun onReplaceContextText(newText: String)
}

enum class ActivePanel {
    KEYBOARD,
    MEDIA,
    CLIPBOARD,
    CURSOR_NAV,
    TOOLS_MENU,
    FONTS,
    LANGUAGE_PICKER,
    CUSTOMIZE_HOTBAR,
    TRANSLATE,
    AI_CHAT,
    SWIFT_AI
}
