package com.crazestudio.crazekeyboard

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Separate input window so the active IME can type into emoji search. */
class EmojiSearchActivity : Activity() {
    companion object {
        const val EXTRA_QUERY = "emoji_search_query"
        @Volatile var callback: ((String) -> Unit)? = null
    }

    private lateinit var query: EditText
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = Color.rgb(247, 248, 252)
        window.navigationBarColor = Color.rgb(247, 248, 252)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(18))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }
        root.addView(TextView(this).apply {
            text = "Search emoji"
            textSize = 20f
            setTextColor(Color.rgb(31, 37, 51))
            setPadding(0, 0, 0, dp(14))
        })
        query = EditText(this).apply {
            hint = "Try: smile, heart, fire…"
            textSize = 16f
            setSingleLine(true)
            setTextColor(Color.rgb(31, 37, 51))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(intent.getStringExtra(EXTRA_QUERY).orEmpty())
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
            }
        }
        root.addView(query, LinearLayout.LayoutParams(-1, dp(52)))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(14), 0, 0)
        }
        actions.addView(action("Cancel", false) { finish() })
        actions.addView(action("Search", true) { submit() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        root.addView(actions)
        setContentView(root)
        query.requestFocus()
        query.post {
            query.setSelection(query.text.length)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(query, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun action(label: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(if (primary) Color.WHITE else Color.rgb(35, 40, 55))
        setBackgroundColor(if (primary) Color.rgb(91, 75, 210) else Color.rgb(235, 237, 243))
        setPadding(dp(18), dp(11), dp(18), dp(11))
        setOnClickListener { onClick() }
    }

    private fun submit() {
        callback?.invoke(query.text.toString().trim())
        callback = null
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(query.windowToken, 0)
        finish()
    }
}