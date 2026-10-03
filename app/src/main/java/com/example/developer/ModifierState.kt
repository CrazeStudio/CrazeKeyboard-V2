package com.example.developer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ModifierState {
    var isCtrlActive by mutableStateOf(false)
    var isAltActive by mutableStateOf(false)
    var isShiftActive by mutableStateOf(false)
    var isFnActive by mutableStateOf(false)

    fun toggleCtrl() {
        isCtrlActive = !isCtrlActive
    }

    fun toggleAlt() {
        isAltActive = !isAltActive
    }

    fun toggleShift() {
        isShiftActive = !isShiftActive
    }

    fun toggleFn() {
        isFnActive = !isFnActive
    }

    fun resetTransientModifiers() {
        // If latching is single-shot, reset after a key action
        isCtrlActive = false
        isAltActive = false
    }

    fun clearAll() {
        isCtrlActive = false
        isAltActive = false
        isShiftActive = false
        isFnActive = false
    }
}
