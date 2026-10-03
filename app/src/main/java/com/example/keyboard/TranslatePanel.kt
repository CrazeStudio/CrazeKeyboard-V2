package com.example.keyboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme
import com.example.translate.TranslateEngine
import com.example.translate.TranslateLanguage

@Composable
fun TranslatePanel(
    theme: KeyboardTheme,
    sourceLanguageCode: String,
    targetLanguageCode: String,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onInsertTranslatedText: (String) -> Unit,
    onStartLiveTranslate: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }

    val sourceLang = remember(sourceLanguageCode) { TranslateEngine.getLanguage(sourceLanguageCode) }
    val targetLang = remember(targetLanguageCode) { TranslateEngine.getLanguage(targetLanguageCode) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        // Header: Language Selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageSelectorPill(
                language = sourceLang.name,
                isActive = showSourcePicker,
                theme = theme,
                onClick = {
                    showSourcePicker = !showSourcePicker
                    showTargetPicker = false
                },
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(theme.keyColor)
                    .clickable {
                        onSourceLanguageChange(targetLanguageCode)
                        onTargetLanguageChange(sourceLanguageCode)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_translate),
                    contentDescription = "Swap",
                    tint = theme.accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            LanguageSelectorPill(
                language = targetLang.name,
                isActive = showTargetPicker,
                theme = theme,
                onClick = {
                    showTargetPicker = !showTargetPicker
                    showSourcePicker = false
                },
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Close",
                    tint = theme.textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (showSourcePicker || showTargetPicker) {
            LanguagePickerList(
                languages = TranslateEngine.supportedLanguages,
                selectedCode = if (showSourcePicker) sourceLanguageCode else targetLanguageCode,
                theme = theme,
                onSelected = { code ->
                    if (showSourcePicker) {
                        onSourceLanguageChange(code)
                        showSourcePicker = false
                    } else {
                        onTargetLanguageChange(code)
                        showTargetPicker = false
                    }
                }
            )
        } else {
            // Live Translate Banner
            val isOnline by com.example.core.rememberNetworkStatus()
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.accentColor.copy(alpha = 0.1f))
                    .clickable { onStartLiveTranslate() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_translate),
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Live Keyboard Translation",
                            color = theme.textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOnline) "Instantly translate as you type" else "Offline dictionary mode active",
                            color = theme.textColor.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.accentColor)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("ON", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TranslateEngine.phrasebookCategories.size) { index ->
                    val cat = TranslateEngine.phrasebookCategories[index]
                    val isSelected = selectedCategoryIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) theme.accentColor else theme.keyColor)
                            .clickable { selectedCategoryIndex = index }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.name,
                            color = if (isSelected) Color.White else theme.textColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Phrase List
            val category = TranslateEngine.phrasebookCategories[selectedCategoryIndex]
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(category.phrases) { phrase ->
                    val translated = TranslateEngine.translate(phrase, sourceLanguageCode, targetLanguageCode)
                    PhraseCard(
                        phrase = phrase,
                        translated = translated,
                        theme = theme,
                        onClick = { onInsertTranslatedText(translated) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelectorPill(
    language: String,
    isActive: Boolean,
    theme: KeyboardTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) theme.accentColor else theme.keyColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = language,
            color = if (isActive) Color.White else theme.textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LanguagePickerList(
    languages: List<com.example.translate.TranslateLanguage>,
    selectedCode: String,
    theme: KeyboardTheme,
    onSelected: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyColor)
    ) {
        items(languages) { lang ->
            val isSelected = lang.code == selectedCode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(lang.code) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lang.name,
                    color = if (isSelected) theme.accentColor else theme.textColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check),
                        contentDescription = null,
                        tint = theme.accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PhraseCard(
    phrase: String,
    translated: String,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = phrase,
                color = theme.textColor.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
            Text(
                text = translated,
                color = theme.textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = theme.accentColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
