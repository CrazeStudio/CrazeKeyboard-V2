package com.crazestudio.crazekeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View

class LatinKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(code: String)
        fun onSecondaryKey(subCode: String)
        fun onSecondaryReplace(oldCode: String, newSubCode: String)
        fun onKeyActionDown(code: String)
        fun onKeyActionUp(code: String)
        fun onSpaceDrag(isRight: Boolean)
        fun onBackspaceSwipe()
        fun onDeleteWord()
    }

    var listener: OnKeyboardActionListener? = null

    data class ThemeColors(
        val bg: Int = Color.rgb(27, 28, 32),               // #1B1C20 Material You Dark Slate Canvas
        val keySurface: Int = Color.rgb(44, 45, 51),       // #2C2D33 Keycap
        val keyBorder: Int = Color.rgb(58, 59, 68),        // #3A3B44 Clean subtle border
        val keyPressed: Int = Color.rgb(60, 62, 71),       // #3C3E47 Tactile pressed state
        val keyFunc: Int = Color.rgb(33, 34, 38),          // #212226 Functional keys (Shift, Backspace, ?123)
        val keyFuncBorder: Int = Color.rgb(48, 50, 56),    // #303238
        val accent: Int = Color.rgb(26, 115, 232),         // #1A73E8 Official Google Blue Action Pill
        val accentBorder: Int = Color.rgb(66, 133, 244),   // #4285F4
        val textPrimary: Int = Color.rgb(255, 255, 255),   // #FFFFFF Crisp White
        val textSecondary: Int = Color.rgb(158, 163, 176), // #9EA3B0 Subtle secondary symbols/hints
        val textMuted: Int = Color.rgb(125, 130, 142),     // #7D828E
        val keyShadow: Int = Color.argb(80, 0, 0, 0),      // Tangible key depth shadow
        val divider: Int = Color.rgb(52, 54, 62)           // #34363E Hairline divider line
    )

    var theme = ThemeColors()
        set(value) {
            field = value
            invalidate()
        }

    var isShifted: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var isCapsLock: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val rows = mutableListOf<List<LatinKey>>()
    private val allKeys = mutableListOf<LatinKey>()
    private val iconCache = mutableMapOf<Int, Drawable>()

    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val popupStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    // Multi-touch tracking with high-speed rollover support
    private class PointerTracker(
        val pointerId: Int,
        var key: LatinKey,
        var downX: Float,
        var downY: Float,
        var isLongPressed: Boolean = false,
        var dragged: Boolean = false,
        var committed: Boolean = false,
        var longPressRunnable: Runnable? = null
    )

    private val activePointers = SparseArray<PointerTracker>()
    var showPopups: Boolean = true
    var showSecondarySymbols: Boolean = true
    var keyboardHeightDp: Int = 226
    var keyRadiusDp: Float = 7.5f
    var keySpacingXDp: Float = 3f
    var keySpacingYDp: Float = 4f
    var keyStrokeWidthDp: Float = 0.8f

    fun loadPreferences(prefs: android.content.SharedPreferences) {
        keyboardHeightDp = prefs.getInt("pref_key_height", 226)
        keyRadiusDp = prefs.getFloat("pref_key_radius", 7.5f)
        keySpacingXDp = prefs.getFloat("pref_key_spacing_x", 3f)
        keySpacingYDp = prefs.getFloat("pref_key_spacing_y", 4f)
        keyStrokeWidthDp = prefs.getFloat("pref_key_stroke", 0.8f)
        showSecondarySymbols = prefs.getBoolean("longpress", true)
        showPopups = prefs.getBoolean("popups", true)
        requestLayout()
        invalidate()
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setKeyRows(newRows: List<List<LatinKey>>) {
        rows.clear()
        rows.addAll(newRows)
        allKeys.clear()
        newRows.flatten().forEach { allKeys.add(it) }
        if (width > 0 && height > 0) {
            layoutKeys(width, height)
        }
        invalidate()
    }

    fun findKeyByCode(code: String): LatinKey? = allKeys.firstOrNull { it.code == code }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Respect the available IME window size instead of forcing the preferred
        // key height. InputMethodService can provide AT_MOST/EXACTLY specs when the
        // navigation bar, display size, or app window reduces the available space.
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val desiredWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
            resources.displayMetrics.widthPixels
        } else {
            widthSize
        }
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val desiredHeight = dp(keyboardHeightDp).coerceAtLeast(dp(1))
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }.coerceAtLeast(0)

        setMeasuredDimension(measuredWidth, measuredHeight)
        layoutKeys(measuredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys(w, h)
    }

    private fun layoutKeys(width: Int, height: Int) {
        if (rows.isEmpty() || width <= 0 || height <= 0) return

        val padX = dp(keySpacingXDp)
        val numRows = rows.size
        if (numRows == 0) return

        // Scale vertical spacing down on short displays so key bounds stay inside
        // the measured keyboard and remain aligned with the visible touch surface.
        val padY = minOf(dp(keySpacingYDp), (height / (numRows * 2 + 1)).coerceAtLeast(0))
        val bottomSafePad = minOf(dp(16), (height / 8).coerceAtLeast(0))
        val availableH = (height - bottomSafePad - (padY * (numRows + 1))).coerceAtLeast(1)
        val rowH = availableH / numRows.toFloat()

        for ((rIndex, row) in rows.withIndex()) {
            val totalWeight = row.sumOf { it.weight.toDouble() }.toFloat()
            val numKeys = row.size

            // Check if this row is Row 2 in QWERTY (9 standard letter keys) requiring standard staggered indentation
            val isIndentedQwertyRow = (numKeys == 9 && Math.abs(totalWeight - 9f) < 0.1f && rIndex == 1)

            val curY = padY + rIndex * (rowH + padY)

            if (isIndentedQwertyRow) {
                // Width is calculated based on standard 10 units so each letter matches Row 1 exactly
                val unitWidth = (width - padX * 11) / 10f
                val sideMargin = padX + (unitWidth * 0.5f) + (padX * 0.5f)
                var curX = sideMargin

                for (k in row) {
                    k.bounds.set(curX, curY, curX + unitWidth, curY + rowH)
                    curX += unitWidth + padX
                }
            } else {
                val availableRowW = width - padX * (numKeys + 1)
                var curX = padX.toFloat()

                for (k in row) {
                    val keyW = (k.weight / totalWeight) * availableRowW
                    k.bounds.set(curX, curY, curX + keyW, curY + rowH)
                    curX += keyW + padX
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.bg)

        val keyRadius = dp(keyRadiusDp).toFloat()
        val pillRadius = dp(keyRadiusDp + 4.5f).toFloat()
        keyStrokePaint.strokeWidth = dp(keyStrokeWidthDp).toFloat()
        keyShadowPaint.color = theme.keyShadow

        val shadowOffset = dp(1.2f).toFloat()

        // 1. Draw all keys with tactile depth and smooth finish
        for (k in allKeys) {
            val rect = k.bounds
            val currentRadius = if (k.isSpace || k.isAction) pillRadius else keyRadius

            // Keycap Background & Border Colors
            when {
                k.isAction -> {
                    keyFillPaint.color = theme.accent
                    keyStrokePaint.color = theme.accentBorder
                }
                k.isFunctional -> {
                    keyFillPaint.color = if (k.isPressed) theme.keyPressed else theme.keyFunc
                    keyStrokePaint.color = theme.keyFuncBorder
                }
                else -> {
                    keyFillPaint.color = if (k.isPressed) theme.keyPressed else theme.keySurface
                    keyStrokePaint.color = if (k.isPressed) theme.accent else theme.keyBorder
                }
            }

            // Subtle tactile bottom shadow lip (adds genuine Gboard depth)
            if (!k.isPressed) {
                canvas.drawRoundRect(
                    rect.left,
                    rect.top + shadowOffset,
                    rect.right,
                    rect.bottom + shadowOffset,
                    currentRadius,
                    currentRadius,
                    keyShadowPaint
                )
            }

            // Main keycap surface
            canvas.drawRoundRect(rect, currentRadius, currentRadius, keyFillPaint)
            // Hairline natural border
            canvas.drawRoundRect(rect, currentRadius, currentRadius, keyStrokePaint)

            // Draw Icon or Text Label
            if (k.iconRes != 0) {
                drawKeyIcon(canvas, k)
            } else {
                drawKeyText(canvas, k)
            }

            // Draw Superscript secondary hint (top right)
            if (showSecondarySymbols && !k.subLabel.isNullOrEmpty() && !k.isFunctional) {
                subTextPaint.color = theme.textSecondary
                subTextPaint.textSize = dp(9.5f).toFloat()
                subTextPaint.typeface = Typeface.DEFAULT_BOLD
                val subX = rect.right - dp(4.5f)
                val subY = rect.top + dp(11.5f)
                canvas.drawText(k.subLabel, subX, subY, subTextPaint)
            }
        }

        // 2. Draw Magnified Popups above active keys
        if (showPopups) {
            for (i in 0 until activePointers.size()) {
                val tracker = activePointers.valueAt(i)
                if (!tracker.key.isFunctional && tracker.key.code.length == 1 && tracker.key.code != " ") {
                    drawPopupBubble(canvas, tracker.key)
                }
            }
        }
    }

    private fun drawKeyText(canvas: Canvas, key: LatinKey) {
        textPaint.color = if (key.isSpace) theme.textSecondary else theme.textPrimary
        textPaint.textSize = when {
            key.isSpace -> dp(13).toFloat()
            key.label.length > 2 -> dp(13.5f).toFloat()
            else -> dp(18f).toFloat()
        }
        textPaint.typeface = if (key.isFunctional) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        val fontMetrics = textPaint.fontMetrics
        val textY = key.bounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2

        val displayLabel = if (isShifted || isCapsLock) key.upperLabel else key.lowerLabel
        canvas.drawText(displayLabel, key.bounds.centerX(), textY, textPaint)
    }

    private fun drawKeyIcon(canvas: Canvas, key: LatinKey) {
        val d = iconCache.getOrPut(key.iconRes) {
            context.getDrawable(key.iconRes)!!.mutate()
        }
        val tint = when {
            key.isAction -> Color.WHITE
            key.code == "⇧" && (isShifted || isCapsLock) -> theme.accent
            else -> theme.textPrimary
        }
        d.setTint(tint)

        val iconSize = dp(21)
        val left = (key.bounds.centerX() - iconSize / 2).toInt()
        val top = (key.bounds.centerY() - iconSize / 2).toInt()
        d.setBounds(left, top, left + iconSize, top + iconSize)
        d.draw(canvas)
    }

    private fun drawPopupBubble(canvas: Canvas, key: LatinKey) {
        val bubbleW = dp(52).toFloat()
        val bubbleH = dp(50).toFloat()
        val cx = key.bounds.centerX()
        val top = key.bounds.top - bubbleH - dp(6)
        val left = cx - bubbleW / 2
        val popupRect = RectF(left, top, left + bubbleW, top + bubbleH)

        if (popupRect.left < dp(4)) popupRect.offset(dp(4) - popupRect.left, 0f)
        if (popupRect.right > width - dp(4)) popupRect.offset(width - dp(4) - popupRect.right, 0f)

        // Drop shadow for popup
        canvas.drawRoundRect(
            popupRect.left,
            popupRect.top + dp(1.5f),
            popupRect.right,
            popupRect.bottom + dp(1.5f),
            dp(9).toFloat(),
            dp(9).toFloat(),
            keyShadowPaint
        )

        popupBgPaint.color = theme.keySurface
        popupStrokePaint.color = theme.accent
        popupStrokePaint.strokeWidth = dp(1f).toFloat()

        val r = dp(9).toFloat()
        canvas.drawRoundRect(popupRect, r, r, popupBgPaint)
        canvas.drawRoundRect(popupRect, r, r, popupStrokePaint)

        popupTextPaint.color = theme.textPrimary
        popupTextPaint.textSize = dp(23).toFloat()
        popupTextPaint.typeface = Typeface.DEFAULT_BOLD
        val fm = popupTextPaint.fontMetrics
        val ty = popupRect.centerY() - (fm.ascent + fm.descent) / 2

        val displayLabel = if (isShifted || isCapsLock) key.upperLabel else key.lowerLabel
        canvas.drawText(displayLabel, popupRect.centerX(), ty, popupTextPaint)

        if (!key.subLabel.isNullOrEmpty()) {
            subTextPaint.color = theme.textSecondary
            subTextPaint.textSize = dp(9.5f).toFloat()
            subTextPaint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(key.subLabel, popupRect.right - dp(5), popupRect.top + dp(11.5f), subTextPaint)
        }
    }

    private fun cancelAllLongPresses() {
        for (i in 0 until activePointers.size()) {
            val tracker = activePointers.valueAt(i)
            tracker.longPressRunnable?.let { removeCallbacks(it) }
            tracker.longPressRunnable = null
        }
    }

    private fun commitKey(key: LatinKey) {
        val code = when {
            key.isSpace -> "space"
            key.isAction -> key.code
            key.code.length == 1 && key.code[0] in 'a'..'z' -> {
                if (isShifted || isCapsLock) key.upperLabel else key.lowerLabel
            }
            else -> key.code
        }
        listener?.onKey(code)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // High-speed Rollover: If any previous pointer is active and uncommitted, commit immediately!
                for (i in 0 until activePointers.size()) {
                    val prev = activePointers.valueAt(i)
                    if (!prev.committed && !prev.isLongPressed && !prev.dragged) {
                        commitKey(prev.key)
                        prev.committed = true
                    }
                }
                cancelAllLongPresses()

                val key = findKey(x, y)
                if (key != null) {
                    key.isPressed = true
                    val tracker = PointerTracker(pointerId, key, x, y)

                    if (key.code == "⌫") {
                        // Instant tactile action & delete 1 char on down; repeating backspace is managed by listener
                        listener?.onKeyActionDown("⌫")
                        tracker.committed = true
                    } else if (key.code == "⇧" || key.code == "?123" || key.code == "=\\<" || key.code == "ABC") {
                        commitKey(key)
                        tracker.committed = true
                    } else if (key.code == "☻") {
                        commitKey(key)
                        tracker.committed = true
                    } else {
                        // Instant tactile feedback on down (sound & haptic trigger 0ms)
                        listener?.onKeyActionDown(key.code)

                        // If key has a secondary superscript symbol, start long-press timer
                        if (showSecondarySymbols && !key.subLabel.isNullOrEmpty() && !key.isFunctional && !key.isSpace) {
                            tracker.longPressRunnable = Runnable {
                                tracker.isLongPressed = true
                                listener?.onSecondaryKey(key.subLabel)
                                invalidate()
                            }
                            postDelayed(tracker.longPressRunnable, 380)
                        }
                    }

                    activePointers.put(pointerId, tracker)
                    invalidate()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val tracker = activePointers.get(pId) ?: continue
                    val px = event.getX(i)
                    val py = event.getY(i)

                    if (tracker.key.isSpace) {
                        val dx = px - tracker.downX
                        if (Math.abs(dx) > dp(12)) {
                            listener?.onSpaceDrag(dx > 0)
                            tracker.downX = px
                            tracker.dragged = true
                        }
                    } else if (tracker.key.code == "⌫") {
                        val dx = px - tracker.downX
                        if (dx < -dp(20) && !tracker.dragged) {
                            tracker.dragged = true
                            tracker.longPressRunnable?.let { removeCallbacks(it) }
                            listener?.onBackspaceSwipe()
                        }
                    } else {
                        val dist = Math.hypot((px - tracker.downX).toDouble(), (py - tracker.downY).toDouble())
                        if (dist > dp(14)) {
                            tracker.longPressRunnable?.let { removeCallbacks(it) }
                            tracker.longPressRunnable = null
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val tracker = activePointers.get(pointerId)
                if (tracker != null) {
                    tracker.longPressRunnable?.let { removeCallbacks(it) }
                    tracker.longPressRunnable = null
                    tracker.key.isPressed = false

                    if (tracker.key.code == "⌫") {
                        listener?.onKeyActionUp("⌫")
                    } else if (!tracker.committed && !tracker.dragged && !tracker.isLongPressed) {
                        commitKey(tracker.key)
                        tracker.committed = true
                    }

                    activePointers.remove(pointerId)
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until activePointers.size()) {
                    val tracker = activePointers.valueAt(i)
                    tracker.longPressRunnable?.let { removeCallbacks(it) }
                    tracker.key.isPressed = false
                }
                activePointers.clear()
                invalidate()
            }
        }
        return true
    }

    private fun findKey(x: Float, y: Float): LatinKey? {
        if (allKeys.isEmpty()) return null

        // 1. Direct hit check
        for (k in allKeys) {
            if (k.bounds.contains(x, y)) return k
        }

        // 2. Fast row finding with padding boundaries
        var targetRow: List<LatinKey>? = null
        for (row in rows) {
            if (row.isEmpty()) continue
            val top = row[0].bounds.top - dp(keySpacingYDp)
            val bottom = row[0].bounds.bottom + dp(keySpacingYDp)
            if (y in top..bottom) {
                targetRow = row
                break
            }
        }

        // 3. If finger touched slightly outside row bounds, select nearest row
        val rowToSearch = targetRow ?: rows.minByOrNull { row ->
            if (row.isEmpty()) Float.MAX_VALUE
            else {
                val cy = row[0].bounds.centerY()
                Math.abs(y - cy)
            }
        } ?: return allKeys.firstOrNull()

        // 4. In selected row, find closest key horizontally
        return rowToSearch.minByOrNull { k ->
            Math.abs(x - k.bounds.centerX())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}
