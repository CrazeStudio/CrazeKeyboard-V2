package com.example.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import com.example.themes.KeyboardTheme

/**
 * Keyboard rendering boundary. The keyboard itself is a single native View so normal
 * character input does not create a Compose node/recomposition per key.
 */
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
                    // Alternate-text long press uses the existing callback. Popup options
                    // are selected by the native surface and inserted through onKeyInsertText.
                    if (key.longPressOptions.isEmpty()) onKeyLongPress(key)
                },
                onKeyInsertText = onKeyInsertText,
                onMoveCursor = onMoveCursor,
                onDeleteWord = onDeleteWord,
                onBackspace = onBackspace,
                onGlideWord = onGlideWord
            )
        }
    )
}
