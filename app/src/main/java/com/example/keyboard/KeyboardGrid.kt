package com.example.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.HapticFeedbackHelper
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import com.example.themes.KeyboardTheme

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
    val hapticHelper = remember { HapticFeedbackHelper.getInstance(context) }
    val currentView = LocalView.current
    val density = LocalDensity.current

    var keyboardGridSize by remember { mutableStateOf(IntSize.Zero) }
    val glideTrailPoints = remember { mutableStateListOf<Offset>() }
    val trailPath = remember { Path() }
    val sampledGlideChars = remember { mutableListOf<Char>() }
    var isGlideGestureActive by remember { mutableStateOf(false) }

    val rows = remember(keyboardMode, preferences.currentLanguage) {
        when (keyboardMode) {
            KeyboardMode.ALPHA -> KeyboardLanguages.getAlphaRows(preferences.currentLanguage)
            KeyboardMode.NUMBERS -> KeyboardLayouts.numberSymbolRows
            KeyboardMode.SYMBOLS -> KeyboardLayouts.advancedSymbolRows
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { keyboardGridSize = it }
            .pointerInput(keyboardMode, preferences.isGlideTypingEnabled, rows, preferences.isNumberRowEnabled) {
                if (keyboardMode != KeyboardMode.ALPHA || !preferences.isGlideTypingEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    sampledGlideChars.clear()
                    glideTrailPoints.clear()
                    isGlideGestureActive = false
                    val startTime = System.currentTimeMillis()
                    val longPressTimeout = preferences.longPressDelayMs.toLong()

                    val startChar = findCharKeyAtOffset(
                        down.position,
                        keyboardGridSize,
                        rows,
                        preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA,
                        0.85f
                    )
                    if (startChar != null) {
                        sampledGlideChars.add(startChar)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (change.pressed) {
                            val currentPoint = change.position
                            val distanceFromDown = (currentPoint - down.position).getDistance()
                            val elapsedTime = System.currentTimeMillis() - startTime

                            if (!isGlideGestureActive) {
                                if (distanceFromDown > 30.dp.toPx() && elapsedTime < longPressTimeout) {
                                    isGlideGestureActive = true
                                    change.consume()
                                }
                            }
                            
                            if (isGlideGestureActive) {
                                glideTrailPoints.add(currentPoint)
                                val charUnderFinger = findCharKeyAtOffset(
                                    currentPoint,
                                    keyboardGridSize,
                                    rows,
                                    preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA,
                                    0.85f
                                )
                                if (charUnderFinger != null && (sampledGlideChars.isEmpty() || sampledGlideChars.last() != charUnderFinger)) {
                                    sampledGlideChars.add(charUnderFinger)
                                    hapticHelper.performSelectionTick(preferences.isHapticsEnabled, currentView)
                                }
                                change.consume()
                            }
                        } else {
                            if (isGlideGestureActive && sampledGlideChars.size >= 2) {
                                val glideWord = suggestionEngine.findGlideWord(sampledGlideChars)
                                if (glideWord != null) {
                                    onGlideWord(glideWord)
                                }
                                change.consume()
                            }
                            break
                        }
                    }
                    isGlideGestureActive = false
                    glideTrailPoints.clear()
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Optional Number Row
            if (preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((preferences.keyHeightDp * 0.85f).dp)
                ) {
                    KeyboardLayouts.numberRow.forEach { key ->
                        KeyView(
                            key = key,
                            theme = theme,
                            preferences = preferences,
                            shiftState = shiftState,
                            onTap = { onKeyTap(key) },
                            modifier = Modifier.weight(key.weight)
                        )
                    }
                }
            }

            // Keyboard Rows
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(preferences.keyHeightDp.dp)
                ) {
                    row.forEach { key ->
                        KeyView(
                            key = key,
                            theme = theme,
                            preferences = preferences,
                            shiftState = shiftState,
                            imeAction = imeAction,
                            isGlideActive = { isGlideGestureActive },
                            onTap = { onKeyTap(key) },
                            onLongPress = { onKeyLongPress(key) },
                            onInsertText = { onKeyInsertText(it) },
                            onSelectionTick = { hapticHelper.performSelectionTick(preferences.isHapticsEnabled, currentView) },
                            onSpaceSwipe = { onMoveCursor(if (it > 0) 1 else -1) },
                            onBackspaceSwipe = { onDeleteWord() },
                            modifier = Modifier.weight(key.weight)
                        )
                    }
                }
            }
        }

        // Glide Typing Trail - Isolated recomposition
        GlideTrailOverlay(
            points = glideTrailPoints,
            color = theme.accentColor
        )
    }
}

@Composable
private fun GlideTrailOverlay(
    points: List<Offset>,
    color: Color
) {
    if (points.size < 2) return
    
    val trailPath = remember { Path() }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val path = trailPath.apply {
            reset()
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midX = (prev.x + curr.x) / 2f
                val midY = (prev.y + curr.y) / 2f
                quadraticBezierTo(prev.x, prev.y, midX, midY)
            }
            lineTo(points.last().x, points.last().y)
        }

        drawPath(
            path = path,
            color = color.copy(alpha = 0.35f),
            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = path,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = color, radius = 6.dp.toPx(), center = points.last())
    }
}

private fun findCharKeyAtOffset(
    offset: Offset,
    size: IntSize,
    rows: List<List<KeyModel>>,
    hasNumberRow: Boolean,
    numberRowHeightRatio: Float
): Char? {
    if (size.width <= 0 || size.height <= 0) return null
    val totalRowsWeight = if (hasNumberRow) (rows.size + numberRowHeightRatio) else rows.size.toFloat()
    val standardRowHeight = size.height / totalRowsWeight
    val numberRowHeight = standardRowHeight * numberRowHeightRatio

    val targetRowIndex: Int
    if (hasNumberRow) {
        if (offset.y < numberRowHeight) return null
        val yInRows = offset.y - numberRowHeight
        targetRowIndex = (yInRows / standardRowHeight).toInt().coerceIn(0, rows.size - 1)
    } else {
        targetRowIndex = (offset.y / standardRowHeight).toInt().coerceIn(0, rows.size - 1)
    }

    val rowKeys = rows.getOrNull(targetRowIndex) ?: return null
    val totalWeight = rowKeys.sumOf { it.weight.toDouble() }.toFloat()
    if (totalWeight <= 0f) return null

    var currentX = 0f
    for (key in rowKeys) {
        val keyWidth = (key.weight / totalWeight) * size.width
        if (offset.x >= currentX && offset.x <= currentX + keyWidth) {
            if (key.type == KeyType.CHARACTER && key.primaryText.isNotEmpty()) {
                return key.primaryText.first().lowercaseChar()
            }
            return null
        }
        currentX += keyWidth
    }
    return null
}
