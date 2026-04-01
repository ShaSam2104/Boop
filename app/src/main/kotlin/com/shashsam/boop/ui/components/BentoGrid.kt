package com.shashsam.boop.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.utils.BoopHaptics
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.resolveSocialIcon
import kotlin.math.roundToInt

private const val TAG = "BentoGrid"
private const val GRID_COLUMNS = 4

@Composable
fun BentoGrid(
    items: List<ProfileItemEntity>,
    isEditable: Boolean,
    onItemClick: (ProfileItemEntity) -> Unit = {},
    onEditItem: (ProfileItemEntity) -> Unit = {},
    onDeleteItem: (Long) -> Unit = {},
    onReorderItems: (List<ProfileItemEntity>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isEditable) {
        DraggableBentoGrid(
            items = items,
            onEditItem = onEditItem,
            onReorderItems = onReorderItems,
            modifier = modifier
        )
    } else {
        StaticBentoGrid(
            items = items,
            onItemClick = onItemClick,
            modifier = modifier
        )
    }
}

// ─── Draggable grid (edit mode) ──────────────────────────────────────────────

@Composable
private fun DraggableBentoGrid(
    items: List<ProfileItemEntity>,
    onEditItem: (ProfileItemEntity) -> Unit,
    onReorderItems: (List<ProfileItemEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberBoopHaptics()
    val density = LocalDensity.current

    // Working copy of the item order — synced from source-of-truth
    val workingItems = remember { mutableStateListOf<ProfileItemEntity>() }
    remember(items) {
        workingItems.clear()
        workingItems.addAll(items)
    }

    // Drag state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Track each item's bounds by flat index
    val itemBounds = remember { mutableMapOf<Int, LayoutCoordinates>() }

    // Grid parent coordinates for hit-testing
    var gridCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val rows = packIntoRows(workingItems)

    // Build a flat index for each item
    var flatIndex = 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { gridCoordinates = it },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var columnsUsed = 0
                for (item in row) {
                    val span = if (item.size == "full") 2 else 1
                    columnsUsed += span
                    val currentFlatIndex = flatIndex
                    val isDragged = draggedIndex == currentFlatIndex

                    key(item.id) {
                        BentoItemDraggable(
                            item = item,
                            isDragged = isDragged,
                            dragOffset = if (isDragged) dragOffset else Offset.Zero,
                            haptics = haptics,
                            onEditItem = onEditItem,
                            onDragStart = {
                                draggedIndex = currentFlatIndex
                                dragOffset = Offset.Zero
                                haptics.heavy()
                            },
                            onDrag = { change ->
                                dragOffset += change
                                // Hit-test: find which item we're over
                                val grid = gridCoordinates ?: return@BentoItemDraggable
                                val draggedBounds = itemBounds[draggedIndex] ?: return@BentoItemDraggable
                                val draggedPos = draggedBounds.positionInParent()
                                val dragCenter = Offset(
                                    draggedPos.x + draggedBounds.size.width / 2f + dragOffset.x,
                                    draggedPos.y + draggedBounds.size.height / 2f + dragOffset.y
                                )

                                for ((idx, coords) in itemBounds) {
                                    if (idx == draggedIndex) continue
                                    val pos = coords.positionInParent()
                                    val left = pos.x
                                    val top = pos.y
                                    val right = left + coords.size.width
                                    val bottom = top + coords.size.height
                                    if (dragCenter.x in left..right && dragCenter.y in top..bottom) {
                                        // Swap
                                        val fromIdx = draggedIndex
                                        val temp = workingItems[fromIdx]
                                        workingItems.removeAt(fromIdx)
                                        workingItems.add(idx, temp)
                                        draggedIndex = idx
                                        dragOffset = Offset.Zero
                                        haptics.tick()
                                        break
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedIndex = -1
                                dragOffset = Offset.Zero
                                onReorderItems(workingItems.toList())
                            },
                            onDragCancel = {
                                draggedIndex = -1
                                dragOffset = Offset.Zero
                            },
                            onPositioned = { coords ->
                                itemBounds[currentFlatIndex] = coords
                            },
                            modifier = Modifier.weight(span.toFloat())
                        )
                    }
                    flatIndex++
                }
                val remaining = GRID_COLUMNS - columnsUsed
                if (remaining > 0) {
                    Spacer(modifier = Modifier.weight(remaining.toFloat()))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItemDraggable(
    item: ProfileItemEntity,
    isDragged: Boolean,
    dragOffset: Offset,
    haptics: BoopHaptics,
    onEditItem: (ProfileItemEntity) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val icon = resolveSocialIcon(item.type, item.value)
    val iconColor = tokens.accent

    Box(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .zIndex(if (isDragged) 10f else 0f)
            .graphicsLayer {
                if (isDragged) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.08f
                    scaleY = 1.08f
                    alpha = 0.9f
                    shadowElevation = 16f
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
    ) {
        GlassCard(
            modifier = Modifier.combinedClickable(
                onClick = { onEditItem(item) }
            )
        ) {
            if (item.size == "full") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        when (icon) {
                            is ImageVector -> Icon(
                                imageVector = icon,
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier.size(32.dp)
                            )
                            is Int -> Icon(
                                painter = painterResource(id = icon),
                                contentDescription = item.label,
                                tint = iconColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (icon) {
                        is ImageVector -> Icon(
                            imageVector = icon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(36.dp)
                        )
                        is Int -> Icon(
                            painter = painterResource(id = icon),
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Static grid (view mode) ─────────────────────────────────────────────────

@Composable
private fun StaticBentoGrid(
    items: List<ProfileItemEntity>,
    onItemClick: (ProfileItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = packIntoRows(items)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var columnsUsed = 0
                for (item in row) {
                    val span = if (item.size == "full") 2 else 1
                    columnsUsed += span
                    if (item.size == "full") {
                        BentoItemFull(
                            item = item,
                            onItemClick = onItemClick,
                            modifier = Modifier.weight(span.toFloat())
                        )
                    } else {
                        BentoItemHalf(
                            item = item,
                            onItemClick = onItemClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                val remaining = GRID_COLUMNS - columnsUsed
                if (remaining > 0) {
                    Spacer(modifier = Modifier.weight(remaining.toFloat()))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItemHalf(
    item: ProfileItemEntity,
    onItemClick: (ProfileItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val icon = resolveSocialIcon(item.type, item.value)
    val iconColor = tokens.accent

    GlassCard(
        modifier = modifier.combinedClickable(
            onClick = {
                Log.d(TAG, "Item clicked: ${item.label}")
                handleItemClick(context, item)
                onItemClick(item)
            },
            onLongClick = {
                copyToClipboard(context, item.value)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(36.dp)
                )
                is Int -> Icon(
                    painter = painterResource(id = icon),
                    contentDescription = item.label,
                    tint = iconColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItemFull(
    item: ProfileItemEntity,
    onItemClick: (ProfileItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val icon = resolveSocialIcon(item.type, item.value)
    val iconColor = tokens.accent

    GlassCard(
        modifier = modifier.combinedClickable(
            onClick = {
                Log.d(TAG, "Item clicked: ${item.label}")
                handleItemClick(context, item)
                onItemClick(item)
            },
            onLongClick = {
                copyToClipboard(context, item.value)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                    is Int -> Icon(
                        painter = painterResource(id = icon),
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Packs items into rows for a 4-column grid.
 * Half items take 1 column, full items take 2 columns.
 */
private fun packIntoRows(items: List<ProfileItemEntity>): List<List<ProfileItemEntity>> {
    val rows = mutableListOf<List<ProfileItemEntity>>()
    val currentRow = mutableListOf<ProfileItemEntity>()
    var columnsUsed = 0

    for (item in items) {
        val span = if (item.size == "full") 2 else 1
        if (columnsUsed + span > GRID_COLUMNS) {
            if (currentRow.isNotEmpty()) {
                rows.add(currentRow.toList())
                currentRow.clear()
                columnsUsed = 0
            }
        }
        currentRow.add(item)
        columnsUsed += span
        if (columnsUsed >= GRID_COLUMNS) {
            rows.add(currentRow.toList())
            currentRow.clear()
            columnsUsed = 0
        }
    }
    if (currentRow.isNotEmpty()) {
        rows.add(currentRow.toList())
    }
    return rows
}

private fun handleItemClick(context: Context, item: ProfileItemEntity) {
    try {
        val intent = when (item.type) {
            "email" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${item.value}"))
            "phone" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.value}"))
            "link" -> {
                val url = if (item.value.startsWith("http")) item.value else "https://${item.value}"
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
            }
            else -> return
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Cannot open item: ${item.value}", e)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Boop", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
