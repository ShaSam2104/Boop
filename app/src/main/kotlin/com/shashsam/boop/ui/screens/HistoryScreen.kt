package com.shashsam.boop.ui.screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.shashsam.boop.ui.models.RecentBoop
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.toFormattedSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

/** Returns icon for the given MIME type. */
private fun getFileTypeIcon(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/") -> Icons.Filled.Image
    mimeType.startsWith("video/") -> Icons.Filled.Videocam
    mimeType.startsWith("audio/") -> Icons.Filled.AudioFile
    mimeType.startsWith("text/") || mimeType.contains("pdf") ||
        mimeType.contains("document") || mimeType.contains("word") ||
        mimeType.contains("sheet") || mimeType.contains("presentation") -> Icons.Filled.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
fun HistoryScreen(
    recentTransfers: List<RecentBoop>,
    onResend: (RecentBoop) -> Unit,
    friendName: String? = null,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HistoryScreen composed — ${recentTransfers.size} total transfers, friend=$friendName")

    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val last30Days = recentTransfers.filter { now - it.timestamp < THIRTY_DAYS_MS }
        .sortedByDescending { it.timestamp }

    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var directionFilter by remember { mutableStateOf(DirectionFilter.ALL) }
    var fileTypeFilter by remember { mutableStateOf(FileTypeFilter.ALL) }

    val activeFilterCount = (if (directionFilter != DirectionFilter.ALL) 1 else 0) +
        (if (fileTypeFilter != FileTypeFilter.ALL) 1 else 0)

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
        val searchMatch = searchQuery.isEmpty() ||
            boop.fileName.contains(searchQuery, ignoreCase = true)
        dirMatch && typeMatch && searchMatch
    }

    // Shared open-file handler
    val openFile: (RecentBoop) -> Unit = { boop ->
        val uri = boop.fileUri
        if (uri != null) {
            Log.d(TAG, "Opening file: ${boop.fileName}")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, boop.mimeType.ifEmpty { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "No app to open ${boop.fileName}", e)
                Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.d(TAG, "No URI for file: ${boop.fileName}")
            Toast.makeText(context, "File no longer available", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────
        if (friendName != null && onBackClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = "Transfer History",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = friendName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textSecondary
                    )
                }
            }
        } else {
            Text(
                text = "Transfer History",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Search bar + View toggle + Filter button ────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(BoopShapeMedium)
                    .background(tokens.glassBg)
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = tokens.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tokens.textTertiary
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = tokens.textSecondary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { searchQuery = "" }
                                )
                            }
                        }
                    }
                )
            }

            // Grid/List toggle button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(BoopShapeMedium)
                    .background(tokens.glassBg)
                    .clickable {
                        haptics.tick()
                        isGridView = !isGridView
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (isGridView) "Switch to list" else "Switch to grid",
                    tint = tokens.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Filter toggle button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(BoopShapeMedium)
                    .background(
                        if (showFilters || activeFilterCount > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            tokens.glassBg
                    )
                    .clickable {
                        haptics.tick()
                        showFilters = !showFilters
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Filters",
                    tint = if (showFilters || activeFilterCount > 0)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        tokens.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                // Active filter badge
                if (activeFilterCount > 0 && !showFilters) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(tokens.accent)
                    )
                }
            }
        }

        // ── Collapsible filters ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp)
            ) {
                // Direction filter chips
                Text(
                    text = "Direction",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DirectionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = directionFilter == filter,
                            onClick = { haptics.tick(); directionFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (directionFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = tokens.glassBg
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                enabled = true,
                                selected = directionFilter == filter
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // File type filter chips
                Text(
                    text = "File Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textSecondary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FileTypeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = fileTypeFilter == filter,
                            onClick = { haptics.tick(); fileTypeFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (fileTypeFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = tokens.glassBg
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                enabled = true,
                                selected = fileTypeFilter == filter
                            )
                        )
                    }
                }

                // Clear filters button (when filters are active)
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Clear filters",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tokens.accent,
                        modifier = Modifier.clickable {
                            haptics.tick()
                            directionFilter = DirectionFilter.ALL
                            fileTypeFilter = FileTypeFilter.ALL
                        }
                    )
                }
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
                    text = if (searchQuery.isNotEmpty()) "No results" else "No transfers yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty())
                        "Try a different search term"
                    else
                        "Your completed transfers from the\nlast 30 days will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (isGridView) {
            // ── Grid view ──────────────────────────────────────────────────
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredList, key = { "${it.timestamp}_${it.fileName}" }) { boop ->
                    HistoryGridItem(
                        boop = boop,
                        onOpen = { openFile(boop) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            // ── List view ──────────────────────────────────────────────────
            LazyColumn {
                items(filteredList, key = { "${it.timestamp}_${it.fileName}" }) { boop ->
                    HistoryItem(
                        boop = boop,
                        onOpen = { openFile(boop) },
                        onResend = { onResend(boop) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color = tokens.textTertiary.copy(alpha = 0.3f)
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─── List item ──────────────────────────────────────────────────────────────

@Composable
private fun HistoryItem(
    boop: RecentBoop,
    onOpen: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { haptics.click(); onOpen() })
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // File type thumbnail
        FileTypeThumbnail(
            mimeType = boop.mimeType,
            fileUri = boop.fileUri,
            size = 56.dp
        )

        // File info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = boop.fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatTimestamp(boop.timestamp)} - ${boop.fileSize.toFormattedSize()}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary
            )
        }

        // Re-share button
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
                tint = tokens.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Grid item ──────────────────────────────────────────────────────────────

@Composable
private fun HistoryGridItem(
    boop: RecentBoop,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptics.click(); onOpen() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large thumbnail
        FileTypeThumbnail(
            mimeType = boop.mimeType,
            fileUri = boop.fileUri,
            size = 0.dp, // ignored — uses fillMaxWidth + aspectRatio
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // File name
        Text(
            text = boop.fileName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Date
        Text(
            text = formatTimestamp(boop.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center
        )

        // File size
        Text(
            text = boop.fileSize.toFormattedSize(),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Shared thumbnail ───────────────────────────────────────────────────────

@Composable
private fun FileTypeThumbnail(
    mimeType: String,
    fileUri: android.net.Uri?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val context = LocalContext.current
    val isImage = mimeType.startsWith("image/")

    // If a specific size is given (> 0), use it; otherwise rely on modifier
    val sizeModifier = if (size > 0.dp) {
        modifier.size(size)
    } else {
        modifier
    }

    Box(
        modifier = sizeModifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.glassBg),
        contentAlignment = Alignment.Center
    ) {
        // Base layer: file type icon (always rendered as fallback)
        Icon(
            imageVector = getFileTypeIcon(mimeType),
            contentDescription = null,
            tint = tokens.textSecondary,
            modifier = Modifier.size(26.dp)
        )

        // Overlay: actual image thumbnail when available
        if (isImage && fileUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fileUri)
                    .crossfade(true)
                    .size(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val todayCal = Calendar.getInstance()
    val tsCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val isToday = todayCal.get(Calendar.YEAR) == tsCal.get(Calendar.YEAR) &&
        todayCal.get(Calendar.DAY_OF_YEAR) == tsCal.get(Calendar.DAY_OF_YEAR)

    return if (isToday) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
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
