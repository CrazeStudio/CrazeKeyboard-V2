package com.example.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.settings.KeyboardLayoutMode
import com.example.settings.KeyboardPreferences
import com.example.themes.KeyboardTheme

@Composable
fun ToolsMenuPanel(
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    onToolSelected: (String) -> Unit,
    onOpenCustomize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolsList = remember { KeyboardToolsCatalog.allTools }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Beautiful Premium Toolkit Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_grid_menu),
                contentDescription = null,
                tint = theme.accentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Keyboard Toolkit",
                    color = theme.textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

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

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Section: Writing & Intelligence
            item {
                ToolSectionHeader("Writing & Intelligence", theme)
                Spacer(modifier = Modifier.height(8.dp))
                ToolGrid(
                    tools = listOf("AI", "TRANSLATE", "CLIPBOARD", "FONTS"),
                    theme = theme,
                    preferences = preferences,
                    onToolSelected = onToolSelected,
                    onOpenCustomize = onOpenCustomize
                )
            }

            // Section: Keyboard Layout
            item {
                ToolSectionHeader("Keyboard Layout", theme)
                Spacer(modifier = Modifier.height(8.dp))
                ToolGrid(
                    tools = listOf("LANGUAGE", "NUMBER_ROW", "ONE_HANDED", "FLOATING"),
                    theme = theme,
                    preferences = preferences,
                    onToolSelected = onToolSelected,
                    onOpenCustomize = onOpenCustomize
                )
            }

            // Section: Utility & Dev
            item {
                ToolSectionHeader("Utility & Feedback", theme)
                Spacer(modifier = Modifier.height(8.dp))
                ToolGrid(
                    tools = listOf("THEME", "SOUND", "VOICE", "DEV_MODE"),
                    theme = theme,
                    preferences = preferences,
                    onToolSelected = onToolSelected,
                    onOpenCustomize = onOpenCustomize
                )
            }
        }

        // Edit Hotbar Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(theme.keyColor)
                .clickable(onClick = onOpenCustomize)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = null,
                    tint = theme.accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Customize Toolbar",
                    color = theme.textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ToolSectionHeader(title: String, theme: KeyboardTheme) {
    Text(
        text = title.uppercase(),
        color = theme.textColor.copy(alpha = 0.5f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.sp
    )
}

@Composable
private fun ToolGrid(
    tools: List<String>,
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    onToolSelected: (String) -> Unit,
    onOpenCustomize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rows = tools.chunked(2)
        rows.forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTools.forEach { toolId ->
                    val tool = KeyboardToolsCatalog.getToolById(toolId)
                    if (tool != null) {
                        PremiumToolCard(
                            tool = tool,
                            theme = theme,
                            preferences = preferences,
                            onClick = { onToolSelected(tool.id) },
                            onLongClick = onOpenCustomize,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (rowTools.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PremiumToolCard(
    tool: KeyboardToolItem,
    theme: KeyboardTheme,
    preferences: KeyboardPreferences,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStateOn = when (tool.id) {
        "DEV_MODE" -> preferences.isDeveloperModeEnabled
        "SOUND" -> preferences.isHapticsEnabled
        "NUMBER_ROW" -> preferences.isNumberRowEnabled
        "ONE_HANDED" -> preferences.layoutMode != com.example.settings.KeyboardLayoutMode.DEFAULT
        "FLOATING" -> preferences.layoutMode == com.example.settings.KeyboardLayoutMode.FLOATING
        else -> false
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(theme.keyColor)
            .border(
                width = 1.dp,
                color = if (isStateOn) theme.accentColor.copy(alpha = 0.3f) else theme.borderColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = tool.iconRes),
                contentDescription = null,
                tint = if (isStateOn) theme.accentColor else theme.textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = tool.title,
                color = theme.textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
