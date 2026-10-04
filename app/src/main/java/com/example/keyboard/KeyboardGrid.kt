package com.example.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.HapticFeedbackHelper
import com.example.core.SoundFeedbackHelper
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
    val soundHelper = remember { SoundFeedbackHelper.getInstance(context) }
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var keyboardGridSize by remember { mutableStateOf(IntSize.Zero) }

    // Glide trail state
    val glideTrailPoints = remember { mutableStateListOf<Offset>() }
    val sampledGlideChars = remember { mutableListOf<Char>() }
    var isGlideGestureActive by remember { mutableStateOf(false) }

    // Geometry computation
    val hasNumberRow = preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA
    val alphaRows = remember(keyboardMode, preferences.currentLanguage) {
        when (keyboardMode) {
            KeyboardMode.ALPHA -> KeyboardLanguages.getAlphaRows(preferences.currentLanguage)
            KeyboardMode.NUMBERS -> KeyboardLayouts.numberSymbolRows
            KeyboardMode.SYMBOLS -> KeyboardLayouts.advancedSymbolRows
        }
    }

    val allDisplayedRows = remember(hasNumberRow, alphaRows) {
        if (hasNumberRow) {
            listOf(KeyboardLayouts.numberRow) + alphaRows
        } else {
            alphaRows
        }
    }

    val keySpacingPx = remember(preferences.keySpacingDp, density) {
        with(density) { preferences.keySpacingDp.dp.toPx() }
    }

    val geometry = remember(keyboardGridSize, allDisplayedRows, hasNumberRow, keySpacingPx) {
        if (keyboardGridSize.width > 0 && keyboardGridSize.height > 0) {
            KeyboardGeometry(
                size = keyboardGridSize,
                rows = allDisplayedRows,
                hasNumberRow = hasNumberRow,
                numberRowHeightRatio = 0.85f,
                keySpacingPx = keySpacingPx
            )
        } else null
    }

    // Stable feedback triggers
    val triggerFeedback: (KeyModel) -> Unit = remember(hapticHelper, soundHelper, preferences, currentView) {
        { key ->
            if (preferences.isHapticsEnabled) {
                val role = if (key.type == KeyType.CHARACTER) {
                    com.example.core.KeyHapticRole.STANDARD
                } else {
                    com.example.core.KeyHapticRole.MODIFIER
                }
                hapticHelper.performMechanicalKeyPress(
                    role = if (preferences.isKeySpecificHapticsEnabled) role else com.example.core.KeyHapticRole.STANDARD,
                    profileId = preferences.hapticProfile,
                    intensityPercent = preferences.hapticIntensityPercent,
                    isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                    isHapticsEnabled = true,
                    view = currentView
                )
            }
            if (preferences.isSoundEnabled) {
                soundHelper.playKeyPress(preferences.soundProfile, preferences.soundVolume, true)
            }
        }
    }

    // Touch coordinator state
    var pressedPositions by remember { mutableStateOf<Set<KeyPosition>>(emptySet()) }
    var activePopupInfo by remember { mutableStateOf<ActivePopupInfo?>(null) }
    var previewPosition by remember { mutableStateOf<KeyPosition?>(null) }

    val currentShiftState by rememberUpdatedState(shiftState)
    val currentPreferences by rememberUpdatedState(preferences)

    val touchCoordinator = remember(coroutineScope, geometry) {
        KeyboardTouchCoordinator(
            scope = coroutineScope,
            onKeyTap = { key ->
                triggerFeedback(key)
                onKeyTap(key)
            },
            onKeyInsertText = { text ->
                triggerFeedback(KeyModel(text))
                onKeyInsertText(text)
            },
            onBackspace = {
                triggerFeedback(KeyModel(type = KeyType.BACKSPACE))
                onBackspace()
            },
            onDeleteWord = {
                triggerFeedback(KeyModel(type = KeyType.BACKSPACE))
                onDeleteWord()
            },
            onMoveCursor = onMoveCursor,
            onLongPressTick = {
                hapticHelper.performLongPressHaptic(currentPreferences.isHapticsEnabled, currentView)
            },
            onSelectionTick = {
                hapticHelper.performSelectionTick(currentPreferences.isHapticsEnabled, currentView)
            },
            getGeometry = { geometry },
            getLongPressDelayMs = { currentPreferences.longPressDelayMs },
            isPopupPreviewEnabled = { currentPreferences.isPopupPreviewEnabled },
            isSpaceSwipeEnabled = { currentPreferences.isSpaceSwipeCursorEnabled },
            isBackspaceSwipeEnabled = { currentPreferences.isBackspaceSwipeDeleteEnabled },
            isShifted = { currentShiftState != ShiftState.OFF }
        ).apply {
            setOnStateChangedListener {
                pressedPositions = this.pressedKeyPositions
                activePopupInfo = this.activePopup
                previewPosition = this.previewKeyPosition
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { keyboardGridSize = it }
            .pointerInput(geometry, preferences.isGlideTypingEnabled) {
                awaitEachGesture {
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            for (change in event.changes) {
                                if (change.changedToDown()) {
                                    change.consume()
                                    touchCoordinator.onPointerDown(change)

                                    // Check glide initiation if enabled
                                    if (currentPreferences.isGlideTypingEnabled && keyboardMode == KeyboardMode.ALPHA) {
                                        val startChar = geometry?.findCharKeyAtOffset(change.position)
                                        if (startChar != null) {
                                            sampledGlideChars.clear()
                                            sampledGlideChars.add(startChar)
                                            glideTrailPoints.clear()
                                        }
                                    }
                                } else if (change.pressed) {
                                    change.consume()
                                    touchCoordinator.onPointerMove(change)

                                    // Handle glide tracking
                                    if (currentPreferences.isGlideTypingEnabled && keyboardMode == KeyboardMode.ALPHA) {
                                        val currentPoint = change.position
                                        if (glideTrailPoints.size > 2 || (change.position - change.previousPosition).getDistance() > 8f) {
                                            glideTrailPoints.add(currentPoint)
                                            val charUnderFinger = geometry?.findCharKeyAtOffset(currentPoint)
                                            if (charUnderFinger != null && (sampledGlideChars.isEmpty() || sampledGlideChars.last() != charUnderFinger)) {
                                                sampledGlideChars.add(charUnderFinger)
                                                hapticHelper.performSelectionTick(currentPreferences.isHapticsEnabled, currentView)
                                            }
                                            isGlideGestureActive = true
                                        }
                                    }
                                } else if (change.changedToUp()) {
                                    change.consume()
                                    if (isGlideGestureActive && sampledGlideChars.size >= 2) {
                                        val glideWord = suggestionEngine.findGlideWord(sampledGlideChars)
                                        if (glideWord != null) {
                                            onGlideWord(glideWord)
                                        }
                                    }
                                    touchCoordinator.onPointerUp(change)
                                    isGlideGestureActive = false
                                    glideTrailPoints.clear()
                                    sampledGlideChars.clear()
                                }
                            }

                            // If all pointers lifted or canceled
                            if (event.changes.all { !it.pressed }) {
                                break
                            }
                        }
                    } finally {
                        touchCoordinator.onCancel()
                        isGlideGestureActive = false
                        glideTrailPoints.clear()
                        sampledGlideChars.clear()
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            allDisplayedRows.forEachIndexed { rIdx, row ->
                val isNumRow = hasNumberRow && rIdx == 0
                val rowHeight = if (isNumRow) {
                    (preferences.keyHeightDp * 0.85f).dp
                } else {
                    preferences.keyHeightDp.dp
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                ) {
                    row.forEachIndexed { kIdx, key ->
                        val isKeyPressed = pressedPositions.any { it.rowIndex == rIdx && it.keyIndex == kIdx }
                        val isPreviewShown = previewPosition?.let { it.rowIndex == rIdx && it.keyIndex == kIdx } == true
                        val popup = if (activePopupInfo?.keyPosition?.rowIndex == rIdx && activePopupInfo?.keyPosition?.keyIndex == kIdx) {
                            activePopupInfo
                        } else null

                        KeyView(
                            key = key,
                            theme = theme,
                            preferences = preferences,
                            shiftState = shiftState,
                            imeAction = imeAction,
                            isPressed = isKeyPressed,
                            showPreview = isPreviewShown,
                            popupInfo = popup,
                            modifier = Modifier.weight(key.weight)
                        )
                    }
                }
            }
        }

        // Glide typing trail overlay
        if (glideTrailPoints.size >= 2) {
            GlideTrailOverlay(
                points = glideTrailPoints,
                color = theme.accentColor
            )
        }
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
