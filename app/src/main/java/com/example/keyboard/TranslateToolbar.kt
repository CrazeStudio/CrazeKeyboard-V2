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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme
import com.example.translate.TranslateEngine

@Composable
fun TranslateToolbar(
    theme: KeyboardTheme,
    sourceLanguageCode: String,
    targetLanguageCode: String,
    sourceText: String,
    translatedText: String,
    onSwapLanguages: () -> Unit,
    onCommitTranslated: (String) -> Unit,
    onOpenTranslatePanel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceLang = TranslateEngine.getLanguage(sourceLanguageCode)
    val targetLang = TranslateEngine.getLanguage(targetLanguageCode)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(theme.toolbarColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Source -> Target Language indicator pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(theme.accentColor.copy(alpha = 0.2f))
                .clickable { onOpenTranslatePanel() }
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sourceLang.symbol,
                color = theme.accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward),
                contentDescription = "To",
                tint = theme.accentColor,
                modifier = Modifier
                    .size(12.dp)
                    .padding(horizontal = 1.dp)
            )
            Text(
                text = targetLang.symbol,
                color = theme.accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(5.dp))

        // Center live translation preview box
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(theme.keyColor)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (translatedText.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = translatedText,
                        color = theme.textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Insert translated button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.accentColor)
                            .clickable { onCommitTranslated(translatedText) }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Commit",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Type in ${sourceLang.name} to translate...",
                        color = theme.secondaryTextColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Offline Dictionary Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(theme.borderColor.copy(alpha = 0.4f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Offline",
                            color = theme.secondaryTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Close live translate button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.keyColor)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Exit Translate Mode",
                tint = theme.secondaryTextColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
