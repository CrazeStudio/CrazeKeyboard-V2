package com.example.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun KeyView(
    key: KeyModel,
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    shiftState: ShiftState,
    imeAction: Int = EditorInfo.IME_ACTION_NONE,
    isGlideActive: () -> Boolean = { false },
    onDown: (() -> Unit)? = null,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onInsertText: ((String) -> Unit)? = null,
    onSelectionTick: (() -> Unit)? = null,
    onSpaceSwipe: ((Float) -> Unit)? = null,
    onBackspaceSwipe: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (key.type == KeyType.SPACER) {
        Spacer(modifier = modifier)
        return
    }

    var isPressed by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var selectedOptionIndex by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Stable references to callbacks
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnInsertText by rememberUpdatedState(onInsertText)
    val currentOnSelectionTick by rememberUpdatedState(onSelectionTick)
    val currentOnSpaceSwipe by rememberUpdatedState(onSpaceSwipe)
    val currentOnBackspaceSwipe by rememberUpdatedState(onBackspaceSwipe)
    val currentKey by rememberUpdatedState(key)
    val currentShiftState by rememberUpdatedState(shiftState)
    val currentIsGlideActive by rememberUpdatedState(isGlideActive)

    val isShifted = shiftState != ShiftState.OFF
    val isLeftEdge = remember(key) { key.primaryText in listOf("q", "1", "a", "z") }
    val isRightEdge = remember(key) { key.primaryText in listOf("p", "0", "l", "m") }
    
    val alternateOptions = remember(key.longPressOptions, key.alternateText, isShifted) {
        val rawList = when {
            key.longPressOptions.isNotEmpty() -> {
                val withoutBase = key.longPressOptions.filter { it.lowercase() != key.primaryText.lowercase() }
                if (withoutBase.isNotEmpty()) withoutBase else key.longPressOptions
            }
            key.alternateText.isNotEmpty() -> listOf(key.alternateText)
            else -> emptyList()
        }
        val optionsWithAlt = if (key.alternateText.isNotEmpty() && !rawList.contains(key.alternateText)) {
            listOf(key.alternateText) + rawList
        } else {
            rawList
        }
        val shifted = if (isShifted) optionsWithAlt.map { opt -> if (opt.length == 1 && opt.first().isLetter()) opt.uppercase() else opt } else optionsWithAlt
        
        if (shifted.size > 1) {
            val result = MutableList(shifted.size) { "" }
            val defaultIndex = when {
                isLeftEdge -> 0
                isRightEdge -> shifted.size - 1
                else -> shifted.size / 2
            }
            result[defaultIndex] = shifted[0]
            var left = defaultIndex - 1
            var right = defaultIndex + 1
            var i = 1
            while (i < shifted.size) {
                if (right < shifted.size) result[right++] = shifted[i++]
                if (i < shifted.size && left >= 0) result[left--] = shifted[i++]
            }
            result
        } else {
            shifted
        }
    }

    val defaultSelectedIndex = remember(alternateOptions) {
        when {
            isLeftEdge -> 0
            isRightEdge -> Math.max(0, alternateOptions.size - 1)
            else -> alternateOptions.size / 2
        }
    }

    val isEnter = key.type == KeyType.ENTER
    val isAccentEnter = isEnter && (theme.id == "light" || theme.id == "snow" || theme.id == "system")
    val isSpecialKey = (key.type != KeyType.CHARACTER && key.type != KeyType.SPACE) || key.primaryText == "," || key.primaryText == "." || key.primaryText == "/" || key.primaryText == "¡" || key.primaryText == "¿"
    
    val baseKeyBg = if (isAccentEnter) theme.accentColor else if (isSpecialKey) theme.specialKeyColor else theme.keyColor
    val keyBg = if (isPressed) theme.accentColor.copy(alpha = 0.28f) else baseKeyBg

    val cornerRadius = remember(preferences.keyCornerRadiusDp) { preferences.keyCornerRadiusDp.dp }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    val displayText = remember(key, shiftState, preferences.currentLanguage) {
        when {
            key.type == KeyType.CHARACTER -> if (shiftState != ShiftState.OFF) key.shiftedText else key.primaryText
            key.type == KeyType.SPACE -> KeyboardLanguages.getLanguage(preferences.currentLanguage).name
            else -> key.primaryText
        }
    }

    val itemStepPx = remember(density) { with(density) { 38.dp.toPx() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("key_${key.primaryText.ifEmpty { key.type.name }}")
            .pointerInput(key.type, preferences.longPressDelayMs) {
                awaitEachGesture {
                    var deleteJob: Job? = null
                    var longPressJob: Job? = null
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnDown?.invoke()
                        isPressed = true
                        showPopup = false
                        selectedOptionIndex = defaultSelectedIndex

                        var isLongPressTriggered = false
                        var isLongPressActionFired = false

                        if (currentKey.type == KeyType.BACKSPACE) {
                            currentOnTap()
                            deleteJob = coroutineScope.launch {
                                delay(300)
                                var delayMs = 60L
                                while (isActive) {
                                    currentOnTap()
                                    delay(delayMs)
                                    if (delayMs > 30L) delayMs -= 5L
                                }
                            }
                        } else {
                            longPressJob = coroutineScope.launch {
                                delay(preferences.longPressDelayMs.toLong())
                                if (isActive && !currentIsGlideActive.invoke()) {
                                    isLongPressTriggered = true
                                    if (alternateOptions.isNotEmpty()) {
                                        showPopup = true
                                        selectedOptionIndex = defaultSelectedIndex
                                        currentOnSelectionTick?.invoke()
                                    } else {
                                        isLongPressActionFired = true
                                        currentOnLongPress?.invoke()
                                    }
                                }
                            }
                        }

                        var isSwipeHandled = false
                        var isCanceled = false
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                            if (change.pressed) {
                                val netDragDistance = (change.position - down.position).getDistance()
                                val dragX = change.positionChange().x

                                if (!isLongPressTriggered) {
                                    if (currentKey.type == KeyType.SPACE && preferences.isSpaceSwipeCursorEnabled && abs(change.position.x - down.position.x) > 20.dp.toPx()) {
                                        currentOnSpaceSwipe?.invoke(dragX)
                                        isSwipeHandled = true
                                        longPressJob?.cancel()
                                    } else if (currentKey.type == KeyType.BACKSPACE && preferences.isBackspaceSwipeDeleteEnabled && (change.position.x - down.position.x) < -40.dp.toPx()) {
                                        currentOnBackspaceSwipe?.invoke()
                                        isSwipeHandled = true
                                        deleteJob?.cancel()
                                    } else {
                                        val glideActive = currentIsGlideActive.invoke()
                                        val maxDragAllowed = if (preferences.isGlideTypingEnabled && currentKey.type == KeyType.CHARACTER) 48.dp.toPx() else 75.dp.toPx()
                                        if (glideActive || netDragDistance > maxDragAllowed) {
                                            isCanceled = true
                                            longPressJob?.cancel()
                                        }
                                    }
                                } else {
                                    if (showPopup && alternateOptions.isNotEmpty()) {
                                        val deltaX = change.position.x - down.position.x
                                        val deltaY = change.position.y - down.position.y
                                        if (deltaY > 80.dp.toPx() || deltaY < -150.dp.toPx()) {
                                            isCanceled = true
                                            showPopup = false
                                        } else {
                                            val offsetIdx = (deltaX / itemStepPx).toInt()
                                            val newIdx = (defaultSelectedIndex + offsetIdx).coerceIn(0, alternateOptions.lastIndex)
                                            if (newIdx != selectedOptionIndex) {
                                                selectedOptionIndex = newIdx
                                                currentOnSelectionTick?.invoke()
                                            }
                                        }
                                    } else {
                                        if (netDragDistance > 75.dp.toPx()) {
                                            isCanceled = true
                                        }
                                    }
                                }
                            } else {
                                change.consume()
                                break
                            }
                        }

                        if (isLongPressTriggered && !isCanceled) {
                            if (alternateOptions.isNotEmpty()) {
                                val textToInsert = alternateOptions.getOrElse(selectedOptionIndex) { 
                                    key.alternateText.ifEmpty { key.primaryText } 
                                }
                                currentOnInsertText?.invoke(textToInsert)
                            } else if (!isLongPressActionFired) {
                                val textToInsert = key.alternateText.ifEmpty { key.primaryText }
                                currentOnInsertText?.invoke(textToInsert)
                            }
                            showPopup = false
                        } else if (!isSwipeHandled && !isCanceled && !currentIsGlideActive.invoke() && currentKey.type != KeyType.BACKSPACE) {
                            currentOnTap()
                        }
                    } finally {
                        isPressed = false
                        showPopup = false
                        deleteJob?.cancel()
                        longPressJob?.cancel()
                    }
                }
            }
            .padding(preferences.keySpacingDp.dp / 2)
            .graphicsLayer {
                val scale = if (isPressed) 0.94f else 1.0f
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = theme.elevationDp.dp, shape = shape, clip = false)
            .background(keyBg, shape)
            .then(if (preferences.showKeyBorders) Modifier.border(1.dp, if (isPressed) theme.accentColor else theme.borderColor, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        KeyContent(key, theme, displayText, currentShiftState, imeAction, isAccentEnter)

        if (key.alternateText.isNotEmpty() && key.type == KeyType.CHARACTER) {
            Text(
                text = key.alternateText,
                color = theme.secondaryTextColor.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
            )
        }

        if (showPopup && isPressed && alternateOptions.isNotEmpty()) {
            KeyLongPressPopup(alternateOptions, selectedOptionIndex, theme, isLeftEdge, isRightEdge)
        }

        if (isPressed && !showPopup && preferences.isPopupPreviewEnabled && key.type == KeyType.CHARACTER && displayText.isNotEmpty()) {
            KeyPreviewOverlay(displayText, theme)
        }
    }
}

@Composable
private fun KeyContent(
    key: KeyModel,
    theme: KeyboardTheme,
    displayText: String,
    shiftState: ShiftState,
    imeAction: Int,
    isAccentEnter: Boolean
) {
    if (key.type == KeyType.SHIFT) {
        val shiftIcon = if (shiftState == ShiftState.CAPS_LOCK) R.drawable.ic_caps_lock else R.drawable.ic_shift
        Icon(
            painter = painterResource(id = shiftIcon),
            contentDescription = "Shift",
            tint = if (shiftState != ShiftState.OFF) theme.accentColor else theme.textColor,
            modifier = Modifier.size(20.dp)
        )
    } else if (key.type == KeyType.ENTER) {
        val resolvedIcon = when (imeAction and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            EditorInfo.IME_ACTION_GO -> R.drawable.ic_arrow_forward
            EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check
            EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_arrow_forward
            else -> key.iconResId ?: R.drawable.ic_return
        }
        Icon(
            painter = painterResource(id = resolvedIcon),
            contentDescription = "Action",
            tint = if (isAccentEnter) Color.White else theme.accentColor,
            modifier = Modifier.size(20.dp)
        )
    } else if (key.iconResId != null) {
        Icon(
            painter = painterResource(id = key.iconResId),
            contentDescription = key.type.name,
            tint = theme.textColor,
            modifier = Modifier.size(20.dp)
        )
    } else {
        Text(
            text = displayText,
            color = if (key.type == KeyType.SPACE) theme.secondaryTextColor.copy(alpha = 0.6f) else theme.textColor,
            fontSize = when {
                key.type == KeyType.SPACE -> 12.sp
                key.type == KeyType.MODE_CHANGE -> 14.sp
                key.primaryText.length > 1 -> 13.sp
                else -> 23.sp
            },
            fontFamily = if (theme.isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (key.type == KeyType.MODE_CHANGE) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun KeyLongPressPopup(
    alternateOptions: List<String>,
    selectedOptionIndex: Int,
    theme: KeyboardTheme,
    isLeftEdge: Boolean,
    isRightEdge: Boolean
) {
    val popupAlignment = when {
        isLeftEdge -> Alignment.BottomStart
        isRightEdge -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }

    Box(
        modifier = Modifier
            .wrapContentSize(popupAlignment, unbounded = true)
            .zIndex(9999f)
            .offset(y = (-58).dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(theme.popupColor)
            .border(1.5.dp, theme.accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            alternateOptions.forEachIndexed { index, option ->
                val isSelected = index == selectedOptionIndex
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 38.dp, minHeight = 46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) theme.accentColor else Color.Transparent)
                        .then(if (isSelected) Modifier.border(1.dp, theme.accentColor, RoundedCornerShape(8.dp)) else Modifier)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) Color.White else theme.popupTextColor,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyPreviewOverlay(displayText: String, theme: KeyboardTheme) {
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.BottomCenter, unbounded = true)
            .zIndex(500f)
            .offset(y = (-56).dp)
            .size(50.dp, 56.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(theme.popupColor)
            .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = theme.popupTextColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

