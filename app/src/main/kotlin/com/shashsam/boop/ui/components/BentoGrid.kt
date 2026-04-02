package com.shashsam.boop.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.resolveSocialIcon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableBentoGrid(
    items: List<ProfileItemEntity>,
    onEditItem: (ProfileItemEntity) -> Unit,
    onReorderItems: (List<ProfileItemEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberBoopHaptics()
    val tokens = LocalBoopTokens.current

    // Working copy synced from source-of-truth
    var workingItems by remember { mutableStateOf(items) }
    remember(items) { workingItems = items; items }

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        workingItems = workingItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gapDp = 8.dp
        val columnWidth = (maxWidth - gapDp * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val totalRows = calculateTotalRows(workingItems)
        val gridHeight = columnWidth * totalRows + gapDp * (totalRows - 1).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = lazyGridState,
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
            verticalArrangement = Arrangement.spacedBy(gapDp),
            horizontalArrangement = Arrangement.spacedBy(gapDp),
            userScrollEnabled = false
        ) {
            itemsIndexed(
                workingItems,
                key = { _, item -> item.id },
                span = { _, item ->
                    GridItemSpan(if (item.size == "full") 2 else 1)
                }
            ) { _, item ->
                ReorderableItem(reorderableLazyGridState, key = item.id) { isDragging ->
                    val icon = resolveSocialIcon(item.type, item.value)

                    GlassCard(
                        modifier = Modifier
                            .height(columnWidth)
                            .longPressDraggableHandle(
                                onDragStarted = { haptics.heavy() },
                                onDragStopped = {
                                    haptics.tick()
                                    onReorderItems(workingItems)
                                }
                            )
                            .combinedClickable(onClick = { onEditItem(item) })
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                shadowElevation = if (isDragging) 8f else 0f
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.size == "full") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    when (icon) {
                                        is ImageVector -> Icon(
                                            imageVector = icon,
                                            contentDescription = item.label,
                                            tint = tokens.accent,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        is Int -> Icon(
                                            painter = painterResource(id = icon),
                                            contentDescription = item.label,
                                            tint = tokens.accent,
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
                            } else {
                                when (icon) {
                                    is ImageVector -> Icon(
                                        imageVector = icon,
                                        contentDescription = item.label,
                                        tint = tokens.accent,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    is Int -> Icon(
                                        painter = painterResource(id = icon),
                                        contentDescription = item.label,
                                        tint = tokens.accent,
                                        modifier = Modifier.size(36.dp)
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

/**
 * Counts the total number of rows needed for the grid layout.
 */
private fun calculateTotalRows(items: List<ProfileItemEntity>): Int {
    if (items.isEmpty()) return 0
    var row = 0
    var col = 0
    for (item in items) {
        val span = if (item.size == "full") 2 else 1
        if (col + span > GRID_COLUMNS) {
            row++
            col = 0
        }
        col += span
        if (col >= GRID_COLUMNS) {
            row++
            col = 0
        }
    }
    return if (col > 0) row + 1 else row
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
