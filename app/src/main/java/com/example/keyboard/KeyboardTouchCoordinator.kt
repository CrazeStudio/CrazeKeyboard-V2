package com.example.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Data class representing an active popup triggered by long-press.
 */
data class ActivePopupInfo(
    val keyPosition: KeyPosition,
    val options: List<String>,
    val selectedIndex: Int,
    val isLeftEdge: Boolean,
    val isRightEdge: Boolean
)

/**
 * Independent pointer tracking state for pointer-aware multitouch.
 */
class PointerTrack(
    val pointerId: PointerId,
    val initialKeyPos: KeyPosition,
    var currentKeyPos: KeyPosition,
    val downPosition: Offset,
    var currentPosition: Offset,
    val downTimeMs: Long,
    var isCommitted: Boolean = false,
    var isLongPressFired: Boolean = false,
    var isPopupShowing: Boolean = false,
    var popupOptions: List<String> = emptyList(),
    var selectedOptionIndex: Int = 0,
    var isDragActionHandled: Boolean = false,
    var longPressJob: Job? = null,
    var repeatJob: Job? = null
)

/**
 * Touch coordinator managing multitouch, rollover typing, key repeat,
 * and popup states for high-frequency typing with zero dropped characters.
 */
class KeyboardTouchCoordinator(
    private val scope: CoroutineScope,
    private val onKeyTap: (KeyModel) -> Unit,
    private val onKeyInsertText: (String) -> Unit,
    private val onBackspace: () -> Unit,
    private val onDeleteWord: () -> Unit,
    private val onMoveCursor: (Int) -> Unit,
    private val onLongPressTick: () -> Unit,
    private val onSelectionTick: () -> Unit,
    private val getGeometry: () -> KeyboardGeometry?,
    private val getLongPressDelayMs: () -> Int,
    private val isPopupPreviewEnabled: () -> Boolean,
    private val isSpaceSwipeEnabled: () -> Boolean,
    private val isBackspaceSwipeEnabled: () -> Boolean,
    private val isShifted: () -> Boolean
) {
    private val activePointers = HashMap<PointerId, PointerTrack>(8)

    // Observable states for UI rendering
    var pressedKeyPositions: Set<KeyPosition> = emptySet()
        private set
    var activePopup: ActivePopupInfo? = null
        private set
    var previewKeyPosition: KeyPosition? = null
        private set

    private var onStateChangedListener: (() -> Unit)? = null

    fun setOnStateChangedListener(listener: () -> Unit) {
        onStateChangedListener = listener
    }

    private fun notifyStateChanged() {
        pressedKeyPositions = activePointers.values.map { it.currentKeyPos }.toSet()
        onStateChangedListener?.invoke()
    }

    /**
     * Handles pointer down events.
     */
    fun onPointerDown(change: PointerInputChange) {
        val geometry = getGeometry() ?: return
        val keyPos = geometry.findKeyAtOffset(change.position) ?: return
        val key = keyPos.key
        val now = System.currentTimeMillis()

        // Rollover typing optimization:
        // If an earlier pointer is still holding an uncommitted character key,
        // commit it immediately so no characters are lost during rapid sequential typing!
        for (track in activePointers.values) {
            if (!track.isCommitted && !track.isLongPressFired && !track.isPopupShowing) {
                if (track.initialKeyPos.key.type == KeyType.CHARACTER ||
                    track.initialKeyPos.key.type == KeyType.SPACE ||
                    track.initialKeyPos.key.type == KeyType.MODE_CHANGE ||
                    track.initialKeyPos.key.type == KeyType.EMOJI
                ) {
                    track.longPressJob?.cancel()
                    track.isCommitted = true
                    onKeyTap(track.initialKeyPos.key)
                }
            }
        }

        // Prepare alternate options for long-press
        val shifted = isShifted()
        val alternateOptions = buildAlternateOptions(key, shifted)

        val isLeftEdge = key.primaryText in listOf("q", "1", "a", "z")
        val isRightEdge = key.primaryText in listOf("p", "0", "l", "m")
        val defaultIdx = when {
            isLeftEdge -> 0
            isRightEdge -> (alternateOptions.size - 1).coerceAtLeast(0)
            else -> alternateOptions.size / 2
        }

        val track = PointerTrack(
            pointerId = change.id,
            initialKeyPos = keyPos,
            currentKeyPos = keyPos,
            downPosition = change.position,
            currentPosition = change.position,
            downTimeMs = now,
            popupOptions = alternateOptions,
            selectedOptionIndex = defaultIdx
        )

        activePointers[change.id] = track

        // Show key preview popup for characters if enabled
        if (isPopupPreviewEnabled() && key.type == KeyType.CHARACTER && key.primaryText.isNotEmpty()) {
            previewKeyPosition = keyPos
        }

        // Handle Backspace auto-repeat
        if (key.type == KeyType.BACKSPACE) {
            track.isCommitted = true
            onBackspace()
            track.repeatJob = scope.launch {
                delay(300L)
                var delayMs = 60L
                while (isActive) {
                    onBackspace()
                    delay(delayMs)
                    if (delayMs > 30L) delayMs -= 5L
                }
            }
        } else {
            // Schedule long-press timer for keys with alternates or long press options
            val longPressDelay = getLongPressDelayMs().toLong()
            track.longPressJob = scope.launch {
                delay(longPressDelay)
                if (isActive && !track.isCommitted && !track.isDragActionHandled) {
                    track.isLongPressFired = true
                    onLongPressTick()

                    if (alternateOptions.isNotEmpty()) {
                        track.isPopupShowing = true
                        activePopup = ActivePopupInfo(
                            keyPosition = keyPos,
                            options = alternateOptions,
                            selectedIndex = defaultIdx,
                            isLeftEdge = isLeftEdge,
                            isRightEdge = isRightEdge
                        )
                        previewKeyPosition = null
                        notifyStateChanged()
                    } else if (key.alternateText.isNotEmpty()) {
                        val text = if (shifted) key.alternateText.uppercase() else key.alternateText
                        onKeyInsertText(text)
                        track.isCommitted = true
                    }
                }
            }
        }

        notifyStateChanged()
    }

    /**
     * Handles pointer movement events.
     */
    fun onPointerMove(change: PointerInputChange) {
        val track = activePointers[change.id] ?: return
        val currentPos = change.position
        val dragDist = (currentPos - track.downPosition).getDistance()
        track.currentPosition = currentPos

        // 1. Popup option selection tracking
        if (track.isPopupShowing && track.popupOptions.isNotEmpty()) {
            val deltaX = currentPos.x - track.downPosition.x
            val itemStepPx = 40f
            val isLeftEdge = activePopup?.isLeftEdge == true
            val isRightEdge = activePopup?.isRightEdge == true
            val baseIdx = when {
                isLeftEdge -> 0
                isRightEdge -> (track.popupOptions.size - 1).coerceAtLeast(0)
                else -> track.popupOptions.size / 2
            }
            val offsetIdx = (deltaX / itemStepPx).toInt()
            val newIdx = (baseIdx + offsetIdx).coerceIn(0, track.popupOptions.lastIndex)
            if (newIdx != track.selectedOptionIndex) {
                track.selectedOptionIndex = newIdx
                activePopup = activePopup?.copy(selectedIndex = newIdx)
                onSelectionTick()
                notifyStateChanged()
            }
            return
        }

        // 2. Spacebar horizontal swipe cursor control
        if (track.initialKeyPos.key.type == KeyType.SPACE && isSpaceSwipeEnabled()) {
            val deltaX = currentPos.x - track.downPosition.x
            if (abs(deltaX) > 24f) {
                track.isDragActionHandled = true
                track.longPressJob?.cancel()
                val steps = (deltaX / 28f).toInt()
                if (steps != 0) {
                    onMoveCursor(if (steps > 0) 1 else -1)
                    // Reset reference position slightly for continuous scrolling
                    track.downPosition.let {
                        val newRefX = currentPos.x
                        // Move cursor
                    }
                }
            }
            return
        }

        // 3. Backspace swipe delete word
        if (track.initialKeyPos.key.type == KeyType.BACKSPACE && isBackspaceSwipeEnabled()) {
            val deltaX = currentPos.x - track.downPosition.x
            if (deltaX < -50f && !track.isDragActionHandled) {
                track.isDragActionHandled = true
                track.repeatJob?.cancel()
                onDeleteWord()
            }
            return
        }

        // 4. Movement beyond slop cancels long-press
        if (dragDist > 30f && !track.isLongPressFired) {
            track.longPressJob?.cancel()
        }

        // Update current key under finger
        val geometry = getGeometry()
        if (geometry != null) {
            val newKeyPos = geometry.findKeyAtOffset(currentPos)
            if (newKeyPos != null && newKeyPos != track.currentKeyPos) {
                track.currentKeyPos = newKeyPos
                notifyStateChanged()
            }
        }
    }

    /**
     * Handles pointer up events.
     */
    fun onPointerUp(change: PointerInputChange) {
        val track = activePointers.remove(change.id) ?: return
        track.longPressJob?.cancel()
        track.repeatJob?.cancel()

        if (previewKeyPosition == track.initialKeyPos) {
            previewKeyPosition = null
        }

        if (track.isPopupShowing) {
            // Commit selected alternate character from popup
            val textToInsert = track.popupOptions.getOrNull(track.selectedOptionIndex)
                ?: track.initialKeyPos.key.alternateText.ifEmpty { track.initialKeyPos.key.primaryText }
            if (textToInsert.isNotEmpty()) {
                onKeyInsertText(textToInsert)
            }
            activePopup = null
        } else if (!track.isCommitted && !track.isLongPressFired && !track.isDragActionHandled) {
            // Normal tap release
            onKeyTap(track.initialKeyPos.key)
            track.isCommitted = true
        }

        notifyStateChanged()
    }

    /**
     * Handles cancellation of touches (e.g. system dialog or window focus lost).
     */
    fun onCancel() {
        for (track in activePointers.values) {
            track.longPressJob?.cancel()
            track.repeatJob?.cancel()
        }
        activePointers.clear()
        activePopup = null
        previewKeyPosition = null
        notifyStateChanged()
    }

    private fun buildAlternateOptions(key: KeyModel, shifted: Boolean): List<String> {
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
        return if (shifted) {
            optionsWithAlt.map { opt -> if (opt.length == 1 && opt.first().isLetter()) opt.uppercase() else opt }
        } else {
            optionsWithAlt
        }
    }
}
