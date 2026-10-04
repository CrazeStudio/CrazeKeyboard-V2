package com.example.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import com.example.themes.KeyboardTheme

/** Native rendering boundary: one Android View instead of one Compose node per key. */
@Composable
fun KeyboardGrid(
    context: android.content.Context,
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    suggestionEngine: SuggestionEngine,
    keyboardMode: KeyboardMode,
    shiftState: ShiftState,
    imeAction: Int,
    onKeyTap: (KeyModel) -> Unit,
    onKeyLongPress: (KeyModel) -> Unit,
    onKeyInsertText: (String) -> Unit,
    onMoveCursor: (Int) -> Unit,
    onSpecialKey: (CursorAction) -> Unit,
    onDeleteWord: () -> Unit,
    onBackspace: () -> Unit,
    onGlideWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasNumberRow = preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA
    val alphaRows = remember(keyboardMode, preferences.currentLanguage) {
        when (keyboardMode) {
            KeyboardMode.ALPHA -> KeyboardLanguages.getAlphaRows(preferences.currentLanguage)
            KeyboardMode.NUMBERS -> KeyboardLayouts.numberSymbolRows
            KeyboardMode.SYMBOLS -> KeyboardLayouts.advancedSymbolRows
        }
    }
    val rows = remember(hasNumberRow, alphaRows) {
        if (hasNumberRow) listOf(KeyboardLayouts.numberRow) + alphaRows else alphaRows
    }

    AndroidView(
        modifier = modifier,
        factory = { FastKeyboardSurface(it) },
        update = { surface ->
            surface.configure(
                rows = rows,
                theme = theme,
                preferences = preferences,
                keyboardMode = keyboardMode,
                shiftState = shiftState,
                imeAction = imeAction,
                suggestionEngine = suggestionEngine,
                onKeyTap = onKeyTap,
                onKeyLongPress = { key ->
                    // Only alternateText keys use the legacy long-press callback.
                    // Popup-option keys are selected and committed by the native surface.
                    if (key.longPressOptions.isEmpty()) onKeyLongPress(key)
                },
                onKeyInsertText = onKeyInsertText,
                onMoveCursor = onMoveCursor,
                onDeleteWord = onDeleteWord,
                onBackspace = onBackspace,
                onGlideWord = onGlideWord
            )
            // A single Canvas redraw is cheap; importantly, no per-key Compose tree is rebuilt.
            surface.invalidate()
        }
    )
}
