package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keyboard.KeyboardToolsCatalog
import com.example.settings.KeyboardPreferences

@Composable
fun ToolbarToolsScreen(
    preferences: KeyboardPreferences,
    modifier: Modifier = Modifier
) {
    val hotbarTools = preferences.hotbarTools

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Section 1: Active Hotbar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Hotbar (${hotbarTools.size}/6)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { preferences.resetHotbarTools() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "These tools appear in the top bar above the keyboard for instant 1-tap access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (hotbarTools.isEmpty()) {
                        Text(
                            text = "No tools pinned. Enable tools below to add them to your hotbar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(hotbarTools) { index, toolId ->
                                val tool = KeyboardToolsCatalog.getToolById(toolId)
                                if (tool != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                painter = painterResource(id = tool.iconRes),
                                                contentDescription = tool.title,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = tool.title,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Move left button
                                            if (index > 0) {
                                                IconButton(
                                                    onClick = { preferences.moveHotbarTool(index, index - 1) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Move Left",
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }

                                            // Move right button
                                            if (index < hotbarTools.size - 1) {
                                                IconButton(
                                                    onClick = { preferences.moveHotbarTool(index, index + 1) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Move Right",
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }

                                            // Remove button
                                            IconButton(
                                                onClick = { preferences.removeHotbarTool(tool.id) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Remove",
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: All Available Tools Catalog
        item {
            Text(
                text = "AVAILABLE TOOLS & SHORTCUTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        items(KeyboardToolsCatalog.allTools.size) { index ->
            val tool = KeyboardToolsCatalog.allTools[index]
            val isEnabled = hotbarTools.contains(tool.id)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { preferences.toggleHotbarTool(tool.id) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = tool.iconRes),
                        contentDescription = tool.title,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { preferences.toggleHotbarTool(tool.id) }
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}
