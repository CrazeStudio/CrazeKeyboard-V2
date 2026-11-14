package com.crazestudio.crazekeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher

/** EditText that reports selection changes so the counter can show the selected length. */
private class SelectionAwareEditText(context: Context) : EditText(context) {
    // Nullable on purpose: TextView's constructor can call onSelectionChanged before this field is initialised.
    var onSelection: (() -> Unit)? = null
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelection?.invoke()
    }
}

/**
 * Full-screen editor companion. Results return through a mailbox after the original editor regains focus.
 *
 * - Back / "<" never silently throws text away: unchanged text closes, edited text asks Insert / Discard / Keep editing.
 * - Draft survives rotation, theme change and process death (cleared as soon as the editor is closed).
 * - Undo/redo coalesces typing bursts, so one undo step is a burst, not one character.
 * - Find & replace bar with match highlighting, next/previous, match case, replace one and replace all.
 * - Light and dark mode, adjustable text size, share, case change and bullet list tools.
 */
class TextEditorActivity : Activity() {
    companion object {
        const val EXTRA_INITIAL_TEXT = "initial_text"
        const val EXTRA_REPLACE_SELECTION = "replace_selection"
        private const val PREFS = "craze_text_editor"
        private const val KEY_DRAFT = "draft"
        private const val KEY_SIZE = "size_index"
        private const val STATE_SEL_START = "sel_start"
        private const val STATE_SEL_END = "sel_end"
        private const val MAILBOX_TTL_MS = 60_000L
        private const val UNDO_LIMIT = 100
        private const val BURST_MS = 900L
        private const val MAX_MATCHES = 5000
        private const val MAX_HIGHLIGHTS = 300
        private const val MAX_SHARE_CHARS = 200_000
        private val TEXT_SIZES = floatArrayOf(14f, 16f, 18f, 20f, 24f, 28f)

        @Volatile var callback: (() -> Unit)? = null
        @Volatile private var pendingText: String? = null
        @Volatile private var postedAt = 0L

        @Synchronized fun post(text: String) {
            pendingText = text
            postedAt = SystemClock.elapsedRealtime()
        }

        /** Returns the pending text once. Text older than a minute is dropped so it can never land in the wrong app later. */
        @Synchronized fun take(): String? {
            val value = pendingText
            pendingText = null
            if (value == null) return null
            return if (SystemClock.elapsedRealtime() - postedAt <= MAILBOX_TTL_MS) value else null
        }
    }

    // ---- palette (light + dark) ----
    private var night = false
    private var ink = 0
    private var muted = 0
    private var accent = 0
    private var page = 0
    private var chipBg = 0
    private var stroke = 0
    private var barBg = 0
    private var highlight = 0
    private var currentHighlight = 0

    private lateinit var editor: SelectionAwareEditText
    private lateinit var counter: TextView
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var replaceInput: EditText
    private lateinit var findLabel: TextView
    private lateinit var caseChip: TextView
    private var undoChip: TextView? = null
    private var redoChip: TextView? = null

    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var initialText = ""
    private var sizeIndex = 2
    private var finishedByUser = false

    // ---- undo / redo ----
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var current = ""
    private var lastEditAt = 0L
    private var restoring = false

    // ---- find ----
    private var matches: List<Int> = emptyList()
    private var matchIndex = -1
    private var matchCase = false
    private val spans = ArrayList<BackgroundColorSpan>()
    private val findRefresh = Runnable { if (::findBar.isInitialized && findBar.visibility == View.VISIBLE) refreshMatches(false) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun initPalette() {
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ink = if (night) Color.rgb(236, 238, 244) else Color.rgb(31, 37, 51)
        muted = if (night) Color.rgb(150, 157, 176) else Color.rgb(110, 118, 137)
        accent = if (night) Color.rgb(112, 96, 235) else Color.rgb(102, 83, 215)
        page = if (night) Color.rgb(18, 20, 27) else Color.WHITE
        chipBg = if (night) Color.rgb(36, 40, 52) else Color.WHITE
        stroke = if (night) Color.rgb(58, 64, 82) else Color.rgb(229, 232, 240)
        barBg = if (night) Color.rgb(30, 33, 44) else Color.rgb(245, 246, 248)
        highlight = if (night) Color.argb(110, 142, 127, 255) else Color.argb(90, 255, 213, 79)
        currentHighlight = if (night) Color.argb(200, 142, 127, 255) else Color.argb(200, 255, 152, 0)
    }

    private fun shape(color: Int, radius: Int = 16, strokeColor: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun styleChip(view: TextView, selected: Boolean, primary: Boolean = false) {
        val active = primary || selected
        view.setTextColor(if (active) Color.WHITE else ink)
        view.background = shape(if (active) accent else chipBg, 12, if (active) 0 else stroke)
    }

    private fun chip(text: String, primary: Boolean = false, small: Boolean = false, description: String? = null, click: () -> Unit) =
        TextView(this).apply {
            this.text = text
            textSize = if (small) 12f else 13f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            styleChip(this, false, primary)
            setPadding(dp(if (small) 10 else 13), dp(if (small) 5 else 10), dp(if (small) 10 else 13), dp(if (small) 5 else 10))
            minHeight = dp(if (small) 36 else 42)
            minWidth = dp(if (small) 36 else 42)
            isClickable = true
            isFocusable = true
            contentDescription = description ?: text
            setOnClickListener { click() }
        }

    private fun watcher(block: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block()
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPalette()
        window.statusBarColor = page
        window.navigationBarColor = page
        window.decorView.systemUiVisibility =
            if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        initialText = intent.getStringExtra(EXTRA_INITIAL_TEXT).orEmpty()
        val restored = savedInstanceState != null
        val startText = if (restored) prefs.getString(KEY_DRAFT, null) ?: initialText else initialText
        if (!restored) prefs.edit().remove(KEY_DRAFT).apply()
        sizeIndex = prefs.getInt(KEY_SIZE, 2).coerceIn(0, TEXT_SIZES.lastIndex)

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(page)
            fitsSystemWindows = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(8))
        }
        shell.addView(root, LinearLayout.LayoutParams(-1, -1))

        // ---- header ----
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(ink)
            contentDescription = "Back"
            isClickable = true
            setOnClickListener { handleBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        header.addView(label("Text Editor", 20f, ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        header.addView(chip("Insert", primary = true, small = true, description = "Insert text into the current field") { applyAndClose() },
            LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(4) })
        header.addView(TextView(this).apply {
            text = "⋮"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(ink)
            contentDescription = "More options"
            isClickable = true
            setOnClickListener { showEditorMenu(this) }
        }, LinearLayout.LayoutParams(dp(44), dp(54)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

        findBar = buildFindBar()
        root.addView(findBar, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })

        // ---- editor ----
        editor = SelectionAwareEditText(this).apply {
            hint = "Start typing"
            textSize = TEXT_SIZES[sizeIndex]
            setTextColor(ink)
            setHintTextColor(muted)
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
            setPadding(dp(22), dp(22), dp(18), dp(18))
            background = null
            minLines = 4
            setText(startText)
        }
        root.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        current = editor.text.toString()
        editor.onSelection = { updateCounter() }
        editor.addTextChangedListener(watcher { onEditorChanged() })

        // ---- tool strip ----
        val items = ArrayList<View>()
        undoChip = chip("↶ Undo", small = true, description = "Undo") { undo() }.also { items.add(it) }
        redoChip = chip("↷ Redo", small = true, description = "Redo") { redo() }.also { items.add(it) }
        items.add(chip("Aa", small = true, description = "Change case") { cycleCase() })
        items.add(chip("• List", small = true, description = "Toggle bullet list") { toggleBullets() })
        items.add(chip("Find", small = true, description = "Find and replace") { openFind() })
        items.add(chip("Select all", small = true) { editor.requestFocus(); editor.selectAll() })
        items.add(chip("Copy", small = true) { copyText() })
        items.add(chip("Cut", small = true) { cutText() })
        items.add(chip("Paste", small = true) { pasteText() })
        items.add(chip("Share", small = true) { shareText() })
        items.add(chip("Clear", small = true) { clearText() })
        items.add(chip("⌨", small = true, description = "Show or hide keyboard") { toggleKeyboard() })
        root.addView(toolStrip(items), LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })

        counter = label("0 words  ·  0 characters", 11f, muted).apply {
            setPadding(dp(10), dp(7), dp(10), dp(2))
        }
        root.addView(counter, LinearLayout.LayoutParams(-1, -2))

        setContentView(shell)
        updateCounter()
        updateUndoState()

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { handleBack() }
        }

        editor.requestFocus()
        val selStart = savedInstanceState?.getInt(STATE_SEL_START, -1) ?: -1
        val selEnd = savedInstanceState?.getInt(STATE_SEL_END, -1) ?: -1
        editor.post {
            val length = editor.text.length
            if (selStart in 0..length && selEnd in 0..length) editor.setSelection(selStart, selEnd)
            else editor.setSelection(length)
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun toolStrip(items: List<View>): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        items.forEachIndexed { index, item ->
            row.addView(item, LinearLayout.LayoutParams(-2, dp(38)).apply {
                if (index > 0) leftMargin = dp(6)
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row, LinearLayout.LayoutParams(-2, dp(40)))
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::editor.isInitialized) return
        outState.putInt(STATE_SEL_START, editor.selectionStart)
        outState.putInt(STATE_SEL_END, editor.selectionEnd)
        saveDraft()
    }

    override fun onPause() {
        if (!isFinishing) saveDraft()
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        if (::editor.isInitialized) {
            editor.onSelection = null
            clearHighlights()
        }
        super.onDestroy()
    }

    @Deprecated("Kept for devices where the back-invoked callback is not used")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun saveDraft() {
        if (finishedByUser || !::editor.isInitialized) return
        val value = editor.text.toString()
        if (value == initialText) prefs.edit().remove(KEY_DRAFT).apply()
        else prefs.edit().putString(KEY_DRAFT, value).apply()
    }

    private fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    // ------------------------------------------------------------------ closing

    private fun handleBack() {
        if (::findBar.isInitialized && findBar.visibility == View.VISIBLE) {
            closeFind()
            return
        }
        val value = editor.text.toString()
        if (value == initialText) {
            discardAndFinish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Keep your changes?")
            .setMessage("You have edited this text.")
            .setPositiveButton("Insert") { _, _ -> applyAndClose() }
            .setNegativeButton("Discard") { _, _ -> discardAndFinish() }
            .setNeutralButton("Keep editing", null)
            .show()
    }

    private fun discardAndFinish() {
        finishedByUser = true
        clearDraft()
        finish()
    }

    private fun applyAndClose() {
        val value = editor.text.toString()
        finishedByUser = true
        clearDraft()
        if (value.isEmpty() && initialText.isEmpty()) {
            finish()
            return
        }
        post(value)
        finish()
        Handler(Looper.getMainLooper()).postDelayed({ callback?.invoke() }, 350)
    }

    // ------------------------------------------------------------------ menu

    private fun showEditorMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add("Select all").setOnMenuItemClickListener { editor.requestFocus(); editor.selectAll(); true }
        menu.menu.add("Find and replace").setOnMenuItemClickListener { openFind(); true }
        menu.menu.add("Copy all").setOnMenuItemClickListener { copyAll(); true }
        menu.menu.add("Paste").setOnMenuItemClickListener { pasteText(); true }
        menu.menu.add("Share").setOnMenuItemClickListener { shareText(); true }
        menu.menu.add("Increase text size").setOnMenuItemClickListener { changeTextSize(1); true }
        menu.menu.add("Decrease text size").setOnMenuItemClickListener { changeTextSize(-1); true }
        menu.menu.add("Clear").setOnMenuItemClickListener { clearText(); true }
        menu.menu.add("Insert into current field").setOnMenuItemClickListener { applyAndClose(); true }
        menu.show()
    }

    private fun changeTextSize(delta: Int) {
        val next = (sizeIndex + delta).coerceIn(0, TEXT_SIZES.lastIndex)
        if (next == sizeIndex) return
        sizeIndex = next
        editor.textSize = TEXT_SIZES[next]
        prefs.edit().putInt(KEY_SIZE, next).apply()
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editor.requestFocus()
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    // ------------------------------------------------------------------ editing tools

    private fun selStart() = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
    private fun selEnd() = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)

    private fun titleCase(value: String): String {
        val out = StringBuilder(value.length)
        var capitalizeNext = true
        for (ch in value) {
            if (ch.isLetter()) {
                out.append(if (capitalizeNext) ch.uppercaseChar() else ch.lowercaseChar())
                capitalizeNext = false
            } else {
                out.append(ch)
                capitalizeNext = !(ch == '\'' || ch == '’' || ch.isDigit())
            }
        }
        return out.toString()
    }

    /** UPPERCASE -> lowercase -> Title Case on the selection, or on the whole text when nothing is selected. */
    private fun cycleCase() {
        val text = editor.text
        var start = selStart()
        var end = selEnd()
        val whole = start == end
        if (whole) { start = 0; end = text.length }
        if (start == end) return
        val segment = text.substring(start, end)
        val next = when {
            segment == segment.uppercase() && segment != segment.lowercase() -> segment.lowercase()
            segment == segment.lowercase() -> titleCase(segment)
            else -> segment.uppercase()
        }
        if (next == segment) return
        val cursor = selEnd().coerceAtMost(text.length)
        text.replace(start, end, next)
        if (whole) editor.setSelection(cursor.coerceAtMost(editor.text.length))
        else editor.setSelection(start, start + next.length)
        editor.requestFocus()
    }

    /** Adds or removes "• " on every line touched by the selection. */
    private fun toggleBullets() {
        val text = editor.text
        val value = text.toString()
        val start = selStart().coerceAtMost(value.length)
        val end = selEnd().coerceAtMost(value.length)
        val lineStart = value.lastIndexOf('\n', start - 1) + 1
        val lineEnd = value.indexOf('\n', end).let { if (it < 0) value.length else it }
        val block = value.substring(lineStart, lineEnd)
        val lines = block.split("\n")
        val filled = lines.filter { it.isNotBlank() }
        val allBulleted = filled.isNotEmpty() && filled.all { it.startsWith("• ") }
        val updated = if (!allBulleted && block.isBlank()) "• " else lines.joinToString("\n") { line ->
            when {
                allBulleted -> line.removePrefix("• ")
                line.isBlank() || line.startsWith("• ") -> line
                else -> "• $line"
            }
        }
        text.replace(lineStart, lineEnd, updated)
        if (lines.size == 1) editor.setSelection(lineStart + updated.length)
        else editor.setSelection(lineStart, lineStart + updated.length)
        editor.requestFocus()
    }

    private fun clipboard() = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

    private fun copyAll() {
        val value = editor.text.toString()
        if (value.isEmpty()) return
        clipboard().setPrimaryClip(ClipData.newPlainText("CrazeKeyboard text", value))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyText() {
        val start = selStart()
        val end = selEnd()
        val value = if (start != end) editor.text.substring(start, end) else editor.text.toString()
        if (value.isEmpty()) return
        clipboard().setPrimaryClip(ClipData.newPlainText("CrazeKeyboard text", value))
        Toast.makeText(this, if (start != end) "Copied selection" else "Copied all text", Toast.LENGTH_SHORT).show()
    }

    private fun cutText() {
        val start = selStart()
        val end = selEnd()
        if (start == end) {
            Toast.makeText(this, "Select text to cut", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard().setPrimaryClip(ClipData.newPlainText("CrazeKeyboard text", editor.text.substring(start, end)))
        editor.text.delete(start, end)
    }

    private fun pasteText() {
        val clip = clipboard().primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val value = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (value.isEmpty()) return
        editor.text.replace(selStart(), selEnd(), value)
        editor.requestFocus()
    }

    private fun shareText() {
        val value = editor.text.toString()
        if (value.isBlank()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
            return
        }
        if (value.length > MAX_SHARE_CHARS) {
            Toast.makeText(this, "Text is too large to share. Copy it instead.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value), "Share text"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app available to share with", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearText() {
        if (editor.text.isEmpty()) return
        editor.text.clear()
        Toast.makeText(this, "Cleared. Tap Undo to bring it back.", Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ change tracking, undo / redo

    private fun onEditorChanged() {
        val value = editor.text.toString()
        if (!restoring && value != current) {
            val now = SystemClock.uptimeMillis()
            val bigChange = kotlin.math.abs(value.length - current.length) > 1
            if (bigChange || undoStack.isEmpty() || now - lastEditAt > BURST_MS) {
                undoStack.addLast(current)
                while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
            }
            redoStack.clear()
            current = value
            lastEditAt = now
        }
        updateCounter()
        updateUndoState()
        if (::findBar.isInitialized && findBar.visibility == View.VISIBLE) {
            ui.removeCallbacks(findRefresh)
            ui.postDelayed(findRefresh, 250)
        }
    }

    private fun updateUndoState() {
        undoChip?.alpha = if (undoStack.isEmpty()) 0.4f else 1f
        redoChip?.alpha = if (redoStack.isEmpty()) 0.4f else 1f
    }

    /** Cursor lands at the end of the region that differs between the two texts. */
    private fun restoreSnapshot(value: String) {
        val old = editor.text.toString()
        var prefix = 0
        val shortest = minOf(old.length, value.length)
        while (prefix < shortest && old[prefix] == value[prefix]) prefix++
        var suffix = 0
        while (suffix < shortest - prefix && old[old.length - 1 - suffix] == value[value.length - 1 - suffix]) suffix++
        restoring = true
        editor.setText(value)
        editor.setSelection((value.length - suffix).coerceIn(0, value.length))
        restoring = false
        current = value
        lastEditAt = 0L
        updateCounter()
        updateUndoState()
        if (::findBar.isInitialized && findBar.visibility == View.VISIBLE) refreshMatches(false)
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

    // ------------------------------------------------------------------ counter

    private fun countWords(value: CharSequence): Int {
        var count = 0
        var inWord = false
        for (ch in value) {
            if (ch.isWhitespace()) inWord = false
            else if (!inWord) { inWord = true; count++ }
        }
        return count
    }

    private fun updateCounter() {
        if (!::counter.isInitialized || !::editor.isInitialized) return
        val value = editor.text
        val selected = selEnd() - selStart()
        val base = "${countWords(value)} words  ·  ${value.length} characters"
        counter.text = if (selected > 0) "$base  ·  $selected selected" else base
    }

    // ------------------------------------------------------------------ find and replace

    private fun buildFindBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(barBg, 16)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            visibility = View.GONE
        }
        findInput = EditText(this).apply {
            hint = "Find"
            textSize = 14f
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ -> findStep(1); true }
            addTextChangedListener(watcher { refreshMatches(true) })
        }
        findLabel = label("", 12f, muted).apply { setPadding(dp(6), 0, dp(6), 0) }
        val row1 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row1.addView(findInput, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(findLabel, LinearLayout.LayoutParams(-2, -2))
        row1.addView(chip("↑", small = true, description = "Previous match") { findStep(-1) }, LinearLayout.LayoutParams(-2, dp(36)))
        row1.addView(chip("↓", small = true, description = "Next match") { findStep(1) },
            LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(4) })
        row1.addView(chip("✕", small = true, description = "Close find") { closeFind() },
            LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(4) })
        bar.addView(row1, LinearLayout.LayoutParams(-1, -2))

        replaceInput = EditText(this).apply {
            hint = "Replace with"
            textSize = 14f
            isSingleLine = true
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        caseChip = chip("Aa", small = true, description = "Match case") {
            matchCase = !matchCase
            styleChip(caseChip, matchCase)
            refreshMatches(true)
        }
        val row2 = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row2.addView(replaceInput, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(caseChip, LinearLayout.LayoutParams(-2, dp(36)))
        row2.addView(chip("Replace", small = true) { replaceCurrent() },
            LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(4) })
        row2.addView(chip("All", small = true, description = "Replace all") { replaceAll() },
            LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(4) })
        bar.addView(row2, LinearLayout.LayoutParams(-1, -2))
        return bar
    }

    private fun openFind() {
        findBar.visibility = View.VISIBLE
        val start = selStart()
        val end = selEnd()
        if (end > start && end - start <= 80) {
            val picked = editor.text.substring(start, end)
            if (!picked.contains('\n')) findInput.setText(picked)
        }
        findInput.requestFocus()
        findInput.setSelection(findInput.text.length)
        refreshMatches(false)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeFind() {
        ui.removeCallbacks(findRefresh)
        clearHighlights()
        matches = emptyList()
        matchIndex = -1
        findBar.visibility = View.GONE
        editor.requestFocus()
    }

    private fun clearHighlights() {
        if (!::editor.isInitialized) return
        val text = editor.text
        spans.forEach { text.removeSpan(it) }
        spans.clear()
    }

    private fun updateFindLabel() {
        findLabel.text = when {
            findInput.text.isEmpty() -> ""
            matches.isEmpty() -> "No results"
            else -> "${matchIndex + 1}/${matches.size}${if (matches.size >= MAX_MATCHES) "+" else ""}"
        }
    }

    /** Recomputes matches and highlights. [select] also moves the editor selection to the active match. */
    private fun refreshMatches(select: Boolean, from: Int = -1) {
        val text = editor.text
        clearHighlights()
        val query = findInput.text.toString()
        if (query.isEmpty()) {
            matches = emptyList()
            matchIndex = -1
            updateFindLabel()
            return
        }
        val hay = text.toString()
        val found = ArrayList<Int>()
        var i = hay.indexOf(query, 0, ignoreCase = !matchCase)
        while (i >= 0 && found.size < MAX_MATCHES) {
            found.add(i)
            i = hay.indexOf(query, i + query.length, ignoreCase = !matchCase)
        }
        matches = found
        if (found.isEmpty()) {
            matchIndex = -1
        } else {
            val anchor = if (from >= 0) from else editor.selectionStart.coerceAtLeast(0)
            val idx = found.indexOfFirst { it >= anchor }
            matchIndex = if (idx < 0) 0 else idx
            for (n in found.indices) {
                if (n >= MAX_HIGHLIGHTS && n != matchIndex) continue
                val span = BackgroundColorSpan(if (n == matchIndex) currentHighlight else highlight)
                text.setSpan(span, found[n], found[n] + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spans.add(span)
            }
            if (select) {
                editor.setSelection(found[matchIndex], found[matchIndex] + query.length)
                editor.bringPointIntoView(found[matchIndex])
            }
        }
        updateFindLabel()
    }

    private fun findStep(direction: Int) {
        if (matches.isEmpty()) {
            refreshMatches(true)
            return
        }
        val query = findInput.text.toString()
        val next = (matchIndex + direction + matches.size) % matches.size
        // Move the "current" highlight without recomputing every match.
        val text = editor.text
        clearHighlights()
        for (n in matches.indices) {
            if (n >= MAX_HIGHLIGHTS && n != next) continue
            val span = BackgroundColorSpan(if (n == next) currentHighlight else highlight)
            text.setSpan(span, matches[n], matches[n] + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spans.add(span)
        }
        matchIndex = next
        editor.setSelection(matches[next], matches[next] + query.length)
        editor.bringPointIntoView(matches[next])
        updateFindLabel()
    }

    private fun replaceCurrent() {
        val query = findInput.text.toString()
        if (query.isEmpty()) return
        if (matchIndex !in matches.indices) {
            refreshMatches(true)
            return
        }
        val start = matches[matchIndex]
        val replacement = replaceInput.text.toString()
        editor.text.replace(start, start + query.length, replacement)
        refreshMatches(true, start + replacement.length)
    }

    private fun replaceAll() {
        val query = findInput.text.toString()
        if (query.isEmpty()) return
        refreshMatches(false)
        val count = matches.size
        if (count == 0) {
            Toast.makeText(this, "No matches to replace", Toast.LENGTH_SHORT).show()
            return
        }
        val before = editor.text.toString()
        val cursor = editor.selectionEnd.coerceAtLeast(0)
        val updated = before.replace(query, replaceInput.text.toString(), ignoreCase = !matchCase)
        clearHighlights()
        editor.setText(updated)
        editor.setSelection(cursor.coerceAtMost(editor.text.length))
        Toast.makeText(this, "$count replaced. Tap Undo to revert.", Toast.LENGTH_SHORT).show()
        refreshMatches(false)
    }
}
