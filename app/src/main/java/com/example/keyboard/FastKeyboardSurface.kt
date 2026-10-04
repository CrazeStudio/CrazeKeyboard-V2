package com.example.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import com.example.core.SoundFeedbackHelper
import com.example.settings.KeyboardLayoutMode
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** Native, allocation-light keyboard surface. Text keys commit on DOWN for minimum latency. */
class FastKeyboardSurface(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }
    private val rect = RectF()
    private val handler = Handler(Looper.getMainLooper())
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sound = SoundFeedbackHelper.getInstance(context)

    private var rows: List<List<KeyModel>> = emptyList()
    private var theme: KeyboardTheme? = null
    private var preferences: KeyboardPreferences? = null
    private var keyboardMode = KeyboardMode.ALPHA
    private var shiftState = ShiftState.OFF

    private var onKeyTap: ((KeyModel) -> Unit)? = null
    private var onKeyLongPress: ((KeyModel) -> Unit)? = null
    private var onKeyInsertText: ((String) -> Unit)? = null
    private var onMoveCursor: ((Int) -> Unit)? = null
    private var onDeleteWord: (() -> Unit)? = null
    private var onBackspace: (() -> Unit)? = null
    private var onGlideWord: ((String) -> Unit)? = null
    private var suggestionEngine: SuggestionEngine? = null

    private data class PointerState(
        val id: Int,
        var key: KeyModel?,
        var keyIndex: Int,
        var rowIndex: Int,
        var downX: Float,
        var downY: Float,
        var lastX: Float,
        var committed: Boolean = false,
        var longPressed: Boolean = false,
        var dragHandled: Boolean = false,
        var repeat: Boolean = false,
        var popupIndex: Int = 0,
        var glide: Boolean = false,
        val glideChars: MutableList<Char> = ArrayList(4)
    )

    private val pointers = HashMap<Int, PointerState>(4)
    private val pressed = HashSet<Int>(4)
    private var floatingX = 0f
    private var floatingY = 0f
    private var twoFingerDrag = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragOriginX = 0f
    private var dragOriginY = 0f

    fun configure(
        rows: List<List<KeyModel>>,
        theme: KeyboardTheme,
        preferences: KeyboardPreferences,
        keyboardMode: KeyboardMode,
        shiftState: ShiftState,
        imeAction: Int,
        suggestionEngine: SuggestionEngine,
        onKeyTap: (KeyModel) -> Unit,
        onKeyLongPress: (KeyModel) -> Unit,
        onKeyInsertText: (String) -> Unit,
        onMoveCursor: (Int) -> Unit,
        onDeleteWord: () -> Unit,
        onBackspace: () -> Unit,
        onGlideWord: (String) -> Unit
    ) {
        val rowsChanged = this.rows !== rows
        this.rows = rows
        this.theme = theme
        this.preferences = preferences
        this.keyboardMode = keyboardMode
        this.shiftState = shiftState
        this.suggestionEngine = suggestionEngine
        this.onKeyTap = onKeyTap
        this.onKeyLongPress = onKeyLongPress
        this.onKeyInsertText = onKeyInsertText
        this.onMoveCursor = onMoveCursor
        this.onDeleteWord = onDeleteWord
        this.onBackspace = onBackspace
        this.onGlideWord = onGlideWord
        if (rowsChanged) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = theme ?: return
        val p = preferences ?: return
        if (rows.isEmpty() || width <= 0 || height <= 0) return

        val keyboardWidth = when (p.layoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT, KeyboardLayoutMode.ONE_HANDED_RIGHT -> width * 0.76f
            KeyboardLayoutMode.FLOATING -> width * 0.82f
            KeyboardLayoutMode.DEFAULT -> width.toFloat()
        }
        val baseLeft = when (p.layoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT -> 0f
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> width - keyboardWidth
            KeyboardLayoutMode.FLOATING -> (width - keyboardWidth) / 2f + floatingX
            KeyboardLayoutMode.DEFAULT -> 0f
        }
        val baseTop = if (p.layoutMode == KeyboardLayoutMode.FLOATING) floatingY else 0f
        val keyGap = dp(p.keySpacingDp.toFloat())
        val keyHeight = dp(p.keyHeightDp.toFloat())
        val corner = dp(p.keyCornerRadiusDp.toFloat())

        paint.style = Paint.Style.FILL
        paint.color = t.backgroundColor.toArgbInt()
        canvas.drawColor(paint.color)

        var y = baseTop
        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = if (p.isNumberRowEnabled && rowIndex == 0 && keyboardMode == KeyboardMode.ALPHA) keyHeight * 0.85f else keyHeight
            val totalWeight = row.sumOf { max(0.1f, it.weight).toDouble() }.toFloat()
            var x = baseLeft
            row.forEachIndexed { keyIndex, key ->
                val usable = keyboardWidth - keyGap * (row.size + 1)
                val keyWidth = usable * (max(0.1f, key.weight) / totalWeight)
                val left = x + keyGap
                val top = y + keyGap
                val right = left + keyWidth
                val bottom = top + rowHeight - keyGap * 2
                val pressedNow = pressed.contains(keyIndexFor(rowIndex, keyIndex))
                rect.set(left, top, right, bottom)
                paint.color = if (pressedNow) t.accentColor.toArgbInt() else
                    if (key.type == KeyType.ACTION || key.type == KeyType.SHIFT || key.type == KeyType.BACKSPACE || key.type == KeyType.ENTER) t.specialKeyColor.toArgbInt() else t.keyColor.toArgbInt()
                canvas.drawRoundRect(rect, corner, corner, paint)

                val label = when {
                    key.iconResId != null -> ""
                    key.type == KeyType.SHIFT -> "⇧"
                    key.type == KeyType.BACKSPACE -> "⌫"
                    key.type == KeyType.ENTER -> "↵"
                    key.type == KeyType.SPACE -> ""
                    key.type == KeyType.EMOJI -> "☺"
                    key.type == KeyType.MODE_CHANGE -> "123"
                    else -> if (shiftState != ShiftState.OFF) key.shiftedText else key.primaryText
                }
                if (label.isNotEmpty()) {
                    paint.color = t.textColor.toArgbInt()
                    paint.textAlign = Paint.Align.CENTER
                    paint.textSize = dp(if (label.length > 2) 13f else 20f)
                    paint.typeface = if (t.isMonospace) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
                    val fm = paint.fontMetrics
                    val textY = top + (bottom - top) / 2f - (fm.ascent + fm.descent) / 2f
                    canvas.drawText(label, (left + right) / 2f, textY, paint)
                }
                x += keyWidth + keyGap
            }
            y += rowHeight
        }

        val popupPointer = pointers.values.firstOrNull { it.longPressed && it.key != null }
        if (popupPointer != null && popupPointer.key!!.longPressOptions.isNotEmpty()) drawPopup(canvas, popupPointer, t)
    }

    private fun drawPopup(canvas: Canvas, state: PointerState, t: KeyboardTheme) {
        val key = state.key ?: return
        val options = key.longPressOptions
        if (options.isEmpty()) return
        val centerX = state.downX
        val bottom = state.downY - dp(8f)
        val h = dp(48f)
        val itemW = dp(42f)
        val left = centerX - itemW * options.size / 2f
        paint.color = t.popupColor.toArgbInt()
        rect.set(left, bottom - h, left + itemW * options.size, bottom)
        canvas.drawRoundRect(rect, dp(10f), dp(10f), paint)
        paint.color = t.popupTextColor.toArgbInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(22f)
        options.forEachIndexed { i, option ->
            val x = left + itemW * i + itemW / 2f
            val fm = paint.fontMetrics
            canvas.drawText(option, x, bottom - h / 2f - (fm.ascent + fm.descent) / 2f, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (rows.isEmpty()) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { twoFingerDrag = false; handleDown(event, 0); return true }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (preferences?.layoutMode == KeyboardLayoutMode.FLOATING && event.pointerCount >= 2) {
                    twoFingerDrag = true
                    dragStartX = averageX(event); dragStartY = averageY(event)
                    dragOriginX = floatingX; dragOriginY = floatingY
                    pointers.values.forEach(::cancelLongPress)
                } else handleDown(event, event.actionIndex)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (twoFingerDrag && preferences?.layoutMode == KeyboardLayoutMode.FLOATING && event.pointerCount >= 2) {
                    floatingX = (dragOriginX + averageX(event) - dragStartX).coerceIn(-width * .35f, width * .35f)
                    floatingY = (dragOriginY + averageY(event) - dragStartY).coerceIn(-height * .35f, height * .15f)
                    invalidate(); return true
                }
                for (i in 0 until event.pointerCount) handleMove(event, i)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> { if (!twoFingerDrag) handleUp(event.getPointerId(event.actionIndex)); return true }
            MotionEvent.ACTION_UP -> { if (!twoFingerDrag) handleUp(event.getPointerId(0)) else cancelAll(); return true }
            MotionEvent.ACTION_CANCEL -> { cancelAll(); return true }
        }
        return true
    }

    private fun handleDown(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val point = eventToKey(event.getX(index), event.getY(index)) ?: return
        val key = point.third
        val state = PointerState(id, key, point.first, event.getX(index), event.getY(index), event.getX(index))
        state.rowIndex = point.second
        pointers[id] = state
        pressed.add(keyIndexFor(point.second, point.first))
        performFeedback()

        when (key.type) {
            KeyType.CHARACTER -> {
                state.committed = true
                onKeyTap?.invoke(key)
                if (key.longPressOptions.isNotEmpty() || key.alternateText.isNotEmpty()) scheduleLongPress(state)
                if (p.glideEnabled()) {
                    state.glide = true
                    key.primaryText.firstOrNull()?.let { state.glideChars.add(it.lowercaseChar()) }
                }
            }
            KeyType.BACKSPACE -> {
                state.committed = true
                onBackspace?.invoke()
                state.repeat = true
                scheduleBackspaceRepeat(state)
            }
            KeyType.SHIFT -> { state.committed = true; onKeyTap?.invoke(key) }
            else -> {
                if (key.longPressOptions.isNotEmpty() || key.alternateText.isNotEmpty()) scheduleLongPress(state)
            }
        }
        invalidate()
    }

    private fun handleMove(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val state = pointers[id] ?: return
        val x = event.getX(index); val y = event.getY(index)
        state.lastX = x
        if (state.longPressed && state.key?.longPressOptions?.isNotEmpty() == true) {
            val options = state.key!!.longPressOptions
            state.popupIndex = ((x - state.downX) / dp(42f) + options.size / 2f).toInt().coerceIn(0, options.lastIndex)
            invalidate(); return
        }
        if (state.key?.type == KeyType.SPACE && preferences?.isSpaceSwipeCursorEnabled == true) {
            val dx = x - state.downX
            if (abs(dx) >= dp(28f)) {
                val steps = (dx / dp(28f)).toInt()
                if (steps != 0) { state.dragHandled = true; state.downX += steps * dp(28f); onMoveCursor?.invoke(steps); performFeedback() }
            }
            return
        }
        if (state.key?.type == KeyType.BACKSPACE && preferences?.isBackspaceSwipeDeleteEnabled == true && x - state.downX < -dp(50f)) {
            state.dragHandled = true; state.repeat = false; cancelLongPress(state); onDeleteWord?.invoke(); return
        }
        if (state.glide && keyboardMode == KeyboardMode.ALPHA) {
            val hit = eventToKey(x, y)?.third
            if (hit?.type == KeyType.CHARACTER) hit.primaryText.firstOrNull()?.let { c ->
                val lower = c.lowercaseChar(); if (state.glideChars.lastOrNull() != lower) state.glideChars.add(lower)
            }
        }
        if ((x - state.downX) * (x - state.downX) + (y - state.downY) * (y - state.downY) > dp(30f) * dp(30f)) cancelLongPress(state)
    }

    private fun handleUp(id: Int) {
        val state = pointers.remove(id) ?: return
        cancelLongPress(state)
        pressed.remove(keyIndexFor(state.rowIndex, state.keyIndex))
        if (state.glide && state.glideChars.size >= 2 && !state.dragHandled) {
            val chars = state.glideChars.toList()
            workScope.launch {
                val word = suggestionEngine?.findGlideWord(chars)
                if (word != null) post { onGlideWord?.invoke(word) }
            }
        }
        if (state.longPressed && state.key?.longPressOptions?.isNotEmpty() == true) {
            val options = state.key!!.longPressOptions
            val selected = options.getOrNull(state.popupIndex) ?: options.firstOrNull()
            if (!selected.isNullOrEmpty()) { onBackspace?.invoke(); onKeyInsertText?.invoke(selected) }
        } else if (!state.committed && !state.dragHandled) state.key?.let { onKeyTap?.invoke(it) }
        invalidate()
    }

    private fun scheduleLongPress(state: PointerState) {
        val key = state.key ?: return
        if (key.longPressOptions.isEmpty() && key.alternateText.isEmpty()) return
        handler.postDelayed({
            if (pointers[state.id] === state && !state.dragHandled) {
                state.longPressed = true
                state.popupIndex = (key.longPressOptions.size / 2).coerceAtMost(key.longPressOptions.lastIndex.coerceAtLeast(0))
                performFeedback()
                invalidate()
            }
        }, preferences?.longPressDelayMs?.toLong() ?: 220L)
    }

    private fun scheduleBackspaceRepeat(state: PointerState) {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (pointers[state.id] !== state || !state.repeat) return
                onBackspace?.invoke(); handler.postDelayed(this, 45L)
            }
        }, 300L)
    }

    private fun cancelLongPress(state: PointerState) { state.dragHandled = true }

    private fun cancelAll() {
        handler.removeCallbacksAndMessages(null)
        pointers.clear(); pressed.clear(); invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        workScope.cancel()
        pointers.clear(); pressed.clear()
        super.onDetachedFromWindow()
    }

    private fun eventToKey(x: Float, y: Float): Triple<Int, Int, KeyModel>? {
        val p = preferences ?: return null
        val keyboardWidth = when (p.layoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT, KeyboardLayoutMode.ONE_HANDED_RIGHT -> width * .76f
            KeyboardLayoutMode.FLOATING -> width * .82f
            KeyboardLayoutMode.DEFAULT -> width.toFloat()
        }
        val left = when (p.layoutMode) {
            KeyboardLayoutMode.ONE_HANDED_LEFT -> 0f
            KeyboardLayoutMode.ONE_HANDED_RIGHT -> width - keyboardWidth
            KeyboardLayoutMode.FLOATING -> (width - keyboardWidth) / 2f + floatingX
            KeyboardLayoutMode.DEFAULT -> 0f
        }
        val topOffset = if (p.layoutMode == KeyboardLayoutMode.FLOATING) floatingY else 0f
        val gap = dp(p.keySpacingDp.toFloat())
        val keyHeight = dp(p.keyHeightDp.toFloat())
        var top = topOffset
        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = if (p.isNumberRowEnabled && rowIndex == 0 && keyboardMode == KeyboardMode.ALPHA) keyHeight * .85f else keyHeight
            if (y >= top && y <= top + rowHeight) {
                val totalWeight = row.sumOf { max(.1f, it.weight).toDouble() }.toFloat()
                var currentX = left
                val usable = keyboardWidth - gap * (row.size + 1)
                row.forEachIndexed { index, key ->
                    val kw = usable * (max(.1f, key.weight) / totalWeight)
                    val l = currentX + gap; val r = l + kw
                    if (x >= l && x <= r) return Triple(index, rowIndex, key)
                    currentX += kw + gap
                }
            }
            top += rowHeight
        }
        return null
    }

    private fun keyIndexFor(row: Int, index: Int): Int = row * 100 + index
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun performFeedback() {
        if (preferences?.isHapticsEnabled == true) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (preferences?.isSoundEnabled == true) sound.playKeyPress(preferences?.soundProfile ?: "iphone", preferences?.soundVolume ?: 80, true)
    }
    private fun averageX(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getX(i); return s / e.pointerCount }
    private fun averageY(e: MotionEvent): Float { var s = 0f; for (i in 0 until e.pointerCount) s += e.getY(i); return s / e.pointerCount }
    private fun KeyboardPreferences.glideEnabled(): Boolean = isGlideTypingEnabled
    private fun android.graphics.Color.toArgbInt(): Int = this.toArgb()
}
