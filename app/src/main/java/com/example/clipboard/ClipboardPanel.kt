package com.example.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.clipboard.ClipboardItemEntity
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.launch

@Composable
fun ClipboardPanel(
    clipboardDao: ClipboardDao,
    theme: KeyboardTheme,
    onPasteText: (String) -> Unit,
    onCloseClipboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items by clipboardDao.getAllItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    
    val pinnedItems = remember(items) { items.filter { it.isPinned } }
    val recentItems = remember(items) { items.filter { !it.isPinned } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
    ) {
        // Premium Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(theme.toolbarColor)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_clipboard),
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Clipboard History",
                color = theme.textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (recentItems.isNotEmpty()) {
                Text(
                    text = "Clear all",
                    color = theme.accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { coroutineScope.launch { clipboardDao.clearUnpinned() } }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(theme.keyColor)
                    .clickable(onClick = onCloseClipboard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = theme.textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (items.isEmpty()) {
            ClipboardEmptyState(theme)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pinnedItems.isNotEmpty()) {
                    item {
                        ClipboardSectionHeader("Pinned", theme)
                    }
                    items(pinnedItems, key = { "pinned_${it.id}" }) { item ->
                        ClipboardItemCard(
                            item = item,
                            theme = theme,
                            onPaste = { onPasteText(item.text) },
                            onTogglePin = { 
                                coroutineScope.launch { 
                                    clipboardDao.update(item.copy(isPinned = false))
                                    clipboardDao.trimUnpinned(10)
                                }
                            },
                            onDelete = { coroutineScope.launch { clipboardDao.delete(item) } }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                if (recentItems.isNotEmpty()) {
                    item {
                        ClipboardSectionHeader("Recent", theme)
                    }
                    items(recentItems, key = { "recent_${it.id}" }) { item ->
                        ClipboardItemCard(
                            item = item,
                            theme = theme,
                            onPaste = { onPasteText(item.text) },
                            onTogglePin = { 
                                coroutineScope.launch { 
                                    clipboardDao.update(item.copy(isPinned = true))
                                }
                            },
                            onDelete = { coroutineScope.launch { clipboardDao.delete(item) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardSectionHeader(title: String, theme: KeyboardTheme) {
    Text(
        text = title,
        color = theme.textColor.copy(alpha = 0.5f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun ClipboardItemCard(
    item: ClipboardItemEntity,
    theme: KeyboardTheme,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyColor)
            .clickable(onClick = onPaste)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.text,
                color = theme.textColor,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onTogglePin),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_pin),
                contentDescription = if (item.isPinned) "Unpin" else "Pin",
                tint = if (item.isPinned) theme.accentColor else theme.textColor.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_delete),
                contentDescription = "Delete",
                tint = theme.textColor.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ClipboardEmptyState(theme: KeyboardTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.ic_clipboard),
                contentDescription = null,
                tint = theme.accentColor.copy(alpha = 0.2f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No clippings found",
                color = theme.textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Copy text anywhere to see it here.",
                color = theme.textColor.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
