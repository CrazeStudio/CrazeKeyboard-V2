package com.example.keyboard

import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme

@Composable
fun KeyView(
    key: KeyModel,
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    shiftState: ShiftState,
    imeAction: Int = EditorInfo.IME_ACTION_NONE,
    isPressed: Boolean = false,
    showPreview: Boolean = false,
    popupInfo: ActivePopupInfo? = null,
    modifier: Modifier = Modifier
) {
    if (key.type == KeyType.SPACER) {
        Spacer(modifier = modifier)
        return
    }

    val isShifted = shiftState != ShiftState.OFF
    val isEnter = key.type == KeyType.ENTER
    val isAccentEnter = isEnter && (theme.id == "light" || theme.id == "snow" || theme.id == "system")
    val isSpecialKey = (key.type != KeyType.CHARACTER && key.type != KeyType.SPACE) ||
            key.primaryText == "," || key.primaryText == "." || key.primaryText == "/" ||
            key.primaryText == "¡" || key.primaryText == "¿"

    val baseKeyBg = if (isAccentEnter) theme.accentColor else if (isSpecialKey) theme.specialKeyColor else theme.keyColor
    val keyBg = if (isPressed) theme.accentColor.copy(alpha = 0.28f) else baseKeyBg

    val cornerRadius = remember(preferences.keyCornerRadiusDp) { preferences.keyCornerRadiusDp.dp }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val displayText = remember(key, shiftState, preferences.currentLanguage) {
        when {
            key.type == KeyType.CHARACTER -> if (isShifted) key.shiftedText else key.primaryText
            key.type == KeyType.SPACE -> KeyboardLanguages.getLanguage(preferences.currentLanguage).name
            else -> key.primaryText
        }
    }

    // Keep the hot keyboard surface cheap: avoid per-key graphics layers and GPU shadow passes.
    // Press feedback is expressed by the background/border only, so typing never waits for a
    // transform or offscreen shadow render.
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("key_${key.primaryText.ifEmpty { key.type.name }}")
            .padding((preferences.keySpacingDp / 2).dp)
            .background(keyBg, shape)
            .then(
                if (preferences.showKeyBorders) {
                    Modifier.border(1.dp, if (isPressed) theme.accentColor else theme.borderColor, shape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        KeyContent(key, theme, displayText, shiftState, imeAction, isAccentEnter)

        if (key.alternateText.isNotEmpty() && key.type == KeyType.CHARACTER) {
            Text(
                text = key.alternateText,
                color = theme.secondaryTextColor.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp)
            )
        }

        if (popupInfo != null && popupInfo.options.isNotEmpty()) {
            KeyLongPressPopup(
                alternateOptions = popupInfo.options,
                selectedOptionIndex = popupInfo.selectedIndex,
                theme = theme,
                isLeftEdge = popupInfo.isLeftEdge,
                isRightEdge = popupInfo.isRightEdge
            )
        }

        if (showPreview && popupInfo == null && preferences.isPopupPreviewEnabled &&
            key.type == KeyType.CHARACTER && displayText.isNotEmpty()
        ) {
            KeyPreviewOverlay(displayText, theme)
        }
    }
}

@Composable
private fun KeyContent(
    key: KeyModel,
    theme: KeyboardTheme,
    displayText: String,
    shiftState: ShiftState,
    imeAction: Int,
    isAccentEnter: Boolean
) {
    if (key.type == KeyType.SHIFT) {
        val shiftIcon = if (shiftState == ShiftState.CAPS_LOCK) R.drawable.ic_caps_lock else R.drawable.ic_shift
        Icon(painter = painterResource(id = shiftIcon), contentDescription = "Shift", tint = if (shiftState != ShiftState.OFF) theme.accentColor else theme.textColor, modifier = Modifier.size(20.dp))
    } else if (key.type == KeyType.ENTER) {
        val resolvedIcon = when (imeAction and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search
            EditorInfo.IME_ACTION_GO -> R.drawable.ic_arrow_forward
            EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check
            EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_arrow_forward
            else -> key.iconResId ?: R.drawable.ic_return
        }
        Icon(painter = painterResource(id = resolvedIcon), contentDescription = "Action", tint = if (isAccentEnter) Color.White else theme.accentColor, modifier = Modifier.size(20.dp))
    } else if (key.iconResId != null) {
        Icon(painter = painterResource(id = key.iconResId), contentDescription = key.type.name, tint = theme.textColor, modifier = Modifier.size(20.dp))
    } else {
        Text(
            text = displayText,
            color = if (key.type == KeyType.SPACE) theme.secondaryTextColor.copy(alpha = 0.6f) else theme.textColor,
            fontSize = when {
                key.type == KeyType.SPACE -> 12.sp
                key.type == KeyType.MODE_CHANGE -> 14.sp
                key.primaryText.length > 1 -> 13.sp
                else -> 23.sp
            },
            fontFamily = if (theme.isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (key.type == KeyType.MODE_CHANGE) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun KeyLongPressPopup(
    alternateOptions: List<String>,
    selectedOptionIndex: Int,
    theme: KeyboardTheme,
    isLeftEdge: Boolean,
    isRightEdge: Boolean
) {
    val popupAlignment = when {
        isLeftEdge -> Alignment.BottomStart
        isRightEdge -> Alignment.BottomEnd
        else -> Alignment.BottomCenter
    }
    Box(
        modifier = Modifier.wrapContentSize(popupAlignment, unbounded = true).zIndex(9999f).offset(y = (-58).dp)
            .clip(RoundedCornerShape(14.dp)).background(theme.popupColor)
            .border(1.5.dp, theme.accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            alternateOptions.forEachIndexed { index, option ->
                val isSelected = index == selectedOptionIndex
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 38.dp, minHeight = 46.dp)
                        .clip(RoundedCornerShape(8.dp)).background(if (isSelected) theme.accentColor else Color.Transparent)
                        .then(if (isSelected) Modifier.border(1.dp, theme.accentColor, RoundedCornerShape(8.dp)) else Modifier)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = option, color = if (isSelected) Color.White else theme.popupTextColor,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun KeyPreviewOverlay(displayText: String, theme: KeyboardTheme) {
    Box(
        modifier = Modifier.wrapContentSize(Alignment.BottomCenter, unbounded = true).zIndex(500f).offset(y = (-56).dp)
            .size(50.dp, 56.dp).clip(RoundedCornerShape(12.dp)).background(theme.popupColor)
            .border(1.dp, theme.accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = displayText, color = theme.popupTextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
