package com.shashsam.boop.ui.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shashsam.boop.nfc.ConnectionDetails
import com.shashsam.boop.ui.theme.BoopBottomNavBar
import com.shashsam.boop.ui.theme.NeoBrutalistButton
import com.shashsam.boop.ui.viewmodels.SettingsUiState
import com.shashsam.boop.ui.viewmodels.TransferUiState
import com.shashsam.boop.utils.LocalHapticsEnabled

private const val TAG = "BoopScaffold"
private const val PREFS_NAME = "boop_prefs"
private const val KEY_ANTENNA_GUIDE_SEEN = "nfc_antenna_guide_seen"

/**
 * Top-level scaffold with bottom navigation, overlays (NFC payload sheet,
 * error dialog), and auto-navigation for transfer progress and NFC guide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoopScaffold(
    transferUiState: TransferUiState,
    settingsState: SettingsUiState,
    permissionsGranted: Boolean,
    onSendClick: () -> Unit,
    onResetClick: () -> Unit,
    onResendBoop: (com.shashsam.boop.ui.models.RecentBoop) -> Unit,
    onResetToReceive: () -> Unit,
    onDismissPayload: () -> Unit,
    onDismissError: () -> Unit,
    onDismissNfcWarning: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onDismissHotspotWarning: () -> Unit,
    onApproveTransfer: () -> Unit,
    onRejectTransfer: () -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onReceivePermissionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalHapticsEnabled provides settingsState.vibrationEnabled) {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only hide bottom nav for modal overlays (NFC guide dialog)
    val hideBottomNav = currentRoute == BoopRoute.NfcGuide.route

    // ── Auto-navigation: transfer progress ───────────────────────────────
    LaunchedEffect(transferUiState.isTransferring) {
        if (transferUiState.isTransferring && currentRoute != BoopRoute.TransferProgress.route) {
            Log.d(TAG, "Auto-navigating to TransferProgress")
            navController.navigate(BoopRoute.TransferProgress.route) {
                launchSingleTop = true
            }
        }
    }

    // Auto-navigate back when transfer completes
    LaunchedEffect(transferUiState.transferComplete) {
        if (transferUiState.transferComplete && currentRoute == BoopRoute.TransferProgress.route) {
            Log.d(TAG, "Transfer complete — navigating back after delay")
            kotlinx.coroutines.delay(1000)
            navController.popBackStack()
            onResetToReceive()
        }
    }

    // ── Auto-navigation: NFC guide (first time) ─────────────────────────
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val nfcActive = transferUiState.isNfcBroadcasting || transferUiState.isNfcReading

    LaunchedEffect(nfcActive) {
        if (nfcActive && !prefs.getBoolean(KEY_ANTENNA_GUIDE_SEEN, false)) {
            Log.d(TAG, "First NFC activation — auto-showing NFC guide")
            prefs.edit().putBoolean(KEY_ANTENNA_GUIDE_SEEN, true).apply()
            navController.navigate(BoopRoute.NfcGuide.route) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomNav,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                BoopBottomNavBar(
                    currentRoute = currentRoute,
                    onItemClick = { item ->
                        Log.d(TAG, "Bottom nav: ${item.route}")
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        BoopNavHost(
            navController = navController,
            transferUiState = transferUiState,
            settingsState = settingsState,
            permissionsGranted = permissionsGranted,
            onSendClick = onSendClick,
            onResetClick = onResetClick,
            onResendBoop = onResendBoop,
            onNotificationsToggle = onNotificationsToggle,
            onVibrationToggle = onVibrationToggle,
            onSoundToggle = onSoundToggle,
            onDisplayNameChange = onDisplayNameChange,
            onDarkModeToggle = onDarkModeToggle,
            onReceivePermissionChange = onReceivePermissionChange,
            modifier = Modifier.padding(innerPadding)
        )
    }

    // ── NFC Payload BottomSheet (with optional approval buttons) ─────────
    val pendingApproval = transferUiState.pendingApproval
    val receivedPayload = transferUiState.receivedPayload
    if (pendingApproval != null) {
        NfcPayloadBottomSheet(
            payload = pendingApproval,
            showApprovalButtons = true,
            onApprove = onApproveTransfer,
            onReject = onRejectTransfer,
            onDismiss = onRejectTransfer
        )
    } else if (receivedPayload != null) {
        NfcPayloadBottomSheet(
            payload = receivedPayload,
            showApprovalButtons = false,
            onApprove = {},
            onReject = {},
            onDismiss = onDismissPayload
        )
    }

    // ── Error Dialog ────────────────────────────────────────────────────
    transferUiState.error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = onDismissError,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Error",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(text = errorMessage)
            },
            confirmButton = {
                TextButton(onClick = onDismissError) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── NFC Disabled Warning Dialog ──────────────────────────────────────
    if (transferUiState.nfcDisabledWarning) {
        AlertDialog(
            onDismissRequest = onDismissNfcWarning,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "NFC is Disabled",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(text = "Boop requires NFC to discover nearby devices. Please enable NFC in your device settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissNfcWarning()
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNfcWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Wi-Fi Disabled Warning Dialog ────────────────────────────────────
    if (transferUiState.wifiDisabledWarning) {
        AlertDialog(
            onDismissRequest = onDismissWifiWarning,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Wi-Fi is Disabled",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(text = "Boop needs Wi-Fi Direct for file transfers. If your hotspot is on, please turn it off.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissWifiWarning()
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissWifiWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Hotspot Enabled Warning Dialog ──────────────────────────────────
    if (transferUiState.hotspotWarning) {
        AlertDialog(
            onDismissRequest = onDismissHotspotWarning,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Hotspot is On",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(text = "Wi-Fi hotspot can interfere with Wi-Fi Direct file transfers. Please turn off your hotspot before using Boop.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissHotspotWarning()
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }) {
                    Text("Turn Off Hotspot", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissHotspotWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    } // CompositionLocalProvider
}

// ─── NFC Payload BottomSheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcPayloadBottomSheet(
    payload: ConnectionDetails,
    showApprovalButtons: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Extract device name from SSID for display
    val deviceName = Regex("^DIRECT-[a-zA-Z0-9]{2}-(.+)$").find(payload.ssid)?.groupValues?.get(1) ?: payload.ssid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = if (showApprovalButtons) "Transfer from $deviceName" else "NFC Payload Received",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = buildPayloadJsonString(payload),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            PayloadDetailRow(label = "SSID", value = payload.ssid)
            PayloadDetailRow(label = "Token", value = payload.token)
            PayloadDetailRow(label = "MAC", value = payload.mac)
            PayloadDetailRow(label = "Port", value = payload.port.toString())
            if (payload.fileCount > 1) {
                PayloadDetailRow(label = "Files", value = payload.fileCount.toString())
            }

            if (showApprovalButtons) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Reject",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    NeoBrutalistButton(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Accept",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayloadDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifEmpty { "\u2014" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun buildPayloadJsonString(payload: ConnectionDetails): String {
    return org.json.JSONObject().apply {
        put("ssid", payload.ssid)
        put("token", payload.token)
        if (payload.fileCount > 1) put("fileCount", payload.fileCount)
    }.toString(2)
}
