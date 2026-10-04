package com.example.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import com.android.inputmethod.keyboard.internal.PointerTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private class PointerTrack(
    val pointerId: PointerId,
    val initialKeyPos: KeyPosition,
    var currentKeyPos: KeyPosition,
    val downPosition: Offset,
    var currentPosition: Offset,
    val downTimeMs: Long,
    var committed: Boolean = false,
    var longPressFired: Boolean = false,
    var popupShowing: Boolean = false,
    val popupOptions: List<String> = emptyList(),
    var selectedOptionIndex: Int = 0,
    var dragHandled: Boolean = false,
    var lastCursorSwipeX: Float = downPosition.x,
    var longPressJob: Job? = null,
    var repeatJob: Job? = null
)

data class ActivePopupInfo(
    val keyPosition: KeyPosition,
    val options: List<String>,
    val selectedIndex: Int,
    val isLeftEdge: Boolean,
    val isRightEdge: Boolean
)

/**
 * Performance-first dispatcher. AOSP PointerTracker is authoritative for simple character taps;
 * Compose state is deliberately not published for those taps because a full keyboard recomposition
 * on every DOWN/UP pair is unnecessary work in the IME hot path.
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
    private val activePointers = HashMap<PointerId, PointerTrack>(4)
    private val aospTapPositions = HashMap<PointerId, KeyPosition>(4)
    private val aospPointerTracker = PointerTracker()
    private val pressedPositionsCache = HashSet<KeyPosition>(4)

    var pressedKeyPositions: Set<KeyPosition> = emptySet()
        private set
    var activePopup: ActivePopupInfo? = null
        private set
    var previewKeyPosition: KeyPosition? = null
        private set

    private var onStateChangedListener: (() -> Unit)? = null

    init {
        aospPointerTracker.setKeyEventHandler(object : PointerTracker.KeyEventHandler {
            override fun onDownKey(code: Int, text: String, isShift: Boolean) {
                if (text.isNotEmpty()) onKeyTap(KeyModel(primaryText = text, shiftedText = if (isShift) text.uppercase() else text))
            }
            override fun onUpKey(code: Int, text: String, isShift: Boolean) {
                if (text.isNotEmpty()) onKeyTap(KeyModel(primaryText = text, shiftedText = if (isShift) text.uppercase() else text))
            }
            override fun onLongPress(code: Int, text: String) = Unit
            override fun onSwipeDelete() = onDeleteWord()
            override fun onMoveCursor(steps: Int) = this@KeyboardTouchCoordinator.onMoveCursor.invoke(steps)
            override fun onHapticFeedback() = onSelectionTick()
        })
    }

    fun setOnStateChangedListener(listener: () -> Unit) { onStateChangedListener = listener }

    private fun notifyStateChanged() {
        pressedPositionsCache.clear()
        var latest: PointerTrack? = null
        for (track in activePointers.values) {
            pressedPositionsCache.add(track.currentKeyPos)
            if (!track.committed && !track.popupShowing && track.initialKeyPos.key.type == KeyType.CHARACTER &&
                track.initialKeyPos.key.primaryText.isNotEmpty() && (latest == null || track.downTimeMs > latest.downTimeMs)) latest = track
        }
        for (keyPos in aospTapPositions.values) pressedPositionsCache.add(keyPos)
        pressedKeyPositions = if (pressedPositionsCache.isEmpty()) emptySet() else pressedPositionsCache.toSet()
        previewKeyPosition = if (isPopupPreviewEnabled()) latest?.initialKeyPos else null
        onStateChangedListener?.invoke()
    }

    fun onPointerDown(change: PointerInputChange) {
        val geometry = getGeometry() ?: return
        val keyPos = geometry.findKeyAtOffset(change.position) ?: return
        val key = keyPos.key

        if (key.type == KeyType.CHARACTER && key.longPressOptions.isEmpty() && key.alternateText.isEmpty()) {
            aospTapPositions[change.id] = keyPos
            aospPointerTracker.setShifted(isShifted())
            aospPointerTracker.onDown(
                change.id.value.toInt(),
                change.position.x.toInt(),
                change.position.y.toInt(),
                System.currentTimeMillis(),
                key.primaryText.firstOrNull()?.code ?: 0,
                key.primaryText
            )
            // Commit immediately for minimum latency. AOSP still retains pointer state for rollover.
            aospPointerTracker.markCommitted(change.id.value.toInt())
            // Deliberately no Compose state publication for ordinary characters.
            return
        }

        val now = System.currentTimeMillis()
        val shifted = isShifted()
        val alternateOptions = buildAlternateOptions(key, shifted)
        val isLeftEdge = key.primaryText in LEFT_EDGE_KEYS
        val isRightEdge = key.primaryText in RIGHT_EDGE_KEYS
        val defaultIdx = when {
            isLeftEdge -> 0
            isRightEdge -> (alternateOptions.size - 1).coerceAtLeast(0)
            else -> alternateOptions.size / 2
        }
        val track = PointerTrack(change.id, keyPos, keyPos, change.position, change.position, now, popupOptions = alternateOptions, selectedOptionIndex = defaultIdx)
        activePointers[change.id] = track

        when (key.type) {
            KeyType.SHIFT -> { track.committed = true; onKeyTap(key) }
            KeyType.BACKSPACE -> {
                track.committed = true
                onBackspace()
                track.repeatJob = scope.launch {
                    delay(300L)
                    var repeatDelay = 60L
                    while (isActive && activePointers.containsKey(track.pointerId)) {
                        onBackspace()
                        delay(repeatDelay)
                        if (repeatDelay > 30L) repeatDelay -= 5L
                    }
                }
            }
            KeyType.CHARACTER -> {
                if (alternateOptions.isEmpty()) { track.committed = true; onKeyTap(key) }
                else scheduleLongPress(track, key, alternateOptions, isLeftEdge, isRightEdge, defaultIdx, shifted)
            }
            else -> scheduleLongPress(track, key, alternateOptions, isLeftEdge, isRightEdge, defaultIdx, shifted)
        }
        notifyStateChanged()
    }

    private fun scheduleLongPress(track: PointerTrack, key: KeyModel, alternateOptions: List<String>, isLeftEdge: Boolean, isRightEdge: Boolean, defaultIdx: Int, shifted: Boolean) {
        val delayMs = getLongPressDelayMs().coerceAtLeast(120).toLong()
        track.longPressJob = scope.launch {
            delay(delayMs)
            if (!isActive || !activePointers.containsKey(track.pointerId) || track.committed || track.dragHandled) return@launch
            track.longPressFired = true
            onLongPressTick()
            if (alternateOptions.isNotEmpty()) {
                track.popupShowing = true
                activePopup = ActivePopupInfo(track.initialKeyPos, alternateOptions, defaultIdx, isLeftEdge, isRightEdge)
                notifyStateChanged()
            } else if (key.alternateText.isNotEmpty()) {
                onKeyInsertText(if (shifted) key.alternateText.uppercase() else key.alternateText)
                track.committed = true
                notifyStateChanged()
            }
        }
    }

    fun onPointerMove(change: PointerInputChange) {
        if (aospTapPositions.containsKey(change.id)) return
        val track = activePointers[change.id] ?: return
        val currentPos = change.position
        track.currentPosition = currentPos

        if (track.popupShowing && track.popupOptions.isNotEmpty()) {
            val deltaX = currentPos.x - track.downPosition.x
            val itemStepPx = 40f
            val baseIdx = when {
                activePopup?.isLeftEdge == true -> 0
                activePopup?.isRightEdge == true -> (track.popupOptions.size - 1).coerceAtLeast(0)
                else -> track.popupOptions.size / 2
            }
            val newIdx = (baseIdx + (deltaX / itemStepPx).toInt()).coerceIn(0, track.popupOptions.lastIndex)
            if (newIdx != track.selectedOptionIndex) {
                track.selectedOptionIndex = newIdx
                activePopup = activePopup?.copy(selectedIndex = newIdx)
                onSelectionTick()
                notifyStateChanged()
            }
            return
        }

        if (track.initialKeyPos.key.type == KeyType.SPACE && isSpaceSwipeEnabled()) {
            val delta = currentPos.x - track.lastCursorSwipeX
            val threshold = 28f
            if (abs(delta) >= threshold) {
                track.dragHandled = true
                track.longPressJob?.cancel()
                val steps = (delta / threshold).toInt()
                if (steps != 0) {
                    onMoveCursor(steps)
                    track.lastCursorSwipeX += steps * threshold
                    onSelectionTick()
                }
            }
            return
        }

        if (track.initialKeyPos.key.type == KeyType.BACKSPACE && isBackspaceSwipeEnabled() && currentPos.x - track.downPosition.x < -50f && !track.dragHandled) {
            track.dragHandled = true
            track.repeatJob?.cancel()
            onDeleteWord()
            return
        }

        if ((currentPos - track.downPosition).getDistance() > 30f && !track.longPressFired) track.longPressJob?.cancel()
        val newKeyPos = getGeometry()?.findKeyAtOffset(currentPos)
        if (newKeyPos != null && newKeyPos != track.currentKeyPos) {
            track.currentKeyPos = newKeyPos
            notifyStateChanged()
        }
    }

    fun onPointerUp(change: PointerInputChange) {
        if (aospTapPositions.remove(change.id) != null) {
            aospPointerTracker.onUp(change.id.value.toInt())
            // No full keyboard recomposition for ordinary character UP.
            return
        }

        val track = activePointers.remove(change.id) ?: return
        track.longPressJob?.cancel()
        track.repeatJob?.cancel()
        if (track.popupShowing) {
            val text = track.popupOptions.getOrNull(track.selectedOptionIndex)
                ?: track.initialKeyPos.key.alternateText.ifEmpty { track.initialKeyPos.key.primaryText }
            if (text.isNotEmpty()) onKeyInsertText(text)
            activePopup = null
        } else if (!track.committed && !track.longPressFired && !track.dragHandled) onKeyTap(track.initialKeyPos.key)
        notifyStateChanged()
    }

    fun onCancel() {
        if (activePointers.isEmpty() && aospTapPositions.isEmpty() && activePopup == null && previewKeyPosition == null) return
        for (track in activePointers.values) { track.longPressJob?.cancel(); track.repeatJob?.cancel() }
        activePointers.clear()
        aospTapPositions.clear()
        aospPointerTracker.onCancel()
        activePopup = null
        previewKeyPosition = null
        pressedKeyPositions = emptySet()
        pressedPositionsCache.clear()
        onStateChangedListener?.invoke()
    }

    private fun buildAlternateOptions(key: KeyModel, shifted: Boolean): List<String> {
        val raw = if (key.longPressOptions.isNotEmpty()) key.longPressOptions.filter { it.lowercase() != key.primaryText.lowercase() }
        else if (key.alternateText.isNotEmpty()) listOf(key.alternateText) else emptyList()
        val options = if (key.alternateText.isNotEmpty() && !raw.contains(key.alternateText)) listOf(key.alternateText) + raw else raw
        return if (shifted) options.map { if (it.length == 1 && it[0].isLetter()) it.uppercase() else it } else options
    }

    private companion object {
        val LEFT_EDGE_KEYS = setOf("q", "1", "a", "z")
        val RIGHT_EDGE_KEYS = setOf("p", "0", "l", "m")
    }
}
