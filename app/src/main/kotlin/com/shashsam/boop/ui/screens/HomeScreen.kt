package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shashsam.boop.R
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.SuccessGreen
import com.shashsam.boop.ui.viewmodels.TransferUiState

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
 * @param transferUiState    Current transfer pipeline state from [TransferViewModel][com.shashsam.boop.ui.viewmodels.TransferViewModel].
 * @param onSendClick        Callback for the "Send File" FAB (also launches the file picker).
 * @param onReceiveClick     Callback for the "Receive File" FAB.
 * @param onFileSelected     Callback invoked with the file [android.net.Uri] after the file picker returns.
 * @param onResetClick       Callback to reset the transfer state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    permissionsGranted: Boolean,
    transferUiState: TransferUiState,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HomeScreen recompose — permissionsGranted=$permissionsGranted logSize=${transferUiState.statusLog.size}")

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

            // ── NFC / Wi-Fi status indicators ─────────────────────────────────
            NfcWifiStatusRow(uiState = transferUiState)

            // ── Action buttons ────────────────────────────────────────────────
            ActionButtonRow(
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick,
                onResetClick = onResetClick,
                enabled = permissionsGranted,
                isSendMode = transferUiState.isSendMode,
                isReceiveMode = transferUiState.isReceiveMode,
                isTransferring = transferUiState.isTransferring
            )

            // ── Transfer progress ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = transferUiState.isTransferring || transferUiState.transferComplete,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                TransferProgressCard(uiState = transferUiState)
            }

            // ── Activity log card ─────────────────────────────────────────────
            ActivityLogCard(
                logEntries = transferUiState.statusLog,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── NFC / Wi-Fi Status Row ──────────────────────────────────────────────────

@Composable
private fun NfcWifiStatusRow(
    uiState: TransferUiState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = uiState.isNfcBroadcasting || uiState.isNfcReading || uiState.isWifiConnecting,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut()
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isNfcBroadcasting || uiState.isNfcReading) {
                StatusChip(
                    icon = Icons.Filled.Nfc,
                    label = if (uiState.isNfcBroadcasting) "NFC: Broadcasting" else "NFC: Reading",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            if (uiState.isWifiConnecting) {
                StatusChip(
                    icon = Icons.Filled.Wifi,
                    label = "Wi-Fi Direct…",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

// ─── Transfer Progress Card ──────────────────────────────────────────────────

@Composable
private fun TransferProgressCard(
    uiState: TransferUiState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.transferComplete) "Transfer Complete ✅" else "Transferring…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(uiState.transferProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { uiState.transferProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            uiState.savedFileUri?.let { uri ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Saved to Downloads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            }
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
            MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "statusBannerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (permissionsGranted)
            SuccessGreen
        else
            MaterialTheme.colorScheme.onTertiaryContainer,
        animationSpec = tween(durationMillis = 500),
        label = "statusBannerContentColor"
    )

    val icon: ImageVector = if (permissionsGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning
    val statusText = if (permissionsGranted)
        stringResource(R.string.status_permissions_granted)
    else
        stringResource(R.string.status_awaiting_permissions)

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
    onResetClick: () -> Unit,
    enabled: Boolean,
    isSendMode: Boolean,
    isReceiveMode: Boolean,
    isTransferring: Boolean,
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
            enabled = enabled && !isTransferring,
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.send_file)
                )
            },
            text = {
                Text(
                    text = if (isSendMode) "Sending…" else stringResource(R.string.send_file),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Default
                )
            },
            containerColor = if (enabled && !isTransferring)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled && !isTransferring)
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
            enabled = enabled && !isTransferring,
            icon = {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = stringResource(R.string.receive_file)
                )
            },
            text = {
                Text(
                    text = if (isReceiveMode) "Waiting…" else stringResource(R.string.receive_file),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Default
                )
            },
            containerColor = if (enabled && !isTransferring)
                MaterialTheme.colorScheme.secondary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled && !isTransferring)
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
            transferUiState = TransferUiState(
                statusLog = listOf(
                    LogEntry("NFC adapter ready."),
                    LogEntry("Wi-Fi Direct initialized."),
                    LogEntry("Waiting for tap…")
                )
            ),
            onSendClick = {},
            onReceiveClick = {},
            onResetClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenTransferringPreview() {
    BoopTheme(dynamicColor = false) {
        HomeScreen(
            permissionsGranted = true,
            transferUiState = TransferUiState(
                statusLog = listOf(LogEntry("🚀 Transferring file…")),
                isTransferring = true,
                isSendMode = true,
                transferProgress = 0.45f
            ),
            onSendClick = {},
            onReceiveClick = {},
            onResetClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAwaitingPreview() {
    BoopTheme(dynamicColor = false) {
        HomeScreen(
            permissionsGranted = false,
            transferUiState = TransferUiState(),
            onSendClick = {},
            onReceiveClick = {},
            onResetClick = {}
        )
    }
}
