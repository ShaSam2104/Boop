package com.shashsam.boop.ui.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shashsam.boop.data.FriendEntity
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.nfc.ConnectionDetails
import com.shashsam.boop.transfer.ProfileData
import com.shashsam.boop.ui.theme.BoopBottomNavBar
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.NeoBrutalistButton
import com.shashsam.boop.ui.viewmodels.BackupUiState
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
    backupState: BackupUiState,
    friends: List<FriendEntity>,
    profileItems: List<ProfileItemEntity>,
    profilePicPath: String?,
    selectedFriend: FriendEntity?,
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
    onApproveAndBefriend: () -> Unit,
    onRejectTransfer: () -> Unit,
    onAcceptFriendRequest: () -> Unit,
    onRejectFriendRequest: () -> Unit,
    onSaveReceivedProfile: () -> Unit,
    onDismissReceivedProfile: () -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onReceivePermissionChange: (String) -> Unit,
    onExportData: (android.net.Uri, String) -> Unit,
    onImportData: (android.net.Uri, String) -> Unit,
    onDismissBackupMessage: () -> Unit,
    onProfilePicPick: (android.net.Uri) -> Unit,
    onAddProfileItem: (String, String, String, String) -> Unit,
    onEditProfileItem: (ProfileItemEntity) -> Unit,
    onDeleteProfileItem: (Long) -> Unit,
    onReorderProfileItems: (List<ProfileItemEntity>) -> Unit,
    onFriendClick: (FriendEntity) -> Unit,
    onSelectFriend: (Long) -> Unit,
    onRemoveFriend: (Long) -> Unit,
    onShareProfileClick: () -> Unit,
    onCancelProfileShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalHapticsEnabled provides settingsState.vibrationEnabled) {

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom nav for modal overlays and overlay screens
    val hideBottomNav = currentRoute == BoopRoute.NfcGuide.route
        || currentRoute == BoopRoute.TransferProgress.route
        || currentRoute == BoopRoute.Settings.route
        || currentRoute == BoopRoute.FriendProfile.route
        || currentRoute?.startsWith("friend_history") == true

    // ── Auto-navigation: transfer progress ───────────────────────────────
    LaunchedEffect(transferUiState.isTransferring) {
        if (transferUiState.isTransferring && currentRoute != BoopRoute.TransferProgress.route) {
            Log.d(TAG, "Auto-navigating to TransferProgress")
            navController.navigate(BoopRoute.TransferProgress.route) {
                popUpTo(BoopRoute.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    // Auto-navigate back when transfer completes, then fully reset state
    LaunchedEffect(transferUiState.transferComplete) {
        if (transferUiState.transferComplete && currentRoute == BoopRoute.TransferProgress.route) {
            Log.d(TAG, "Transfer complete — navigating back after delay, then resetting")
            kotlinx.coroutines.delay(1000)
            navController.popBackStack(BoopRoute.Home.route, inclusive = false)
            // Reset state AFTER navigation so the TransferProgress screen doesn't
            // flash "Unknown File" from a wiped state.
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
            backupState = backupState,
            friends = friends,
            profileItems = profileItems,
            profilePicPath = profilePicPath,
            selectedFriend = selectedFriend,
            permissionsGranted = permissionsGranted,
            onSendClick = onSendClick,
            onResetClick = onResetClick,
            onResendBoop = onResendBoop,
            onNotificationsToggle = onNotificationsToggle,
            onVibrationToggle = onVibrationToggle,
            onDisplayNameChange = onDisplayNameChange,
            onDarkModeToggle = onDarkModeToggle,
            onReceivePermissionChange = onReceivePermissionChange,
            onExportData = onExportData,
            onImportData = onImportData,
            onDismissBackupMessage = onDismissBackupMessage,
            onProfilePicPick = onProfilePicPick,
            onAddProfileItem = onAddProfileItem,
            onEditProfileItem = onEditProfileItem,
            onDeleteProfileItem = onDeleteProfileItem,
            onReorderProfileItems = onReorderProfileItems,
            onFriendClick = onFriendClick,
            onSelectFriend = onSelectFriend,
            onRemoveFriend = onRemoveFriend,
            onShareProfileClick = onShareProfileClick,
            onCancelProfileShare = onCancelProfileShare,
            modifier = Modifier.padding(innerPadding)
        )
    }

    // ── Auto-dismiss non-approval NFC payload ──────────────────────────
    LaunchedEffect(transferUiState.receivedPayload, transferUiState.pendingApproval) {
        if (transferUiState.receivedPayload != null && transferUiState.pendingApproval == null) {
            onDismissPayload()
        }
    }

    // ── Transfer Approval BottomSheet ───────────────────────────────
    val pendingApproval = transferUiState.pendingApproval
    if (pendingApproval != null) {
        TransferApprovalBottomSheet(
            payload = pendingApproval,
            onApprove = onApproveTransfer,
            onApproveAndBefriend = onApproveAndBefriend,
            onReject = onRejectTransfer,
            onDismiss = onRejectTransfer
        )
    }

    // ── Friend Request Dialog (sender sees after file transfer) ──────
    if (transferUiState.pendingFriendRequest != null) {
        val friendName = transferUiState.pendingFriendRequest.displayName.takeIf { it.isNotBlank() } ?: "Someone"
        AlertDialog(
            onDismissRequest = onRejectFriendRequest,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = LocalBoopTokens.current.accent,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Friend Request",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "$friendName wants to become friends",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                NeoBrutalistButton(onClick = onAcceptFriendRequest) {
                    Text("Accept", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onRejectFriendRequest) {
                    Text("Decline", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Profile Received Dialog (NFC profile share) ──────────────────
    if (transferUiState.receivedProfile != null) {
        val profile = transferUiState.receivedProfile
        val profileName = profile.displayName.takeIf { it.isNotBlank() } ?: "Unknown"
        AlertDialog(
            onDismissRequest = onDismissReceivedProfile,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = LocalBoopTokens.current.accent,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Profile Received",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "Received profile from $profileName",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                NeoBrutalistButton(onClick = onSaveReceivedProfile) {
                    Text("Save as Friend", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReceivedProfile) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Error Dialog ────────────────────────────────────────────────────
    transferUiState.error?.let { errorMessage ->
        val dismissAndNavigate = {
            // Navigate back to Home if we're on TransferProgress, then dismiss error
            if (currentRoute == BoopRoute.TransferProgress.route) {
                navController.popBackStack(BoopRoute.Home.route, inclusive = false)
            }
            onDismissError()
        }
        AlertDialog(
            onDismissRequest = dismissAndNavigate,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Something went wrong",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = dismissAndNavigate) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    // ── NFC Disabled Warning Dialog ──────────────────────────────────────
    if (transferUiState.nfcDisabledWarning) {
        AlertDialog(
            onDismissRequest = onDismissNfcWarning,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "NFC is Disabled",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "Boop requires NFC to discover nearby devices. Please enable NFC in your device settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissNfcWarning()
                    context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }) {
                    Text("Open Settings", fontWeight = FontWeight.Bold, color = LocalBoopTokens.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissNfcWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Wi-Fi Disabled Warning Dialog ────────────────────────────────────
    if (transferUiState.wifiDisabledWarning) {
        AlertDialog(
            onDismissRequest = onDismissWifiWarning,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = com.shashsam.boop.ui.theme.WarningAmber,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Wi-Fi is Disabled",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "Boop needs Wi-Fi Direct for file transfers. If your hotspot is on, please turn it off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissWifiWarning()
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }) {
                    Text("Open Settings", fontWeight = FontWeight.Bold, color = LocalBoopTokens.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissWifiWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Hotspot Enabled Warning Dialog ──────────────────────────────────
    if (transferUiState.hotspotWarning) {
        AlertDialog(
            onDismissRequest = onDismissHotspotWarning,
            shape = BoopShapeMedium,
            containerColor = LocalBoopTokens.current.dialogSurface,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = com.shashsam.boop.ui.theme.WarningAmber,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Hotspot is On",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "Wi-Fi hotspot can interfere with Wi-Fi Direct transfers. Please turn off your hotspot before using Boop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissHotspotWarning()
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }) {
                    Text("Turn Off Hotspot", fontWeight = FontWeight.Bold, color = LocalBoopTokens.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissHotspotWarning) {
                    Text("Dismiss", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    } // CompositionLocalProvider
}

// ─── Transfer Approval BottomSheet ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferApprovalBottomSheet(
    payload: ConnectionDetails,
    onApprove: () -> Unit,
    onApproveAndBefriend: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tokens = LocalBoopTokens.current

    // Prefer sender's display name, fall back to SSID-derived device name
    val deviceName = payload.displayName.takeIf { it.isNotBlank() }
        ?: Regex("^DIRECT-[a-zA-Z0-9]{2}-(.+)$").find(payload.ssid)?.groupValues?.get(1)
        ?: payload.ssid

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalBoopTokens.current.dialogSurface,
        dragHandle = {
            // Subtle drag handle
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device name prominently displayed
            Text(
                text = deviceName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (payload.fileCount > 1)
                    "wants to send you ${payload.fileCount} files"
                else
                    "wants to send you a file",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Accept button — primary action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp, bottom = 6.dp)
            ) {
                NeoBrutalistButton(
                    onClick = onApprove
                ) {
                    Text(
                        text = "Accept",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Accept + Become Friends button — secondary action with icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 6.dp, bottom = 6.dp)
            ) {
                NeoBrutalistButton(
                    onClick = onApproveAndBefriend
                ) {
                    Icon(
                        imageVector = Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Accept + Become Friends",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Reject button — tertiary/destructive
            TextButton(
                onClick = onReject,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Reject",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
