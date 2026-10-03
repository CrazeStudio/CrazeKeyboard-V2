package com.example.themes

import androidx.compose.ui.graphics.Color

data class KeyboardTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val keyColor: Color,
    val specialKeyColor: Color,
    val accentColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val borderColor: Color,
    val hasBorders: Boolean = true,
    val elevationDp: Float = 1f,
    val keyCornerRadiusDp: Float = 8f,
    val isMonospace: Boolean = false,
    val popupColor: Color = keyColor,
    val popupTextColor: Color = textColor,
    val toolbarColor: Color = backgroundColor,
    val devToolbarColor: Color = specialKeyColor
)
