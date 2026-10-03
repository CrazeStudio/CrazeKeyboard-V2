package com.example.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

data class KeyPreviewState(
    val text: String,
    val position: Offset,
    val size: IntSize,
    val alternateOptions: List<String> = emptyList(),
    val selectedOptionIndex: Int = 0
)
