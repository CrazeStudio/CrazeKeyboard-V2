package com.example.keyboard

enum class KeyType {
    CHARACTER,
    SHIFT,
    BACKSPACE,
    ENTER,
    MODE_CHANGE,
    SPACE,
    EMOJI,
    ACTION,
    SPACER
}

data class KeyModel(
    val primaryText: String = "",
    val shiftedText: String = primaryText.uppercase(),
    val alternateText: String = "",
    val type: KeyType = KeyType.CHARACTER,
    val weight: Float = 1.0f,
    val iconResId: Int? = null,
    val longPressOptions: List<String> = emptyList()
)
