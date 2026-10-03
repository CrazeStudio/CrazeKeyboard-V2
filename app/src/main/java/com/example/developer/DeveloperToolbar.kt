package com.example.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.themes.KeyboardTheme

enum class DevKeyType {
    TEXT,
    ESC,
    TAB,
    CTRL,
    ALT,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    HOME,
    END
}

data class DevKeyItem(
    val label: String,
    val type: DevKeyType,
    val insertText: String = ""
)

@Composable
fun DeveloperToolbar(
    modifierState: ModifierState,
    theme: KeyboardTheme,
    onDevKeyClick: (DevKeyItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val devKeys = listOf(
        DevKeyItem("ESC", DevKeyType.ESC),
        DevKeyItem("TAB", DevKeyType.TAB),
        DevKeyItem("CTRL", DevKeyType.CTRL),
        DevKeyItem("ALT", DevKeyType.ALT),
        DevKeyItem("←", DevKeyType.ARROW_LEFT),
        DevKeyItem("→", DevKeyType.ARROW_RIGHT),
        DevKeyItem("↑", DevKeyType.ARROW_UP),
        DevKeyItem("↓", DevKeyType.ARROW_DOWN),
        DevKeyItem("HOME", DevKeyType.HOME),
        DevKeyItem("END", DevKeyType.END),
        DevKeyItem("{", DevKeyType.TEXT, "{"),
        DevKeyItem("}", DevKeyType.TEXT, "}"),
        DevKeyItem("[", DevKeyType.TEXT, "["),
        DevKeyItem("]", DevKeyType.TEXT, "]"),
        DevKeyItem("(", DevKeyType.TEXT, "("),
        DevKeyItem(")", DevKeyType.TEXT, ")"),
        DevKeyItem("<", DevKeyType.TEXT, "<"),
        DevKeyItem(">", DevKeyType.TEXT, ">"),
        DevKeyItem("/", DevKeyType.TEXT, "/"),
        DevKeyItem("\\", DevKeyType.TEXT, "\\"),
        DevKeyItem("|", DevKeyType.TEXT, "|"),
        DevKeyItem("`", DevKeyType.TEXT, "`"),
        DevKeyItem("~", DevKeyType.TEXT, "~"),
        DevKeyItem("=", DevKeyType.TEXT, "="),
        DevKeyItem("_", DevKeyType.TEXT, "_"),
        DevKeyItem(";", DevKeyType.TEXT, ";"),
        DevKeyItem(":", DevKeyType.TEXT, ":"),
        DevKeyItem("\"", DevKeyType.TEXT, "\""),
        DevKeyItem("'", DevKeyType.TEXT, "'"),
        DevKeyItem("&&", DevKeyType.TEXT, "&&"),
        DevKeyItem("||", DevKeyType.TEXT, "||"),
        DevKeyItem("=>", DevKeyType.TEXT, "=>"),
        DevKeyItem("->", DevKeyType.TEXT, "->")
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(theme.devToolbarColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        devKeys.forEach { key ->
            val isActiveModifier = when (key.type) {
                DevKeyType.CTRL -> modifierState.isCtrlActive
                DevKeyType.ALT -> modifierState.isAltActive
                else -> false
            }

            val keyBg = if (isActiveModifier) {
                theme.accentColor
            } else {
                theme.keyColor
            }

            val keyTextColor = if (isActiveModifier) {
                Color.Black
            } else {
                theme.textColor
            }

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .widthIn(min = 34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(keyBg)
                    .border(
                        width = 1.dp,
                        color = if (isActiveModifier) theme.accentColor else theme.borderColor,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onDevKeyClick(key) }
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = key.label,
                    color = keyTextColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isActiveModifier || key.type != DevKeyType.TEXT) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
