package com.example.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.ChatMessage
import com.example.ai.SearchSource
import com.example.ai.SearchResultItem
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme
import com.example.ui.MarkdownView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SwiftKeyAiTab {
    CHATBOT,
    TONE,
    ASK_AI
}

@Composable
fun SwiftKeyAiPanel(
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    contextText: String,
    currentQuery: String,
    activeTab: SwiftKeyAiTab,
    chatMessages: List<ChatMessage>,
    onTabChange: (SwiftKeyAiTab) -> Unit,
    onClearQuery: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onSendQuickQuery: (String) -> Unit,
    onRewriteTone: (String) -> Unit,
    onReplaceText: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onClose: () -> Unit,
    isProcessing: Boolean,
    aiResultText: String?,
    searchSource: SearchSource = SearchSource.ALL,
    onSearchSourceChange: (SearchSource) -> Unit = {},
    quickSearchResults: List<SearchResultItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var selectedSourceForDetail by remember { mutableStateOf<SearchResultItem?>(null) }

    // Auto-scroll chat to bottom when a new message arrives
    LaunchedEffect(chatMessages.size, isProcessing) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // 1. Top Header with Tabs & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Tab Switchers & Model Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab Switchers
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabPill(
                        selected = activeTab == SwiftKeyAiTab.CHATBOT,
                        icon = R.drawable.ic_chat,
                        label = "Chat",
                        theme = theme,
                        onClick = { onTabChange(SwiftKeyAiTab.CHATBOT) }
                    )
                    TabPill(
                        selected = activeTab == SwiftKeyAiTab.TONE,
                        icon = R.drawable.ic_sparkle,
                        label = "Tone",
                        theme = theme,
                        onClick = { onTabChange(SwiftKeyAiTab.TONE) }
                    )
                    TabPill(
                        selected = activeTab == SwiftKeyAiTab.ASK_AI,
                        icon = R.drawable.ic_ai,
                        label = "Ask",
                        theme = theme,
                        onClick = { onTabChange(SwiftKeyAiTab.ASK_AI) }
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))
                VerticalDivider(theme)
                Spacer(modifier = Modifier.width(2.dp))

                // Model Switcher
                ModelSwitcher(preferences, theme)
            }

            // Right Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Clear / New Chat button (active on CHATBOT tab)
                if (activeTab == SwiftKeyAiTab.CHATBOT && chatMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = "New Chat",
                            tint = theme.textColor.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close AI",
                        tint = theme.textColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2. Tab Content Views
        when (activeTab) {
            SwiftKeyAiTab.CHATBOT -> {
                ChatbotView(
                    theme = theme,
                    preferences = preferences,
                    chatMessages = chatMessages,
                    chatListState = chatListState,
                    currentQuery = currentQuery,
                    isProcessing = isProcessing,
                    searchSource = searchSource,
                    onSearchSourceChange = onSearchSourceChange,
                    onClearQuery = onClearQuery,
                    onSendChatMessage = onSendChatMessage,
                    onInsertText = onInsertText,
                    onViewSourceDetail = { selectedSourceForDetail = it }
                )
            }

            SwiftKeyAiTab.TONE -> {
                ToneRewriteView(
                    theme = theme,
                    contextText = contextText,
                    isProcessing = isProcessing,
                    onRewriteTone = onRewriteTone
                )
            }

            SwiftKeyAiTab.ASK_AI -> {
                QuickAskView(
                    theme = theme,
                    currentQuery = currentQuery,
                    isProcessing = isProcessing,
                    searchSource = searchSource,
                    onSearchSourceChange = onSearchSourceChange,
                    onClearQuery = onClearQuery,
                    onSendQuery = onSendQuickQuery
                )
            }
        }

        // 3. AI Result Card (for TONE and ASK_AI tabs)
        if (activeTab != SwiftKeyAiTab.CHATBOT) {
            AnimatedVisibility(
                visible = isProcessing || !aiResultText.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.keyColor.copy(alpha = 0.95f))
                        .border(1.dp, theme.accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (isProcessing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                color = theme.accentColor,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = if (searchSource != SearchSource.NONE) "Searching ${searchSource.displayName} & Thinking..." else "Thinking...",
                                color = theme.textColor,
                                fontSize = 11.5.sp
                            )
                        }
                    } else if (!aiResultText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownView(
                                markdown = aiResultText,
                                theme = theme
                            )
                        }

                        // Source pills for Quick Ask results
                        if (quickSearchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sources (${quickSearchResults.size}):",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.accentColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickSearchResults.forEach { item ->
                                    SourcePill(
                                        item = item,
                                        theme = theme,
                                        onClick = { selectedSourceForDetail = item }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy Action
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("AI Result", aiResultText))
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_copy),
                                    contentDescription = "Copy",
                                    tint = theme.textColor.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Insert Action
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(theme.keyColor)
                                    .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable { onInsertText(aiResultText) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "Insert",
                                    color = theme.textColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Insert with Sources Action
                            if (quickSearchResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            val textWithSources = buildString {
                                                append(aiResultText)
                                                append("\n\nSources:")
                                                quickSearchResults.forEach { res ->
                                                    if (!res.url.isNullOrBlank()) {
                                                        append("\n- [${res.title}](${res.url})")
                                                    } else {
                                                        append("\n- ${res.title}")
                                                    }
                                                }
                                            }
                                            onInsertText(textWithSources)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Insert + Sources",
                                        color = theme.accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Replace Action (if context was present)
                            if (contextText.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.accentColor)
                                        .clickable { onReplaceText(aiResultText) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Replace Text",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Source Details
    selectedSourceForDetail?.let { item ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            SourceDetailSheet(
                item = item,
                theme = theme,
                onClose = { selectedSourceForDetail = null },
                onInsertText = { text ->
                    onInsertText(text)
                    selectedSourceForDetail = null
                }
            )
        }
    }
    }
}

@Composable
fun SearchSourceSelector(
    currentSource: SearchSource,
    onSourceSelected: (SearchSource) -> Unit,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchSource.values().forEach { source ->
            val isSelected = source == currentSource
            val iconRes = when (source) {
                SearchSource.ALL -> R.drawable.ic_globe
                SearchSource.DUCKDUCKGO -> R.drawable.ic_duck
                SearchSource.WIKIPEDIA -> R.drawable.ic_wiki
                SearchSource.REDDIT -> R.drawable.ic_reddit
                SearchSource.NONE -> R.drawable.ic_bot
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) theme.accentColor else theme.keyColor)
                    .border(
                        1.dp,
                        if (isSelected) theme.accentColor else theme.accentColor.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSourceSelected(source) }
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = source.displayName,
                        tint = if (isSelected) Color.White else theme.textColor.copy(alpha = 0.75f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = source.displayName,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else theme.textColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
fun SourcePill(
    item: SearchResultItem,
    theme: KeyboardTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRes = when (item.source) {
        SearchSource.WIKIPEDIA -> R.drawable.ic_wiki
        SearchSource.REDDIT -> R.drawable.ic_reddit
        SearchSource.DUCKDUCKGO -> R.drawable.ic_duck
        else -> R.drawable.ic_globe
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyColor)
            .border(1.dp, theme.accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = item.source.displayName,
                tint = theme.accentColor,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = item.title,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = theme.textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_link),
                contentDescription = "Link",
                tint = theme.textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(9.dp)
            )
        }
    }
}

@Composable
fun SourceDetailSheet(
    item: SearchResultItem,
    theme: KeyboardTheme,
    onClose: () -> Unit,
    onInsertText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconRes = when (item.source) {
        SearchSource.WIKIPEDIA -> R.drawable.ic_wiki
        SearchSource.REDDIT -> R.drawable.ic_reddit
        SearchSource.DUCKDUCKGO -> R.drawable.ic_duck
        else -> R.drawable.ic_globe
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.backgroundColor)
            .border(1.5.dp, theme.accentColor, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = item.source.displayName,
                    tint = theme.accentColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${item.source.displayName} Source",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.accentColor
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = theme.textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title
        Text(
            text = item.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = theme.textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // Snippet
        if (item.snippet.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.snippet,
                fontSize = 10.5.sp,
                color = theme.textColor.copy(alpha = 0.8f),
                lineHeight = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Open URL in Browser
            if (!item.url.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.accentColor)
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Open Link",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Copy URL
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.keyColor)
                        .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("Source URL", item.url))
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Copy",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textColor
                    )
                }

                // Insert URL
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.keyColor)
                        .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            onInsertText(item.url)
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Insert Link",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textColor
                    )
                }
            }
        }
    }
}

@Composable
fun ChatbotView(
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    chatMessages: List<ChatMessage>,
    chatListState: androidx.compose.foundation.lazy.LazyListState,
    currentQuery: String,
    isProcessing: Boolean,
    searchSource: SearchSource = SearchSource.ALL,
    onSearchSourceChange: (SearchSource) -> Unit = {},
    onClearQuery: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onInsertText: (String) -> Unit,
    onViewSourceDetail: (SearchResultItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 190.dp)
    ) {
        // Chat Conversation Scroll Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (chatMessages.isEmpty()) {
                // Welcome card with suggested prompts
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bot),
                                contentDescription = "AI Bot",
                                tint = theme.accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = "Craze AI Chatbot",
                            color = theme.textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Conversational assistant with full Markdown, bold, lists, and code blocks.",
                        color = theme.textColor.copy(alpha = 0.65f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Starter suggestion chips (SVG/Icon based instead of emojis)
                    val starterPrompts = listOf(
                        "Latest tech news",
                        "History of Android",
                        "Best mechanical switches",
                        "Privacy tips",
                        "Help me draft a reply",
                        "Explain in simple terms"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        starterPrompts.forEach { prompt ->
                            val iconRes = when {
                                prompt.contains("tech", true) -> R.drawable.ic_globe
                                prompt.contains("History", true) -> R.drawable.ic_wiki
                                prompt.contains("switches", true) -> R.drawable.ic_reddit
                                prompt.contains("Privacy", true) -> R.drawable.ic_duck
                                else -> null
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.keyColor)
                                    .border(1.dp, theme.accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isProcessing) {
                                        onSendChatMessage(prompt)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (iconRes != null) {
                                        Icon(
                                            painter = painterResource(id = iconRes),
                                            contentDescription = null,
                                            tint = theme.accentColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = prompt,
                                        color = theme.textColor.copy(alpha = 0.9f),
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Message List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chatMessages, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            // User Message Bubble
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 2.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                        .background(theme.accentColor)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_person),
                                            contentDescription = "User",
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier
                                                .size(13.dp)
                                                .padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Assistant Bot Message Bubble with full Markdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                        .background(theme.keyColor)
                                        .border(1.dp, theme.accentColor.copy(alpha = 0.35f), RoundedCornerShape(topStart = 2.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                                        .padding(8.dp)
                                ) {
                                    // Bot header tag
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_bot),
                                            contentDescription = "Bot",
                                            tint = theme.accentColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        val modelName = if (msg.searchSource != null && msg.searchSource != SearchSource.NONE) {
                                            "Llama 3.3 70B"
                                        } else {
                                            when (preferences.preferredAiModel) {
                                                "gemini" -> "Gemini 2.0"
                                                "llama" -> "Llama 3.3 70B"
                                                else -> "Nex-AGI"
                                            }
                                        }
                                        Text(
                                            text = modelName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.accentColor
                                        )
                                        msg.searchSource?.let { src ->
                                            if (src != SearchSource.NONE) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(theme.accentColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "Grounded with ${src.displayName}",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = theme.accentColor
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Full Markdown Body
                                    MarkdownView(
                                        markdown = msg.text,
                                        theme = theme
                                    )

                                    // Render search result source pills
                                    if (msg.searchResults.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Sources (${msg.searchResults.size}):",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = theme.accentColor
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            msg.searchResults.forEach { resItem ->
                                                SourcePill(
                                                    item = resItem,
                                                    theme = theme,
                                                    onClick = { onViewSourceDetail(resItem) }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Action Toolbar (Copy & Insert into active field)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        var isMsgCopied by remember { mutableStateOf(false) }

                                        // Copy action
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Chatbot Reply", msg.text))
                                                    isMsgCopied = true
                                                    coroutineScope.launch {
                                                        delay(1200)
                                                        isMsgCopied = false
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = if (isMsgCopied) R.drawable.ic_check else R.drawable.ic_copy),
                                                contentDescription = "Copy message",
                                                tint = if (isMsgCopied) Color(0xFF4CAF50) else theme.textColor.copy(alpha = 0.75f),
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = if (isMsgCopied) "Copied" else "Copy",
                                                fontSize = 9.5.sp,
                                                color = if (isMsgCopied) Color(0xFF4CAF50) else theme.textColor.copy(alpha = 0.75f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Insert action
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(theme.accentColor.copy(alpha = 0.15f))
                                                .clickable { onInsertText(msg.text) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_return),
                                                contentDescription = "Insert text",
                                                tint = theme.accentColor,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Text(
                                                text = "Insert",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = theme.accentColor
                                            )
                                        }

                                        // Insert + Sources action
                                        if (msg.searchResults.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(theme.accentColor.copy(alpha = 0.2f))
                                                    .clickable {
                                                        val formattedWithSources = buildString {
                                                            append(msg.text)
                                                            append("\n\nSources:")
                                                            msg.searchResults.forEach { res ->
                                                                if (!res.url.isNullOrBlank()) {
                                                                    append("\n- [${res.title}](${res.url})")
                                                                } else {
                                                                    append("\n- ${res.title}")
                                                                }
                                                            }
                                                        }
                                                        onInsertText(formattedWithSources)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_link),
                                                    contentDescription = "Insert with sources",
                                                    tint = theme.accentColor,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "+ Sources",
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = theme.accentColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bot is thinking indicator
                    if (isProcessing) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(theme.keyColor)
                                        .border(1.dp, theme.accentColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        color = theme.accentColor,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = if (searchSource != SearchSource.NONE) "Searching ${searchSource.displayName} & Typing..." else "AI is typing...",
                                        fontSize = 11.sp,
                                        color = theme.textColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Search Source Selector Bar
        SearchSourceSelector(
            currentSource = searchSource,
            onSourceSelected = onSearchSourceChange,
            theme = theme
        )

        Spacer(modifier = Modifier.height(3.dp))

        // Chat Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.keyColor)
                .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (currentQuery.isEmpty()) {
                    Text(
                        text = "Message AI (type on keyboard below)...",
                        color = theme.textColor.copy(alpha = 0.5f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "$currentQuery|",
                        color = theme.textColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (currentQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = theme.textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (currentQuery.isNotBlank() && !isProcessing) {
                        onSendChatMessage(currentQuery)
                    }
                },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (currentQuery.isNotBlank() && !isProcessing) theme.accentColor else theme.keyColor.copy(alpha = 0.4f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = if (currentQuery.isNotBlank() && !isProcessing) Color.White else theme.textColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ToneRewriteView(
    theme: KeyboardTheme,
    contextText: String,
    isProcessing: Boolean,
    onRewriteTone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (contextText.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(theme.keyColor.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context: \"$contextText\"",
                    color = theme.textColor.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Text(
                text = "Type text in your app, then tap a tone below to rewrite it:",
                color = theme.textColor.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val toneChips = listOf(
            "Polish" to "polish",
            "Professional" to "professional",
            "Casual" to "casual",
            "Polite" to "polite",
            "Concise" to "concise",
            "Social" to "social"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            toneChips.forEach { (label, toneKey) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(theme.keyColor)
                        .border(1.dp, theme.accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !isProcessing && contextText.isNotBlank()) {
                            onRewriteTone(toneKey)
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = label,
                        color = if (contextText.isNotBlank()) theme.textColor else theme.textColor.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun QuickAskView(
    theme: KeyboardTheme,
    currentQuery: String,
    isProcessing: Boolean,
    searchSource: SearchSource = SearchSource.ALL,
    onSearchSourceChange: (SearchSource) -> Unit = {},
    onClearQuery: () -> Unit,
    onSendQuery: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Search Source Selector Bar
        SearchSourceSelector(
            currentSource = searchSource,
            onSourceSelected = onSearchSourceChange,
            theme = theme
        )

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.keyColor)
                .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                if (currentQuery.isEmpty()) {
                    Text(
                        text = "Quick Ask (type on keyboard below)...",
                        color = theme.textColor.copy(alpha = 0.5f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "$currentQuery|",
                        color = theme.textColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (currentQuery.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = theme.textColor.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (currentQuery.isNotBlank() && !isProcessing) {
                        onSendQuery(currentQuery)
                    }
                },
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (currentQuery.isNotBlank() && !isProcessing) theme.accentColor else theme.keyColor.copy(alpha = 0.5f))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_send),
                    contentDescription = "Send",
                    tint = if (currentQuery.isNotBlank() && !isProcessing) Color.White else theme.textColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val suggestions = listOf(
            "🌐 Web: Latest news",
            "📖 Wiki: Quantum computing",
            "🔴 Reddit: Top mechanical keyboards",
            "🦆 Duck: Privacy tools",
            "Explain this",
            "Help me reply",
            "Summarize",
            "Translate to English"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.keyColor.copy(alpha = 0.6f))
                        .clickable(enabled = !isProcessing) {
                            onSendQuery(suggestion)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = suggestion,
                        color = theme.textColor.copy(alpha = 0.85f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPill(
    selected: Boolean,
    icon: Int,
    label: String,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) theme.accentColor else theme.keyColor)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                tint = if (selected) Color.White else theme.textColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = if (selected) Color.White else theme.textColor,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun VerticalDivider(theme: KeyboardTheme) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(theme.textColor.copy(alpha = 0.15f))
    )
}

@Composable
private fun ModelSwitcher(
    preferences: KeyboardPreferences,
    theme: KeyboardTheme
) {
    val currentModel = preferences.preferredAiModel
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyColor)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val models = listOf("nex" to "Nex", "gemini" to "Gemini", "llama" to "Llama")
        models.forEach { (id, label) ->
            val isSelected = currentModel == id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) theme.accentColor else Color.Transparent)
                    .clickable { preferences.updatePreferredAiModel(id) }
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else theme.textColor.copy(alpha = 0.7f),
                    fontSize = 9.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
