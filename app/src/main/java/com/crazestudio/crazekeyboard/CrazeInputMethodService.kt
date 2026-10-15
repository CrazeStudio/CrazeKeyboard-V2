package com.crazestudio.crazekeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

class CrazeInputMethodService : InputMethodService() {
    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 8)
            setBackgroundColor(Color.rgb(24, 25, 33))
        }
        val rows = listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l"),
            listOf("⇧","z","x","c","v","b","n","m","⌫"),
            listOf("?123",",","☻","English"," . ","↵")
        )
        rows.forEachIndexed { rowIndex, keys ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            keys.forEach { label ->
                val button = Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = if (rowIndex == 3) 13f else 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(247, 247, 252))
                    background = GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(if (label in listOf("⇧","⌫","?123","↵")) Color.rgb(67, 68, 83) else Color.rgb(48, 49, 62))
                    }
                    setPadding(0, 0, 0, 0)
                    setOnClickListener { handleKey(label) }
                }
                val weight = if (label == "English") 2.4f else if (label == "☻" || label == "?123") 1.4f else 1f
                row.addView(button, LinearLayout.LayoutParams(0, if (rowIndex == 3) dp(48) else dp(48), weight).apply {
                    setMargins(2, 3, 2, 3)
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return root
    }

    private fun handleKey(label: String) {
        val connection = currentInputConnection ?: return
        when (label) {
            "⌫" -> connection.deleteSurroundingText(1, 0)
            "↵" -> connection.commitText("\n", 1)
            "⇧", "?123", "☻", "English" -> { /* UI placeholders; mode switching is implemented later. */ }
            " . " -> connection.commitText(".", 1)
            "," -> connection.commitText(",", 1)
            else -> connection.commitText(label, 1)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
