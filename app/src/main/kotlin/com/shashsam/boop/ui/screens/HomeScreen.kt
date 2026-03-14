package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.SuccessGreen

private const val TAG = "HomeScreen"

/**
 * Immutable data model for a single status log entry shown in the activity card.
 */
data class LogEntry(
    val message: String,
    val isError: Boolean = false
)

/**
 * Home screen — the app's primary Compose destination.
 *
 * @param permissionsGranted Whether all runtime permissions have been granted.
 * @param statusLog         Ordered list of status / activity log messages.
 * @param onSendClick       Callback for the "Send File" FAB.
 * @param onReceiveClick    Callback for the "Receive File" FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    permissionsGranted: Boolean,
    statusLog: List<LogEntry>,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HomeScreen recompose — permissionsGranted=$permissionsGranted, logSize=${statusLog.size}")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Boop",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── System status banner ──────────────────────────────────────────
            SystemStatusBanner(permissionsGranted = permissionsGranted)

            // ── Action buttons ────────────────────────────────────────────────
            ActionButtonRow(
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick,
                enabled = permissionsGranted
            )

            // ── Activity log card ─────────────────────────────────────────────
            ActivityLogCard(
                logEntries = statusLog,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── System Status Banner ────────────────────────────────────────────────────

@Composable
private fun SystemStatusBanner(
    permissionsGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (permissionsGranted)
            SuccessGreen.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.errorContainer,
        animationSpec = tween(durationMillis = 500),
        label = "statusBannerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (permissionsGranted)
            SuccessGreen
        else
            MaterialTheme.colorScheme.onErrorContainer,
        animationSpec = tween(durationMillis = 500),
        label = "statusBannerContentColor"
    )

    val icon: ImageVector = if (permissionsGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val statusText = if (permissionsGranted)
        "Systems Ready: Permissions Granted"
    else
        "Awaiting Permissions…"

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically()
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}

// ─── Action Button Row ───────────────────────────────────────────────────────

@Composable
private fun ActionButtonRow(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Send File FAB
        ExtendedFloatingActionButton(
            onClick = {
                Log.d(TAG, "Send File clicked")
                onSendClick()
            },
            expanded = true,
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = "Send File"
                )
            },
            text = {
                Text(
                    text = "Send File",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Default
                )
            },
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        )

        // Receive File FAB
        ExtendedFloatingActionButton(
            onClick = {
                Log.d(TAG, "Receive File clicked")
                onReceiveClick()
            },
            expanded = true,
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = "Receive File"
                )
            },
            text = {
                Text(
                    text = "Receive File",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Default
                )
            },
            containerColor = if (enabled)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled)
                MaterialTheme.colorScheme.onSecondary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .height(64.dp)
        )
    }
}

// ─── Activity Log Card ───────────────────────────────────────────────────────

@Composable
private fun ActivityLogCard(
    logEntries: List<LogEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom whenever a new entry is appended
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (logEntries.isEmpty()) {
                Text(
                    text = "No activity yet. Tap a button to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(logEntries) { entry ->
                        LogEntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (entry.isError)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.isError)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun HomeScreenGrantedPreview() {
    BoopTheme(dynamicColor = false) {
        HomeScreen(
            permissionsGranted = true,
            statusLog = listOf(
                LogEntry("NFC adapter ready."),
                LogEntry("Wi-Fi Direct initialized."),
                LogEntry("Waiting for tap…")
            ),
            onSendClick = {},
            onReceiveClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAwaitingPreview() {
    BoopTheme(dynamicColor = false) {
        HomeScreen(
            permissionsGranted = false,
            statusLog = emptyList(),
            onSendClick = {},
            onReceiveClick = {}
        )
    }
}
