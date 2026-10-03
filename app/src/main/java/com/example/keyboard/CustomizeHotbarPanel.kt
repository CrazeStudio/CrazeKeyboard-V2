package com.example.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.themes.KeyboardTheme
import kotlin.math.roundToInt

@Composable
fun CustomizeHotbarPanel(
    theme: KeyboardTheme,
    initialHotbarTools: List<String>,
    onSave: (List<String>) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hotbarList by remember { mutableStateOf(initialHotbarTools.toMutableList()) }

    // Tools that are not in hotbar
    val moreTools = remember(hotbarList.toList()) {
        KeyboardToolsCatalog.allTools.filter { !hotbarList.contains(it.id) }
    }

    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var isDraggingFromHotbar by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_drag_handle),
                    contentDescription = "Customize Hotbar",
                    tint = theme.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Organize Hotbar",
                    color = theme.textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reset Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.keyColor)
                        .clickable {
                            val defaultList = KeyboardToolsCatalog.defaultHotbarIds
                            hotbarList = defaultList.toMutableList()
                            onSave(defaultList)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        color = theme.secondaryTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Done / Save Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(theme.accentColor)
                        .clickable {
                            onSave(hotbarList)
                            onClose()
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check),
                            contentDescription = "Done",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Done",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val isHoveringOverHotbar = draggingItemId != null && !isDraggingFromHotbar && dragOffsetY < -45f
        val hotbarBorderColor = if (isHoveringOverHotbar) theme.accentColor else theme.accentColor.copy(alpha = 0.4f)
        val hotbarBorderWidth = if (isHoveringOverHotbar) 2.dp else 1.dp
        
        // SECTION 1: Hotbar Target Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isHoveringOverHotbar) theme.accentColor.copy(alpha = 0.08f) else theme.toolbarColor)
                .border(hotbarBorderWidth, hotbarBorderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOTBAR (${hotbarList.size}/5)",
                        color = theme.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Drag or tap items to rearrange",
                        color = theme.secondaryTextColor,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Hotbar Slots
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(hotbarList) { index, toolId ->
                        val tool = KeyboardToolsCatalog.getToolById(toolId)
                        if (tool != null) {
                            val isBeingDragged = draggingItemId == tool.id
                            val scale by animateFloatAsState(if (isBeingDragged) 1.15f else 1.0f)

                            val isInRemoveZone = isBeingDragged && isDraggingFromHotbar && dragOffsetY > 60f
                            val itemBgColor = if (isInRemoveZone) Color.Red.copy(alpha = 0.15f) else theme.keyColor
                            val itemBorderColor = if (isInRemoveZone) Color.Red else (if (isBeingDragged) theme.accentColor else theme.accentColor.copy(alpha = 0.5f))
                            val itemAlpha = if (isInRemoveZone) 0.6f else 1.0f

                            Box(
                                modifier = Modifier
                                    .zIndex(if (isBeingDragged) 10f else 1f)
                                    .offset {
                                        if (isBeingDragged) IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt())
                                        else IntOffset.Zero
                                    }
                                    .scale(scale)
                                    .shadow(if (isBeingDragged) 12.dp else 0.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(itemBgColor)
                                    .border(1.dp, itemBorderColor, RoundedCornerShape(8.dp))
                                    .pointerInput(tool.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingItemId = tool.id
                                                isDraggingFromHotbar = true
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                // If dragged downwards into More Features, remove from hotbar
                                                if (dragOffsetY > 60f) {
                                                    val newList = hotbarList.toMutableList()
                                                    newList.remove(tool.id)
                                                    hotbarList = newList
                                                    onSave(newList)
                                                }
                                                draggingItemId = null
                                                isDraggingFromHotbar = false
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingItemId = null
                                                isDraggingFromHotbar = false
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetX += dragAmount.x
                                                dragOffsetY += dragAmount.y

                                                // Real-time reordering / swapping in hotbar!
                                                val localIndex = hotbarList.indexOf(tool.id)
                                                if (localIndex != -1) {
                                                    // Button width in pixels is roughly 80.dp
                                                    val buttonWidthPx = 80.dp.toPx()
                                                    
                                                    if (dragOffsetX > buttonWidthPx * 0.8f && localIndex < hotbarList.size - 1) {
                                                        // Swap with next
                                                        val newList = hotbarList.toMutableList()
                                                        val temp = newList[localIndex]
                                                        newList[localIndex] = newList[localIndex + 1]
                                                        newList[localIndex + 1] = temp
                                                        hotbarList = newList
                                                        onSave(newList)
                                                        dragOffsetX -= buttonWidthPx
                                                    } else if (dragOffsetX < -buttonWidthPx * 0.8f && localIndex > 0) {
                                                        // Swap with previous
                                                        val newList = hotbarList.toMutableList()
                                                        val temp = newList[localIndex]
                                                        newList[localIndex] = newList[localIndex - 1]
                                                        newList[localIndex - 1] = temp
                                                        hotbarList = newList
                                                        onSave(newList)
                                                        dragOffsetX += buttonWidthPx
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = tool.iconRes),
                                        contentDescription = tool.title,
                                        tint = if (isInRemoveZone) Color.Red else theme.accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = tool.title,
                                        color = if (isInRemoveZone) Color.Red else theme.textColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    // Remove tap button
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(theme.backgroundColor.copy(alpha = 0.5f))
                                            .clickable {
                                                val newList = hotbarList.toMutableList()
                                                newList.remove(tool.id)
                                                hotbarList = newList
                                                onSave(newList)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_close),
                                            contentDescription = "Remove from Hotbar",
                                            tint = theme.secondaryTextColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty slot placeholder if < 5
                    if (hotbarList.size < 5) {
                        item {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        theme.borderColor.copy(alpha = 0.5f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Add Tool",
                                    color = theme.secondaryTextColor,
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // SECTION 2: More Features (available to drag up / add)
        Text(
            text = "MORE FEATURES (Tap or drag up to hotbar)",
            color = theme.secondaryTextColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(moreTools, key = { it.id }) { tool ->
                val isBeingDragged = draggingItemId == tool.id
                val scale by animateFloatAsState(if (isBeingDragged) 1.15f else 1.0f)

                Box(
                    modifier = Modifier
                        .zIndex(if (isBeingDragged) 10f else 1f)
                        .offset {
                            if (isBeingDragged) IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt())
                            else IntOffset.Zero
                        }
                        .scale(scale)
                        .shadow(if (isBeingDragged) 12.dp else 0.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.keyColor)
                        .border(1.dp, if (isBeingDragged) theme.accentColor else theme.borderColor, RoundedCornerShape(8.dp))
                        .pointerInput(tool.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingItemId = tool.id
                                    isDraggingFromHotbar = false
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    // If dragged upward towards hotbar, add to hotbar or replace
                                    if (dragOffsetY < -45f) {
                                        val newList = hotbarList.toMutableList()
                                        // Estimate drop index based on horizontal offset
                                        val buttonWidthPx = 80.dp.toPx()
                                        val dropIndex = (dragOffsetX / buttonWidthPx).roundToInt().coerceIn(0, newList.size)
                                        
                                        if (newList.size < 5) {
                                            if (dropIndex >= newList.size) {
                                                newList.add(tool.id)
                                            } else {
                                                newList.add(dropIndex, tool.id)
                                            }
                                        } else {
                                            // Full: replace/swap item at the estimated drop index!
                                            val targetIdx = dropIndex.coerceIn(0, 4)
                                            newList[targetIdx] = tool.id
                                        }
                                        hotbarList = newList
                                        onSave(newList)
                                    }
                                    draggingItemId = null
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingItemId = null
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                }
                            )
                        }
                        .clickable {
                            if (hotbarList.size < 5 && !hotbarList.contains(tool.id)) {
                                val newList = hotbarList.toMutableList()
                                newList.add(tool.id)
                                hotbarList = newList
                                onSave(newList)
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = tool.iconRes),
                            contentDescription = tool.title,
                            tint = theme.textColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tool.title,
                            color = theme.textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
