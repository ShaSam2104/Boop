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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private const val TAG = "BentoGrid"

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
                for (item in row) {
                    val weight = if (item.size == "full" || row.size == 1) 1f else 0.5f
                    BentoItem(
                        item = item,
                        isEditable = isEditable,
                        onItemClick = onItemClick,
                        onEditItem = onEditItem,
                        onDeleteItem = onDeleteItem,
                        modifier = Modifier.weight(weight)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoItem(
    item: ProfileItemEntity,
    isEditable: Boolean,
    onItemClick: (ProfileItemEntity) -> Unit,
    onEditItem: (ProfileItemEntity) -> Unit,
    onDeleteItem: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val icon = resolveSocialIcon(item.type, item.value)

    GlassCard(
        modifier = modifier.then(
            if (!isEditable) {
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
            } else Modifier
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (icon) {
                        is ImageVector -> Icon(
                            imageVector = icon,
                            contentDescription = item.label,
                            tint = tokens.accent,
                            modifier = Modifier.size(20.dp)
                        )
                        is Int -> Icon(
                            painter = painterResource(id = icon),
                            contentDescription = item.label,
                            tint = tokens.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    maxLines = if (item.size == "full") 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isEditable) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    IconButton(
                        onClick = { onEditItem(item) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { onDeleteItem(item.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun packIntoRows(items: List<ProfileItemEntity>): List<List<ProfileItemEntity>> {
    val rows = mutableListOf<List<ProfileItemEntity>>()
    var i = 0
    while (i < items.size) {
        val item = items[i]
        if (item.size == "full") {
            rows.add(listOf(item))
            i++
        } else {
            // Half-size item — check if next item is also half
            if (i + 1 < items.size && items[i + 1].size == "half") {
                rows.add(listOf(item, items[i + 1]))
                i += 2
            } else {
                rows.add(listOf(item))
                i++
            }
        }
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
