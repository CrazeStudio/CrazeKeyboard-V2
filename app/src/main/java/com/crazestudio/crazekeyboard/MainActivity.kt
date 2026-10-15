package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(16, 17, 22))
        }
        val title = TextView(this).apply {
            text = "CrazeKeyboard"
            textSize = 30f
            setTextColor(Color.rgb(245, 245, 250))
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "A lightweight keyboard, designed around local typing."
            textSize = 15f
            setTextColor(Color.rgb(185, 185, 200))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 28)
        }
        val enable = Button(this).apply {
            text = "1 · Enable CrazeKeyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }
        val choose = Button(this).apply {
            text = "2 · Choose keyboard"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(enable, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        root.addView(choose, LinearLayout.LayoutParams(-1, -2))
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
    }
}
