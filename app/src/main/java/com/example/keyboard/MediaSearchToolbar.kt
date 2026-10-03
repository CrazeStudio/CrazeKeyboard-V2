package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.emoji.EmojiData
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaSearchToolbar(
    theme: KeyboardTheme,
    query: String,
    tab: MediaTab,
    onClose: () -> Unit,
    onEmojiSelected: (String) -> Unit
) {
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(query, tab) {
        if (query.isNotEmpty() && tab == MediaTab.EMOJI) {
            withContext(Dispatchers.Default) {
                val results = EmojiData.searchEmojis(query).take(15)
                searchResults = results
            }
        } else {
            searchResults = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .background(theme.toolbarColor)
    ) {
        // Search input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Close",
                tint = theme.secondaryTextColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() }
                    .padding(4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.keyColor)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (query.isEmpty()) "Search..." else query,
                    color = if (query.isEmpty()) theme.secondaryTextColor else theme.textColor,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // Search Results
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(theme.backgroundColor),
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { emoji ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }
    }
}
