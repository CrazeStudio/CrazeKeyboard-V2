package com.crazestudio.crazekeyboard

import android.graphics.RectF

data class LatinKey(
    val code: String,
    var label: String = code,
    val subLabel: String? = null,
    var iconRes: Int = 0,
    val bounds: RectF = RectF(),
    var isPressed: Boolean = false,
    val isFunctional: Boolean = false,
    val isAction: Boolean = false,
    val isSpace: Boolean = false,
    val weight: Float = 1.0f
) {
    val upperLabel: String = if (code.length == 1 && code[0] in 'a'..'z') code.uppercase() else label
    val lowerLabel: String = if (code.length == 1 && code[0] in 'a'..'z') code.lowercase() else label
}
