package com.example.keyboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme

enum class ToolbarAction {
    TOGGLE_DEV_MODE,
    OPEN_TRANSLATE,
    OPEN_EMOJI,
    OPEN_GIF,
    OPEN_CLIPBOARD,
    OPEN_CURSOR_NAV,
    OPEN_SETTINGS,
    CYCLE_THEME,
    VOICE_INPUT,
    OPEN_FONTS,
    OPEN_LANGUAGE,
    TOGGLE_SOUND,
    TOGGLE_NUMBER_ROW,
    OPEN_ONE_HANDED,
    OPEN_FLOATING,
    OPEN_SPEED_TEST,
    OPEN_TOOLS_MENU,
    OPEN_CUSTOMIZE_HOTBAR,
    OPEN_AI
}

@Composable
fun KeyboardToolbar(
    theme: KeyboardTheme,
    suggestions: List<String>,
    aiContextualEmojis: List<String> = emptyList(),
    hotbarToolIds: List<String>,
    isDevModeActive: Boolean,
    autoCorrectNotice: Pair<String, String>?,
    clipboardSuggestion: String? = null,
    inlineViews: List<android.view.View> = emptyList(),
    onUndoAutoCorrect: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onPasteClipboardSuggestion: (String) -> Unit = {},
    onEmojiClick: (String) -> Unit = {},
    onAction: (ToolbarAction) -> Unit,
    isSentenceStart: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasSuggestionsContent = remember(autoCorrectNotice, inlineViews, clipboardSuggestion, suggestions, aiContextualEmojis) {
        autoCorrectNotice != null ||
                inlineViews.isNotEmpty() ||
                !clipboardSuggestion.isNullOrBlank() ||
                suggestions.isNotEmpty() ||
                aiContextualEmojis.isNotEmpty()
    }

    var isHotbarManuallyExpanded by remember { mutableStateOf(false) }
    val isShowingHotbar = isHotbarManuallyExpanded || (!hasSuggestionsContent && isSentenceStart)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(theme.toolbarColor),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.animation.AnimatedContent(
            targetState = isShowingHotbar,
            transitionSpec = {
                (fadeIn(animationSpec = tween(150)) + androidx.compose.animation.scaleIn(initialScale = 0.95f))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + androidx.compose.animation.scaleOut(targetScale = 0.95f))
            },
            label = "ToolbarTransition"
        ) { showingHotbar ->
            if (showingHotbar) {
                HotbarStrip(
                    theme = theme,
                    hotbarToolIds = hotbarToolIds,
                    isDevModeActive = isDevModeActive,
                    hasSuggestionsContent = hasSuggestionsContent,
                    onAction = onAction,
                    onCollapse = { isHotbarManuallyExpanded = false }
                )
            } else {
                SuggestionStripWrapper(
                    theme = theme,
                    suggestions = suggestions,
                    autoCorrectNotice = autoCorrectNotice,
                    aiContextualEmojis = aiContextualEmojis,
                    clipboardSuggestion = clipboardSuggestion,
                    inlineViews = inlineViews,
                    onExpand = { isHotbarManuallyExpanded = true },
                    onUndoAutoCorrect = onUndoAutoCorrect,
                    onSuggestionClick = onSuggestionClick,
                    onPasteClipboardSuggestion = onPasteClipboardSuggestion,
                    onEmojiClick = onEmojiClick
                )
            }
        }
    }
}

@Composable
private fun HotbarStrip(
    theme: KeyboardTheme,
    hotbarToolIds: List<String>,
    isDevModeActive: Boolean,
    hasSuggestionsContent: Boolean,
    onAction: (ToolbarAction) -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasSuggestionsContent) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Collapse",
                    tint = theme.accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GridView,
                    contentDescription = null,
                    tint = theme.accentColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(hotbarToolIds) { toolId ->
                val tool = KeyboardToolsCatalog.getToolById(toolId)
                if (tool != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { 
                                when(tool.id) {
                                    "TRANSLATE" -> onAction(ToolbarAction.OPEN_TRANSLATE)
                                    "AI" -> onAction(ToolbarAction.OPEN_AI)
                                    "CLIPBOARD" -> onAction(ToolbarAction.OPEN_CLIPBOARD)
                                    "CURSOR_NAV" -> onAction(ToolbarAction.OPEN_CURSOR_NAV)
                                    "FONTS" -> onAction(ToolbarAction.OPEN_FONTS)
                                    "LANGUAGE" -> onAction(ToolbarAction.OPEN_LANGUAGE)
                                    "GIF" -> onAction(ToolbarAction.OPEN_GIF)
                                    "THEME" -> onAction(ToolbarAction.CYCLE_THEME)
                                    "SOUND" -> onAction(ToolbarAction.TOGGLE_SOUND)
                                    "NUMBER_ROW" -> onAction(ToolbarAction.TOGGLE_NUMBER_ROW)
                                    "SETTINGS" -> onAction(ToolbarAction.OPEN_SETTINGS)
                                    "VOICE" -> onAction(ToolbarAction.VOICE_INPUT)
                                    "ONE_HANDED" -> onAction(ToolbarAction.OPEN_ONE_HANDED)
                                    "FLOATING" -> onAction(ToolbarAction.OPEN_FLOATING)
                                    "SPEED_TEST" -> onAction(ToolbarAction.OPEN_SPEED_TEST)
                                    "DEV_MODE" -> onAction(ToolbarAction.TOGGLE_DEV_MODE)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = tool.iconRes),
                            contentDescription = tool.title,
                            tint = theme.textColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            item {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onAction(ToolbarAction.OPEN_TOOLS_MENU) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "More",
                        tint = theme.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionStripWrapper(
    theme: KeyboardTheme,
    suggestions: List<String>,
    autoCorrectNotice: Pair<String, String>?,
    aiContextualEmojis: List<String>,
    clipboardSuggestion: String?,
    inlineViews: List<android.view.View>,
    onExpand: () -> Unit,
    onUndoAutoCorrect: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    onPasteClipboardSuggestion: (String) -> Unit,
    onEmojiClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onExpand),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Expand",
                tint = theme.accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        LazyRow(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            if (autoCorrectNotice != null) {
                item {
                    AutocorrectPill(autoCorrectNotice, theme, onUndoAutoCorrect)
                }
            }

            if (!clipboardSuggestion.isNullOrBlank()) {
                item {
                    ClipboardSuggestionPill(clipboardSuggestion, theme, onPasteClipboardSuggestion)
                }
            }

            items(suggestions.size) { index ->
                val suggestion = suggestions[index]
                val isAutocorrect = index == 1 && suggestions.size > 1
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onSuggestionClick(suggestion) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = suggestion,
                        color = if (isAutocorrect) theme.accentColor else theme.textColor,
                        fontSize = 15.sp,
                        fontWeight = if (isAutocorrect) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = if (theme.isMonospace) FontFamily.Monospace else FontFamily.Default
                    )
                }
            }

            items(aiContextualEmojis) { emoji ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onEmojiClick(emoji) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun AutocorrectPill(notice: Pair<String, String>, theme: KeyboardTheme, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.accentColor.copy(alpha = 0.15f))
            .clickable(onClick = onUndo)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AutoFixHigh,
            contentDescription = null,
            tint = theme.accentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Undo: ${notice.first}",
            color = theme.accentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ClipboardSuggestionPill(text: String, theme: KeyboardTheme, onPaste: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyColor)
            .border(1.dp, theme.borderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onPaste(text) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = null,
            tint = theme.accentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = theme.textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 100.dp)
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: KeyboardTheme,
    onClick: () -> Unit,
    contentDescription: String,
    backgroundColor: Color = theme.keyColor
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = theme.accentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HotbarToolButton(
    iconRes: Int,
    title: String,
    theme: KeyboardTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = theme.textColor,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun DevModeButton(isDevModeActive: Boolean, theme: KeyboardTheme, onAction: (ToolbarAction) -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isDevModeActive) theme.accentColor else theme.keyColor)
            .clickable { onAction(ToolbarAction.TOGGLE_DEV_MODE) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "DEV",
            color = if (isDevModeActive) theme.backgroundColor else theme.textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
