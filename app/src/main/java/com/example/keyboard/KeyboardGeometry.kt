package com.example.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/**
 * High-performance, allocation-minimal keyboard geometry and hitbox engine.
 * Calculates exact key bounds, visual rects, and touch hitboxes with zero dead zones.
 */
data class KeyPosition(
    val key: KeyModel,
    val rowIndex: Int,
    val keyIndex: Int,
    val cellRect: Rect,       // Full touch hitbox covering the entire cell area (zero gaps)
    val visualRect: Rect      // Inset visual bounds with padding for drawing
)

class KeyboardGeometry(
    val size: IntSize,
    val rows: List<List<KeyModel>>,
    val hasNumberRow: Boolean = false,
    val numberRowHeightRatio: Float = 0.85f,
    val keySpacingPx: Float = 6f
) {
    val keyPositions = ArrayList<KeyPosition>(45)
    val rowHeights = FloatArray(rows.size)
    val rowTopPositions = FloatArray(rows.size)

    init {
        computeLayout()
    }

    private fun computeLayout() {
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        if (width <= 0f || height <= 0f || rows.isEmpty()) return

        val totalEffectiveRows = if (hasNumberRow) {
            ((rows.size - 1).coerceAtLeast(1) + numberRowHeightRatio)
        } else {
            rows.size.toFloat().coerceAtLeast(1f)
        }
        val standardRowHeight = height / totalEffectiveRows
        val numberRowHeight = standardRowHeight * numberRowHeightRatio

        var currentY = 0f
        var currentRowIdx = 0

        // 1. Compute rows (number row if present is at index 0)
        for (r in rows.indices) {
            val isNumRow = hasNumberRow && r == 0
            val rowH = if (isNumRow) numberRowHeight else standardRowHeight
            rowHeights[currentRowIdx] = rowH
            rowTopPositions[currentRowIdx] = currentY

            val rowKeys = rows[r]
            val totalWeight = rowKeys.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1.0f)

            var currentX = 0f
            for (k in rowKeys.indices) {
                val key = rowKeys[k]
                val keyW = (key.weight / totalWeight) * width

                // Touch cell covers full area up to adjacent key boundaries
                val cellRect = Rect(
                    left = currentX,
                    top = currentY,
                    right = currentX + keyW,
                    bottom = currentY + rowH
                )

                // Visual rect applies spacing inset
                val halfSpacing = keySpacingPx / 2f
                val visualRect = Rect(
                    left = currentX + halfSpacing,
                    top = currentY + halfSpacing,
                    right = currentX + keyW - halfSpacing,
                    bottom = currentY + rowH - halfSpacing
                )

                if (key.type != KeyType.SPACER) {
                    keyPositions.add(
                        KeyPosition(
                            key = key,
                            rowIndex = r,
                            keyIndex = k,
                            cellRect = cellRect,
                            visualRect = visualRect
                        )
                    )
                }

                currentX += keyW
            }

            currentY += rowH
            currentRowIdx++
        }
    }

    /**
     * Finds the key corresponding to a touch offset.
     * Uses cellRect bounding boxes first; if slightly outside, finds nearest key within touch slop.
     */
    fun findKeyAtOffset(offset: Offset, touchSlopPx: Float = 40f): KeyPosition? {
        if (keyPositions.isEmpty()) return null

        // 1. Direct hit test within cell bounds
        for (pos in keyPositions) {
            if (pos.cellRect.contains(offset)) {
                return pos
            }
        }

        // 2. Nearest neighbor fallback within touch slop (e.g. finger landed on screen edge or gap)
        var closestKey: KeyPosition? = null
        var closestDistanceSq = touchSlopPx * touchSlopPx

        for (pos in keyPositions) {
            val cx = (pos.cellRect.left + pos.cellRect.right) / 2f
            val cy = (pos.cellRect.top + pos.cellRect.bottom) / 2f
            val dx = offset.x - cx
            val dy = offset.y - cy
            val distSq = dx * dx + dy * dy
            if (distSq < closestDistanceSq) {
                closestDistanceSq = distSq
                closestKey = pos
            }
        }

        return closestKey
    }

    /**
     * Quick character key lookup for glide/swipe typing.
     */
    fun findCharKeyAtOffset(offset: Offset): Char? {
        val pos = findKeyAtOffset(offset) ?: return null
        if (pos.key.type == KeyType.CHARACTER && pos.key.primaryText.isNotEmpty()) {
            return pos.key.primaryText.first().lowercaseChar()
        }
        return null
    }
}
