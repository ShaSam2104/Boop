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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.utils.resolveSocialIcon
import com.shashsam.boop.utils.resolveSocialIconColor

private const val TAG = "BentoGrid"
private const val GRID_COLUMNS = 4

@Composable
fun BentoGrid(
    items: List<ProfileItemEntity>,
    isEditable: Boolean,
    onItemClick: (ProfileItemEntity) -> Unit = {},
    onEditItem: (ProfileItemEntity) -> Unit = {},
    onDeleteItem: (Long) -> Unit = {},
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
                            isEditable = isEditable,
                            onItemClick = onItemClick,
                            onEditItem = onEditItem,
                            modifier = Modifier.weight(span.toFloat())
                        )
                    } else {
                        BentoItemHalf(
                            item = item,
                            isEditable = isEditable,
                            onItemClick = onItemClick,
                            onEditItem = onEditItem,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                // Fill remaining empty columns with spacers
                val remaining = GRID_COLUMNS - columnsUsed
                if (remaining > 0) {
                    Spacer(modifier = Modifier.weight(remaining.toFloat()))
                }
            }
        }
    }
}

/**
 * Half item (1x1) — icon-only square tile. Tap opens link/email/phone in view mode,
 * opens edit dialog in edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItemHalf(
    item: ProfileItemEntity,
    isEditable: Boolean,
    onItemClick: (ProfileItemEntity) -> Unit,
    onEditItem: (ProfileItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val icon = resolveSocialIcon(item.type, item.value)
    val iconColor = resolveSocialIconColor(item.type, item.value, tokens.accent)

    GlassCard(
        modifier = modifier.then(
            if (isEditable) {
                Modifier.combinedClickable(
                    onClick = { onEditItem(item) }
                )
            } else {
                Modifier.combinedClickable(
                    onClick = {
                        Log.d(TAG, "Item clicked: ${item.label}")
                        handleItemClick(context, item)
                        onItemClick(item)
                    },
                    onLongClick = {
                        copyToClipboard(context, item.value)
                    }
                )
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

/**
 * Full item (1x2) — icon + label text, wider tile. Tap opens link/email/phone in view mode,
 * opens edit dialog in edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItemFull(
    item: ProfileItemEntity,
    isEditable: Boolean,
    onItemClick: (ProfileItemEntity) -> Unit,
    onEditItem: (ProfileItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val icon = resolveSocialIcon(item.type, item.value)
    val iconColor = resolveSocialIconColor(item.type, item.value, tokens.accent)

    GlassCard(
        modifier = modifier.then(
            if (isEditable) {
                Modifier.combinedClickable(
                    onClick = { onEditItem(item) }
                )
            } else {
                Modifier.combinedClickable(
                    onClick = {
                        Log.d(TAG, "Item clicked: ${item.label}")
                        handleItemClick(context, item)
                        onItemClick(item)
                    },
                    onLongClick = {
                        copyToClipboard(context, item.value)
                    }
                )
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
