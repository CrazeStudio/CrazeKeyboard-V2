package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.regex.Pattern

sealed class MarkdownBlock {
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class ListItem(val text: String, val number: String? = null) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code Block
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language.ifEmpty { "code" }, codeLines.joinToString("\n")))
            continue
        }

        // 2. Divider
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headings
        if (trimmed.startsWith("#")) {
            val hashCount = trimmed.takeWhile { it == '#' }.length
            if (hashCount in 1..6 && (trimmed.length == hashCount || trimmed[hashCount] == ' ')) {
                val headingText = trimmed.drop(hashCount).trim()
                blocks.add(MarkdownBlock.Heading(hashCount, headingText))
                i++
                continue
            }
        }

        // 4. Blockquote
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            quoteLines.add(trimmed.removePrefix(">").trim())
            i++
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString(" ")))
            continue
        }

        // 5. Unordered List (- , * , + )
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            val itemText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.ListItem(text = itemText, number = null))
            i++
            continue
        }

        // 6. Ordered List
        val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1]
            val itemText = orderedMatch.groupValues[2]
            blocks.add(MarkdownBlock.ListItem(text = itemText, number = "$num."))
            i++
            continue
        }

        // 7. Empty line
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 8. Paragraph
        val paraLines = mutableListOf<String>()
        paraLines.add(trimmed)
        i++
        while (i < lines.size) {
            val nextTrimmed = lines[i].trim()
            if (nextTrimmed.isEmpty() ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("#") ||
                nextTrimmed.startsWith(">") ||
                nextTrimmed.startsWith("- ") ||
                nextTrimmed.startsWith("* ") ||
                Regex("^\\d+\\.\\s+").containsMatchIn(nextTrimmed) ||
                nextTrimmed == "---"
            ) {
                break
            }
            paraLines.add(nextTrimmed)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

fun parseInlineMarkdown(
    text: String,
    baseColor: Color,
    codeBgColor: Color,
    codeTextColor: Color,
    accentColor: Color
): AnnotatedString = buildAnnotatedString {
    val pattern = Pattern.compile("(\\[([^\\]]+)\\]\\(([^\\)]+)\\))|(`([^`]+)`)|(\\*\\*\\*([^*]+)\\*\\*\\*)|(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)|(~~([^~]+)~~)|(__([^_]+)__)|(_([^_]+)_)")
    val matcher = pattern.matcher(text)
    var lastIndex = 0

    while (matcher.find()) {
        val start = matcher.start()
        val end = matcher.end()

        if (start > lastIndex) {
            append(text.substring(lastIndex, start))
        }

        when {
            matcher.group(2) != null && matcher.group(3) != null -> { // [label](url)
                val label = matcher.group(2)
                val url = matcher.group(3)
                pushStringAnnotation(tag = "URL", annotation = url)
                pushStyle(
                    SpanStyle(
                        color = accentColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                append(label)
                pop()
                pop()
            }
            matcher.group(5) != null -> { // `inline code`
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBgColor,
                        color = codeTextColor,
                        fontSize = 11.sp
                    )
                )
                append(" ${matcher.group(5)} ")
                pop()
            }
            matcher.group(7) != null -> { // ***bold italic***
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                append(matcher.group(7))
                pop()
            }
            matcher.group(9) != null -> { // **bold**
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                append(matcher.group(9))
                pop()
            }
            matcher.group(11) != null -> { // *italic*
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(matcher.group(11))
                pop()
            }
            matcher.group(13) != null -> { // ~~strike~~
                pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                append(matcher.group(13))
                pop()
            }
            matcher.group(15) != null -> { // __bold__
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                append(matcher.group(15))
                pop()
            }
            matcher.group(17) != null -> { // _italic_
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(matcher.group(17))
                pop()
            }
            else -> {
                append(matcher.group())
            }
        }
        lastIndex = end
    }

    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
fun MarkdownView(
    markdown: String,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val isDarkTheme = theme.backgroundColor.red < 0.5f && theme.backgroundColor.green < 0.5f && theme.backgroundColor.blue < 0.5f
    
    val codeBgColor = if (isDarkTheme) Color(0xFF1E1E2E) else Color(0xFFEAEAEA)
    val codeTextColor = if (isDarkTheme) Color(0xFF89DDFF) else Color(0xFF005CC5)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 15.sp
                        2 -> 13.5.sp
                        else -> 12.5.sp
                    }
                    Text(
                        text = parseInlineMarkdown(
                            text = block.text,
                            baseColor = theme.textColor,
                            codeBgColor = codeBgColor,
                            codeTextColor = codeTextColor,
                            accentColor = theme.accentColor
                        ),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = theme.accentColor
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(
                            text = block.text,
                            baseColor = theme.textColor,
                            codeBgColor = codeBgColor,
                            codeTextColor = codeTextColor,
                            accentColor = theme.accentColor
                        ),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = theme.textColor
                    )
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (block.number != null) {
                            Text(
                                text = block.number,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentColor
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(theme.accentColor)
                            )
                        }

                        Text(
                            text = parseInlineMarkdown(
                                text = block.text,
                                baseColor = theme.textColor,
                                codeBgColor = codeBgColor,
                                codeTextColor = codeTextColor,
                                accentColor = theme.accentColor
                            ),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = theme.textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.keyColor.copy(alpha = 0.5f))
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(theme.accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = parseInlineMarkdown(
                                text = block.text,
                                baseColor = theme.textColor,
                                codeBgColor = codeBgColor,
                                codeTextColor = codeTextColor,
                                accentColor = theme.accentColor
                            ),
                            fontSize = 11.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = theme.textColor.copy(alpha = 0.9f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    CodeBlockCard(
                        language = block.language,
                        code = block.code,
                        theme = theme,
                        isDark = isDarkTheme
                    )
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = theme.textColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    theme: KeyboardTheme,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val cardBg = if (isDark) Color(0xFF16161E) else Color(0xFF24292E)
    val headerBg = if (isDark) Color(0xFF1F1F2E) else Color(0xFF2F363D)
    val codeColor = Color(0xFFE1E4E8)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardBg)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
    ) {
        // Code Block Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_terminal),
                    contentDescription = "Code",
                    tint = theme.accentColor,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = language.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Copy Code Action
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Code", code))
                        isCopied = true
                        coroutineScope.launch {
                            delay(1500)
                            isCopied = false
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    painter = painterResource(id = if (isCopied) R.drawable.ic_check else R.drawable.ic_copy),
                    contentDescription = if (isCopied) "Copied" else "Copy Code",
                    tint = if (isCopied) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = if (isCopied) "Copied!" else "Copy",
                    fontSize = 9.5.sp,
                    color = if (isCopied) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // Code Content (Horizontal scrollable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = codeColor
            )
        }
    }
}
