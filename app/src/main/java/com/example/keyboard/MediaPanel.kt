package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.emoji.EmojiCategory
import com.example.emoji.EmojiData
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme

enum class MediaTab {
    EMOJI, KAOMOJI, GIF
}

@Composable
fun MediaPanel(
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    initialTab: MediaTab = MediaTab.EMOJI,
    onEmojiSelected: (String) -> Unit,
    onGifSelected: (GifItem) -> Unit,
    onOpenSearch: (MediaTab) -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(if (initialTab == MediaTab.GIF) MediaTab.EMOJI else initialTab) }
    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    var searchQuery by remember { mutableStateOf("") }

    val recentEmojis = preferences.recentEmojis
    val displayedEmojis = remember(selectedCategory, searchQuery, recentEmojis) {
        if (searchQuery.isNotBlank()) {
            EmojiData.searchEmojis(searchQuery)
        } else {
            EmojiData.getCategoryList(selectedCategory, recentEmojis)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
    ) {
        // 1. Search Bar Header (100% Offline Local Search)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(theme.toolbarColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.keyColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = theme.secondaryTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = theme.textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(theme.accentColor),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = if (currentTab == MediaTab.KAOMOJI) "Search Kaomoji..." else "Search local emojis...",
                                    color = theme.secondaryTextColor,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Clear",
                            tint = theme.secondaryTextColor,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        // 2. Category Navigation Bar (All categories with vector icons)
        if (currentTab == MediaTab.EMOJI && searchQuery.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(theme.toolbarColor.copy(alpha = 0.85f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EmojiCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) theme.accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) theme.accentColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedCategory = category },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = category.iconResId),
                            contentDescription = category.title,
                            tint = if (isSelected) theme.accentColor else theme.secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 3. Main Content: Emoji Grid or Kaomoji Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentTab) {
                MediaTab.EMOJI -> {
                    if (displayedEmojis.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No emojis found for \"$searchQuery\"",
                                color = theme.secondaryTextColor,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 42.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayedEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            preferences.addRecentEmoji(emoji)
                                            onEmojiSelected(emoji)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 24.sp
                                    )
                                }
                            }
                        }
                    }
                }

                MediaTab.KAOMOJI -> {
                    val kaomojis = remember {
                        listOf(
                            "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ",
                            "(ง'̀-'́)ง", "(~˘▾˘)~", "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)",
                            "ʕ•ᴥ•ʔ", "༼ つ ◕_◕ ༽つ", "(づ￣ ³￣)づ", "♥‿♥", "ᕙ(⇀‸↼‶)ᕗ",
                            "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "ಥ_ಥ", "ಠ~ಠ", "(งツ)ว", "ᕕ( ᐛ )ᕗ",
                            "(=^･ω･^=)", "(>_<)", "(;´༎ຶД༎ຶ`)", "(´･ω･`)", "( ˘ ³˘)♥",
                            "(¬‿¬)", "(¬_¬)", "(ʘ‿ʘ)", "(◕‿◕)", "(⊙_☉)", "(づ｡◕‿‿◕｡)づ"
                        )
                    }
                    val filtered = remember(searchQuery, kaomojis) {
                        if (searchQuery.isEmpty()) kaomojis else kaomojis.filter { it.contains(searchQuery, ignoreCase = true) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(110.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filtered) { kaomoji ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.keyColor)
                                    .border(1.dp, theme.borderColor, RoundedCornerShape(8.dp))
                                    .clickable { onEmojiSelected(kaomoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = kaomoji,
                                    fontSize = 15.sp,
                                    color = theme.textColor
                                )
                            }
                        }
                    }
                }

                MediaTab.GIF -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Online GIF search is disabled in offline mode.",
                            color = theme.secondaryTextColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 4. Bottom Action Bar: ABC, Tab Toggle, SVG Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(theme.toolbarColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ABC button to return to keyboard
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.keyColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(8.dp))
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ABC",
                    color = theme.textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sub-tabs: EMOJI, KAOMOJI
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentTab == MediaTab.EMOJI) theme.keyColor else Color.Transparent)
                        .border(
                            1.dp,
                            if (currentTab == MediaTab.EMOJI) theme.accentColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { currentTab = MediaTab.EMOJI },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_emoji),
                        contentDescription = "Emoji",
                        tint = if (currentTab == MediaTab.EMOJI) theme.accentColor else theme.secondaryTextColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentTab == MediaTab.KAOMOJI) theme.keyColor else Color.Transparent)
                        .border(
                            1.dp,
                            if (currentTab == MediaTab.KAOMOJI) theme.accentColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { currentTab = MediaTab.KAOMOJI }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "(^_^)",
                        color = if (currentTab == MediaTab.KAOMOJI) theme.accentColor else theme.secondaryTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SVG Backspace
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(theme.keyColor)
                    .border(1.dp, theme.borderColor, RoundedCornerShape(8.dp))
                    .clickable { onBackspace() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_backspace),
                    contentDescription = "Backspace",
                    tint = theme.textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
