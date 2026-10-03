package com.example.keyboard

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.ImageRequest
import com.example.R
import com.example.emoji.EmojiCategory
import com.example.emoji.EmojiData
import com.example.emoji.StickerData
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MediaTab {
    EMOJI, KAOMOJI, STICKER, GIF
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
    var currentTab by remember { mutableStateOf(initialTab) }
    val coroutineScope = rememberCoroutineScope()

    // Search state
    var emojiSearchQuery by remember { mutableStateOf("") }
    var gifSearchQuery by remember { mutableStateOf("") }
    var stickerCategory by remember { mutableStateOf("Trending") }

    // Emoji Category
    var selectedEmojiCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }

    val recentEmojis = preferences.recentEmojis
    val displayedEmojis = remember(selectedEmojiCategory, emojiSearchQuery, recentEmojis) {
        if (emojiSearchQuery.isNotBlank()) {
            EmojiData.searchEmojis(emojiSearchQuery)
        } else {
            EmojiData.getCategoryList(selectedEmojiCategory, recentEmojis)
        }
    }

    // GIF state
    var gifList by remember { mutableStateOf(GifRepository.curatedTrending) }
    var isGifLoading by remember { mutableStateOf(false) }
    var gifSearchJob by remember { mutableStateOf<Job?>(null) }

    fun loadGifs(query: String) {
        gifSearchJob?.cancel()
        gifSearchJob = coroutineScope.launch {
            isGifLoading = true
            gifList = GifRepository.searchGifs(query)
            isGifLoading = false
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == MediaTab.GIF && gifList.isEmpty()) {
            loadGifs(gifSearchQuery)
        }
    }

    // GIF & Sticker ImageLoader with 30MB Disk Cache limit
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil_cache"))
                    .maxSizeBytes(30 * 1024 * 1024L) // 30 MB Cache Limit
                    .build()
            }
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
    ) {
        // Top Search Bar / Filter Bar
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

                    val activeSearchQuery = when (currentTab) {
                        MediaTab.EMOJI -> emojiSearchQuery
                        MediaTab.KAOMOJI -> emojiSearchQuery
                        MediaTab.STICKER -> emojiSearchQuery
                        MediaTab.GIF -> gifSearchQuery
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onOpenSearch(currentTab) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (activeSearchQuery.isEmpty()) {
                            val placeholder = when (currentTab) {
                                MediaTab.EMOJI -> "Search emojis..."
                                MediaTab.KAOMOJI -> "Search kaomojis..."
                                MediaTab.STICKER -> "Search stickers..."
                                MediaTab.GIF -> "Search GIFs..."
                            }
                            Text(
                                text = placeholder,
                                color = theme.secondaryTextColor,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = activeSearchQuery,
                                color = theme.textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (activeSearchQuery.isNotEmpty()) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Clear",
                            tint = theme.secondaryTextColor,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    when (currentTab) {
                                        MediaTab.EMOJI -> emojiSearchQuery = ""
                                        MediaTab.KAOMOJI -> emojiSearchQuery = ""
                                        MediaTab.STICKER -> emojiSearchQuery = ""
                                        MediaTab.GIF -> {
                                            gifSearchQuery = ""
                                            loadGifs("")
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // Subcategory bar depending on tab
        when (currentTab) {
            MediaTab.KAOMOJI -> {}
            MediaTab.EMOJI -> {
                if (emojiSearchQuery.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(theme.toolbarColor.copy(alpha = 0.8f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EmojiCategory.values().forEach { category ->
                            val isSelected = selectedEmojiCategory == category
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
                                    .clickable { selectedEmojiCategory = category },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = category.iconResId),
                                    contentDescription = category.title,
                                    tint = if (isSelected) theme.accentColor else theme.secondaryTextColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            MediaTab.STICKER -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(theme.toolbarColor.copy(alpha = 0.8f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StickerData.categories.forEach { cat ->
                        val isSelected = stickerCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) theme.accentColor else theme.keyColor)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.accentColor else theme.borderColor,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { stickerCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else theme.textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            MediaTab.GIF -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(theme.toolbarColor.copy(alpha = 0.8f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GifRepository.curatedCategories.forEach { cat ->
                        val isSelected = gifSearchQuery.equals(cat, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) theme.accentColor else theme.keyColor)
                                .border(
                                    1.dp,
                                    if (isSelected) theme.accentColor else theme.borderColor,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    gifSearchQuery = cat
                                    loadGifs(cat)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.Black else theme.textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (currentTab) {
                MediaTab.KAOMOJI -> {
                    val kaomojis = listOf(
                        "(╯°□°)╯︵ ┻━┻", "┬─┬ノ( º _ ºノ)", "¯\\_(ツ)_/¯", "( ͡° ͜ʖ ͡°)", "ಠ_ಠ",
                        "(ง'̀-'́)ง", "(~˘▾˘)~", "(•_•)", "( •_•)>⌐■-■", "(⌐■_■)",
                        "ʕ•ᴥ•ʔ", "༼ つ ◕_◕ ༽つ", "(づ￣ ³￣)づ", "♥‿♥", "ᕙ(⇀‸↼‶)ᕗ",
                        "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "ಥ_ಥ", "ಠ~ಠ", "(งツ)ว", "ᕕ( ᐛ )ᕗ",
                        "(=^･ω･^=)", "(>_<)", "(;´༎ຶД༎ຶ`)", "(´･ω･`)", "( ˘ ³˘)♥"
                    )
                    val filtered = if (emojiSearchQuery.isEmpty()) kaomojis else kaomojis.filter { it.contains(emojiSearchQuery, ignoreCase = true) }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 60.dp)
                    ) {
                        items(filtered) { kaomoji ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(48.dp)
                                    .background(theme.keyColor, RoundedCornerShape(8.dp))
                                    .clickable { onEmojiSelected(kaomoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = kaomoji,
                                    fontSize = 16.sp,
                                    color = theme.textColor
                                )
                            }
                        }
                    }
                }
                MediaTab.EMOJI -> {
                    if (displayedEmojis.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No emojis found for \"$emojiSearchQuery\"",
                                color = theme.secondaryTextColor,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 40.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayedEmojis) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
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

                MediaTab.STICKER -> {
                    val stickers = remember(stickerCategory, emojiSearchQuery) {
                        if (emojiSearchQuery.isNotBlank()) {
                            StickerData.searchStickers(emojiSearchQuery)
                        } else {
                            StickerData.getByCategory(stickerCategory)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(stickers) { sticker ->
                            Box(
                                modifier = Modifier
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.keyColor)
                                    .border(1.dp, theme.borderColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        onGifSelected(
                                            GifItem(
                                                id = sticker.id,
                                                url = sticker.url,
                                                previewUrl = sticker.url,
                                                label = sticker.name
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(sticker.url)
                                        .crossfade(true)
                                        .build(),
                                    imageLoader = imageLoader,
                                    contentDescription = sticker.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                MediaTab.GIF -> {
                    if (isGifLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = theme.accentColor,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else if (gifList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No GIFs found for \"$gifSearchQuery\"",
                                color = theme.secondaryTextColor,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(gifList) { gif ->
                                Box(
                                    modifier = Modifier
                                        .height(88.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(theme.keyColor)
                                        .border(1.dp, theme.borderColor, RoundedCornerShape(8.dp))
                                        .clickable { onGifSelected(gif) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(gif.previewUrl.ifBlank { gif.url })
                                            .crossfade(true)
                                            .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = gif.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    )
                                    Text(
                                        text = gif.label,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom action strip (Gboard style with ABC, EMOJI, STICKER, GIF, and SVG Backspace)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(theme.toolbarColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ABC Return to keyboard Button
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

            // Tab Selector (Emoji, Sticker, GIF)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Emoji Tab
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

                // Sticker Tab
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentTab == MediaTab.STICKER) theme.keyColor else Color.Transparent)
                        .border(
                            1.dp,
                            if (currentTab == MediaTab.STICKER) theme.accentColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { currentTab = MediaTab.STICKER },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sticker),
                        contentDescription = "Stickers",
                        tint = if (currentTab == MediaTab.STICKER) theme.accentColor else theme.secondaryTextColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // GIF Tab
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (currentTab == MediaTab.GIF) theme.keyColor else Color.Transparent)
                        .border(
                            1.dp,
                            if (currentTab == MediaTab.GIF) theme.accentColor else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { currentTab = MediaTab.GIF },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_gif),
                        contentDescription = "GIFs",
                        tint = if (currentTab == MediaTab.GIF) theme.accentColor else theme.secondaryTextColor,
                        modifier = Modifier.size(19.dp)
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
