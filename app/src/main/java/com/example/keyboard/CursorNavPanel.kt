package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.themes.KeyboardTheme

@Composable
fun CursorNavPanel(
    theme: KeyboardTheme,
    isSelectMode: Boolean,
    onAction: (CursorAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gboard-style Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = theme.textColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Text(
                text = "Text editing",
                color = theme.textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Select All, Cut
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GboardEditButton(
                    iconRes = R.drawable.ic_select_all,
                    label = "Select all",
                    theme = theme,
                    onClick = { onAction(CursorAction.SELECT_ALL) }
                )
                GboardEditButton(
                    iconRes = R.drawable.ic_cut,
                    label = "Cut",
                    theme = theme,
                    onClick = { onAction(CursorAction.CUT) }
                )
            }

            // Center Column: D-pad
            Column(
                modifier = Modifier.weight(1.5f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // UP
                GboardNavButton(R.drawable.ic_arrow_up, theme) { onAction(CursorAction.UP) }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // LEFT
                    GboardNavButton(R.drawable.ic_arrow_left, theme) { onAction(CursorAction.LEFT) }
                    
                    // SELECT
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelectMode) theme.accentColor else theme.keyColor)
                            .clickable { onAction(CursorAction.TOGGLE_SELECT) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SELECT",
                            color = if (isSelectMode) theme.backgroundColor else theme.textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // RIGHT
                    GboardNavButton(R.drawable.ic_arrow_right, theme) { onAction(CursorAction.RIGHT) }
                }

                // DOWN
                GboardNavButton(R.drawable.ic_arrow_down, theme) { onAction(CursorAction.DOWN) }
            }

            // Right Column: Copy, Paste
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GboardEditButton(
                    iconRes = R.drawable.ic_copy,
                    label = "Copy",
                    theme = theme,
                    onClick = { onAction(CursorAction.COPY) }
                )
                GboardEditButton(
                    iconRes = R.drawable.ic_clipboard,
                    label = "Paste",
                    theme = theme,
                    onClick = { onAction(CursorAction.PASTE) }
                )
            }
        }

        // Bottom Row: Start / End
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GboardBottomNavButton(
                iconRes = R.drawable.ic_skip_previous,
                label = "Move to start",
                theme = theme,
                modifier = Modifier.weight(1f),
                onClick = { onAction(CursorAction.HOME) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            GboardBottomNavButton(
                iconRes = R.drawable.ic_skip_next,
                label = "Move to end",
                theme = theme,
                modifier = Modifier.weight(1f),
                onClick = { onAction(CursorAction.END) }
            )
        }
    }
}

@Composable
private fun GboardNavButton(
    iconRes: Int,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(theme.keyColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = theme.textColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun GboardEditButton(
    iconRes: Int,
    label: String,
    theme: KeyboardTheme,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = theme.textColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = theme.textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun GboardBottomNavButton(
    iconRes: Int,
    label: String,
    theme: KeyboardTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(theme.keyColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            tint = theme.textColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = theme.textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
