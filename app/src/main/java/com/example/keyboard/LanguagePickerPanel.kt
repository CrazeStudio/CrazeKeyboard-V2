package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme

@Composable
fun LanguagePickerPanel(
    theme: KeyboardTheme,
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_globe),
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Languages",
                color = theme.textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(theme.keyColor)
                    .clickable(onClick = onClose),
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

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(KeyboardLanguages.supportedLanguages, key = { it.code }) { language ->
                val isSelected = language.code == currentLanguageCode
                
                LanguageCard(
                    language = language,
                    isSelected = isSelected,
                    theme = theme,
                    onClick = { onLanguageSelected(language.code) }
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(
    language: com.example.keyboard.KeyboardLanguage,
    isSelected: Boolean,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) theme.accentColor.copy(alpha = 0.1f) else theme.keyColor)
            .border(
                width = 1.dp,
                color = if (isSelected) theme.accentColor else theme.borderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.nativeName,
                color = theme.textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${language.name} • ${language.layoutType}",
                color = theme.textColor.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Icon(
                painter = painterResource(id = R.drawable.ic_check),
                contentDescription = "Active",
                tint = theme.accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
