package com.shashsam.boop.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shashsam.boop.ui.models.RecentBoop
import com.shashsam.boop.ui.theme.BoopAccentYellow
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.toFormattedSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "HistoryScreen"
private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

private enum class DirectionFilter(val label: String) {
    ALL("All"), SENT("Sent"), RECEIVED("Received")
}

private enum class FileTypeFilter(val label: String) {
    ALL("All"), IMAGES("Images"), VIDEOS("Videos"), DOCUMENTS("Docs"), OTHER("Other")
}

/** Matches a MIME type to a [FileTypeFilter]. */
private fun classifyMimeType(mimeType: String): FileTypeFilter = when {
    mimeType.startsWith("image/") -> FileTypeFilter.IMAGES
    mimeType.startsWith("video/") -> FileTypeFilter.VIDEOS
    mimeType.startsWith("text/") || mimeType in setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    ) -> FileTypeFilter.DOCUMENTS
    else -> FileTypeFilter.OTHER
}

@Composable
fun HistoryScreen(
    recentTransfers: List<RecentBoop>,
    onResend: (RecentBoop) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HistoryScreen composed — ${recentTransfers.size} total transfers")

    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val last30Days = recentTransfers.filter { now - it.timestamp < THIRTY_DAYS_MS }
        .sortedByDescending { it.timestamp }

    var directionFilter by remember { mutableStateOf(DirectionFilter.ALL) }
    var fileTypeFilter by remember { mutableStateOf(FileTypeFilter.ALL) }

    val filteredList = last30Days.filter { boop ->
        val dirMatch = when (directionFilter) {
            DirectionFilter.ALL -> true
            DirectionFilter.SENT -> boop.wasSender
            DirectionFilter.RECEIVED -> !boop.wasSender
        }
        val typeMatch = when (fileTypeFilter) {
            FileTypeFilter.ALL -> true
            else -> classifyMimeType(boop.mimeType) == fileTypeFilter
        }
        dirMatch && typeMatch
    }.sortedByDescending { it.timestamp }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────
        Text(
            text = "Transfer History",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Last 30 days",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Direction filter chips ──────────────────────────────────────────
        Text(
            text = "Direction",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DirectionFilter.entries.forEach { filter ->
                FilterChip(
                    selected = directionFilter == filter,
                    onClick = { directionFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (directionFilter == filter) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── File type filter chips ──────────────────────────────────────────
        Text(
            text = "File Type",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tokens.textSecondary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileTypeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = fileTypeFilter == filter,
                    onClick = { fileTypeFilter = filter },
                    label = {
                        Text(
                            text = filter.label,
                            fontWeight = if (fileTypeFilter == filter) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            // ── Empty state ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No transfers yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your completed transfers from the\nlast 30 days will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // ── Transfer list ──────────────────────────────────────────────
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { "${it.timestamp}_${it.fileName}" }) { boop ->
                    HistoryItem(
                        boop = boop,
                        onOpen = {
                            boop.fileUri?.let { uri ->
                                Log.d(TAG, "Opening file: ${boop.fileName}")
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, boop.mimeType.ifEmpty { "*/*" })
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.w(TAG, "No app to open ${boop.fileName}", e)
                                }
                            }
                        },
                        onResend = { onResend(boop) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    boop: RecentBoop,
    onOpen: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = boop.fileUri != null, onClick = { haptics.click(); onOpen() })
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Direction icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (boop.wasSender) BoopAccentYellow.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (boop.wasSender) Icons.Filled.CloudUpload else Icons.Filled.CloudDownload,
                    contentDescription = if (boop.wasSender) "Sent" else "Received",
                    tint = if (boop.wasSender) BoopAccentYellow else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = boop.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${boop.fileSize.toFormattedSize()} — ${if (boop.wasSender) "Sent" else "Received"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
                Text(
                    text = formatTimestamp(boop.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textTertiary
                )
            }

            // Re-share button (only if we have a URI)
            if (boop.fileUri != null) {
                IconButton(
                    onClick = {
                        Log.d(TAG, "Re-share: ${boop.fileName}")
                        haptics.click()
                        onResend()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Re-share",
                        tint = tokens.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.InsertDriveFile,
                    contentDescription = null,
                    tint = tokens.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HistoryScreenEmptyPreview() {
    BoopTheme(darkTheme = true) {
        HistoryScreen(recentTransfers = emptyList(), onResend = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HistoryScreenWithDataPreview() {
    BoopTheme(darkTheme = true) {
        HistoryScreen(
            recentTransfers = listOf(
                RecentBoop("vacation_photos.zip", 4_500_000L, "application/zip", System.currentTimeMillis() - 3600_000, true),
                RecentBoop("report.pdf", 2_100_000L, "application/pdf", System.currentTimeMillis() - 86400_000, false),
                RecentBoop("song.mp3", 8_000_000L, "audio/mpeg", System.currentTimeMillis() - 172800_000, true)
            ),
            onResend = {}
        )
    }
}
