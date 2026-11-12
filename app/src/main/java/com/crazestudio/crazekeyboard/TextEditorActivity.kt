package com.crazestudio.crazekeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView

/** Full-screen editor companion. Results return through a mailbox after the original editor regains focus. */
class TextEditorActivity : Activity() {
    companion object {
        const val EXTRA_INITIAL_TEXT = "initial_text"
        const val EXTRA_REPLACE_SELECTION = "replace_selection"

        @Volatile var callback: (() -> Unit)? = null
        @Volatile private var pendingText: String? = null

        @Synchronized fun post(text: String) { pendingText = text }
        @Synchronized fun take(): String? {
            val value = pendingText
            pendingText = null
            return value
        }
    }

    private val ink = Color.rgb(31, 37, 51)
    private val muted = Color.rgb(110, 118, 137)
    private val accent = Color.rgb(102, 83, 215)
    private val page = Color.rgb(244, 246, 251)
    private lateinit var editor: EditText
    private lateinit var counter: TextView
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSnapshot = ""
    private var restoring = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 16, stroke: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun action(text: String, primary: Boolean = false, click: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (primary) Color.WHITE else ink)
            background = shape(if (primary) accent else Color.WHITE, 12,
                if (primary) 0 else Color.rgb(229, 232, 240))
            setPadding(dp(13), dp(10), dp(13), dp(10))
            minHeight = dp(42)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    /** Compact horizontally-scrollable action row so the editor keeps usable height above the IME. */
    private fun toolStrip(items: List<Pair<String, () -> Unit>>): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        items.forEachIndexed { index, item ->
            val button = action(item.first, click = item.second).apply {
                textSize = 12f
                minHeight = dp(36)
                setPadding(dp(10), dp(5), dp(10), dp(5))
            }
            row.addView(button, LinearLayout.LayoutParams(-2, dp(38)).apply {
                if (index > 0) leftMargin = dp(4)
                if (index < items.lastIndex) rightMargin = dp(2)
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row, HorizontalScrollView.LayoutParams(-2, dp(40)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(action("Close") { finish() })
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        titleBlock.addView(label("Text editor", 20f, ink, true))
        titleBlock.addView(label("Compose, refine and insert", 11f, muted))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("CRAZE", 11f, accent, true))
        root.addView(header)

        counter = label("0 words  ·  0 characters", 11f, muted).apply {
            setPadding(dp(2), dp(14), 0, dp(8))
        }
        root.addView(counter)

        editor = EditText(this).apply {
            hint = "Write here, or select text in the previous app before opening the editor…"
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(Color.rgb(154, 160, 174))
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = shape(Color.WHITE, 18, Color.rgb(229, 232, 240))
            minLines = 3
            setText(intent.getStringExtra(EXTRA_INITIAL_TEXT).orEmpty())
        }
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(4)
            bottomMargin = dp(12)
        })
        lastSnapshot = editor.text.toString()
        updateCounter()
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString().orEmpty()
                if (!restoring && value != lastSnapshot) {
                    undoStack.addLast(lastSnapshot)
                    while (undoStack.size > 60) undoStack.removeFirst()
                    redoStack.clear()
                    lastSnapshot = value
                }
                updateCounter()
            }
        })

        // Editing actions share two compact horizontal strips instead of four tall rows.
        // This leaves substantially more room for text while the keyboard is visible.
        root.addView(toolStrip(listOf(
            "Undo" to { undo() },
            "Redo" to { redo() },
            "←" to { moveCursor(-1) },
            "→" to { moveCursor(1) },
            "Word" to { selectWord() },
            "Line" to { selectLine() },
            "Start" to { moveToLineBoundary(false) },
            "End" to { moveToLineBoundary(true) }
        )), LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(4)
        })
        root.addView(toolStrip(listOf(
            "Select all" to { editor.selectAll() },
            "Copy" to { copyText() },
            "Cut" to { cutText() },
            "Paste" to { pasteText() },
            "Find / replace" to { findReplace() },
            "Clear" to { editor.text?.clear() }
        )), LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(2)
        })

        root.addView(action(
            if (intent.getBooleanExtra(EXTRA_REPLACE_SELECTION, false)) "Replace selected text" else "Insert into current field",
            true
        ) { applyAndClose() }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
            bottomMargin = dp(4)
        })

        setContentView(root)
        editor.requestFocus()
        editor.post {
            editor.setSelection(editor.text.length)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun moveCursor(delta: Int) {
        val length = editor.text?.length ?: 0
        val start = editor.selectionStart.coerceIn(0, length)
        val end = editor.selectionEnd.coerceIn(0, length)
        val target = (if (start != end) maxOf(start, end) else start + delta).coerceIn(0, length)
        editor.setSelection(target)
        editor.requestFocus()
    }

    private fun selectWord() {
        val value = editor.text?.toString().orEmpty()
        if (value.isEmpty()) return
        val cursor = editor.selectionStart.coerceIn(0, value.length)
        var start = cursor
        var end = cursor
        if (start == value.length && start > 0) start--
        while (start > 0 && (value[start - 1].isLetterOrDigit() || value[start - 1] == '\'')) start--
        while (end < value.length && (value[end].isLetterOrDigit() || value[end] == '\'')) end++
        if (start == end) return
        editor.setSelection(start, end)
        editor.requestFocus()
    }

    private fun selectLine() {
        val value = editor.text?.toString().orEmpty()
        val cursor = editor.selectionStart.coerceIn(0, value.length)
        val start = value.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val nextBreak = value.indexOf('\n', cursor)
        val end = if (nextBreak < 0) value.length else nextBreak
        editor.setSelection(start, end)
        editor.requestFocus()
    }

    private fun moveToLineBoundary(endOfLine: Boolean) {
        val value = editor.text?.toString().orEmpty()
        val cursor = editor.selectionStart.coerceIn(0, value.length)
        val start = value.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val nextBreak = value.indexOf('\n', cursor)
        val end = if (nextBreak < 0) value.length else nextBreak
        editor.setSelection(if (endOfLine) end else start)
        editor.requestFocus()
    }

    private fun updateCounter() {
        if (!::counter.isInitialized || !::editor.isInitialized) return
        val value = editor.text?.toString().orEmpty()
        val words = value.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        counter.text = "$words words  ·  ${value.length} characters"
    }

    private fun restoreSnapshot(value: String) {
        restoring = true
        editor.setText(value)
        editor.setSelection(editor.text.length)
        lastSnapshot = value
        restoring = false
        updateCounter()
    }

    private fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(editor.text.toString())
        restoreSnapshot(undoStack.removeLast())
    }

    private fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(editor.text.toString())
        restoreSnapshot(redoStack.removeLast())
    }

    private fun copyText() {
        val value = editor.text.toString()
        if (value.isEmpty()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CrazeKeyboard text", value))
    }

    private fun cutText() {
        copyText()
        editor.text?.clear()
    }

    private fun pasteText() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val value = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(0)
        editor.text?.replace(minOf(start, end), maxOf(start, end), value)
    }

    private fun findReplace() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val find = EditText(this).apply { hint = "Find text"; isSingleLine = true }
        val replace = EditText(this).apply { hint = "Replace with"; isSingleLine = true }
        box.addView(find)
        box.addView(replace)
        AlertDialog.Builder(this)
            .setTitle("Find and replace")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Replace all") { _, _ ->
                val needle = find.text.toString()
                if (needle.isNotEmpty()) {
                    val updated = editor.text.toString().replace(needle, replace.text.toString())
                    editor.setText(updated)
                    editor.setSelection(editor.text.length)
                }
            }
            .show()
    }

    private fun applyAndClose() {
        post(editor.text.toString())
        finish()
        Handler(Looper.getMainLooper()).postDelayed({ callback?.invoke() }, 350)
    }
}
