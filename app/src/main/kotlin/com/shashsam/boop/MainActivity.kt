package com.shashsam.boop

import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shashsam.boop.nfc.NfcReader
import com.shashsam.boop.nfc.NfcReaderState
import com.shashsam.boop.ui.navigation.BoopScaffold
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.viewmodels.BackupViewModel
import com.shashsam.boop.ui.viewmodels.ProfileViewModel
import com.shashsam.boop.ui.viewmodels.SettingsViewModel
import com.shashsam.boop.ui.viewmodels.TransferViewModel
import com.shashsam.boop.utils.rememberMultiFilePicker
import com.shashsam.boop.utils.rememberPermissionLauncher
import com.shashsam.boop.utils.rememberPermissionsState
import com.shashsam.boop.utils.requiredPermissions

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()

    /** Wraps the NFC adapter for reader mode and foreground dispatch. */
    private lateinit var nfcReader: NfcReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        nfcReader = NfcReader(NfcAdapter.getDefaultAdapter(this))
        if (!nfcReader.isAvailable) {
            Log.w(TAG, "NFC not available on this device")
            viewModel.appendLog("⚠️ NFC is not available on this device.")
        } else if (!nfcReader.isEnabled) {
            Log.w(TAG, "NFC is disabled")
            viewModel.appendLog("⚠️ NFC is disabled. Enable it in Settings for full functionality.")
            viewModel.setNfcWarning(true)
        } else {
            Log.d(TAG, "NFC adapter ready")
            viewModel.appendLog("📡 NFC adapter ready.")
            // Handle NFC payload from cold-start launch intent
            nfcReader.parseIntent(intent)?.let { details ->
                Log.d(TAG, "NFC payload from launch intent: $details")
                viewModel.onNfcPayloadReceived(details)
            }
        }

        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wifiManager != null && !wifiManager.isWifiEnabled) {
            Log.w(TAG, "Wi-Fi is disabled")
            viewModel.setWifiWarning(true)
        }

        if (wifiManager != null && isHotspotEnabled(wifiManager)) {
            Log.w(TAG, "Hotspot is enabled — may interfere with Wi-Fi Direct")
            viewModel.setHotspotWarning(true)
        }

        // Handle share sheet intents (ACTION_SEND / ACTION_SEND_MULTIPLE)
        handleShareIntent(intent)

        enableEdgeToEdge()

        setContent {
            val settingsState by settingsViewModel.uiState.collectAsState()

            BoopTheme(darkTheme = settingsState.darkModeEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val backupState by backupViewModel.uiState.collectAsState()
                    val friends by viewModel.friends.collectAsState()
                    val profileItems by profileViewModel.profileItems.collectAsState()
                    val profilePicPath by profileViewModel.profilePicPath.collectAsState()
                    val selectedFriend by viewModel.selectedFriend.collectAsState()
                    var permissionsGranted by rememberPermissionsState()

                    // ── File picker (multi-file) ─────────────────────────────
                    val filePicker = rememberMultiFilePicker { metadataList ->
                        if (metadataList.isNotEmpty()) {
                            Log.d(TAG, "Files picked: ${metadataList.size}")
                            viewModel.prepareSend()
                            if (metadataList.size == 1) {
                                val m = metadataList.first()
                                viewModel.appendLog("📄 Selected: ${m.name} (${m.formattedSize})")
                                viewModel.startSending(m.uri)
                            } else {
                                viewModel.appendLog("📄 Selected ${metadataList.size} files")
                                viewModel.startSendingMultiple(metadataList.map { it.uri })
                            }
                        } else {
                            Log.d(TAG, "File picker cancelled")
                            viewModel.appendLog("ℹ️ File selection cancelled.")
                        }
                    }

                    // ── Permission launcher ──────────────────────────────────
                    val permissionLauncher = rememberPermissionLauncher { allGranted ->
                        Log.d(TAG, "Permission result: allGranted=$allGranted")
                        permissionsGranted = allGranted
                        if (allGranted) {
                            viewModel.appendLog("✅ All permissions granted. Systems ready.")
                        } else {
                            viewModel.appendLog(
                                "⚠️ Some permissions were denied. Please grant all permissions.",
                                isError = true
                            )
                        }
                    }

                    // Request permissions on first launch if not already granted
                    LaunchedEffect(permissionsGranted) {
                        if (!permissionsGranted) {
                            Log.d(TAG, "Permissions not yet granted — requesting…")
                            permissionLauncher.launch(requiredPermissions())
                        }
                    }

                    // ── Observe NFC Reader Mode state ──────────────────────
                    LaunchedEffect(Unit) {
                        nfcReader.state.collect { readerState ->
                            when (readerState) {
                                is NfcReaderState.Connected -> {
                                    Log.d(TAG, "NFC reader mode payload: ${readerState.details}")
                                    nfcReader.reset()
                                    nfcReader.disableReaderMode(this@MainActivity)
                                    viewModel.onNfcPayloadReceived(readerState.details)
                                }
                                is NfcReaderState.Error -> {
                                    Log.w(TAG, "NFC reader mode error: ${readerState.message}")
                                    viewModel.appendLog("❌ NFC: ${readerState.message}", isError = true)
                                }
                                else -> { /* Idle / Reading — no-op */ }
                            }
                        }
                    }

                    // ── Auto-toggle NFC reader mode reactively ──────────────
                    LaunchedEffect(uiState.isNfcReading, permissionsGranted) {
                        if (uiState.isNfcReading && permissionsGranted) {
                            nfcReader.enableReaderMode(this@MainActivity)
                        } else {
                            nfcReader.disableReaderMode(this@MainActivity)
                        }
                    }

                    BoopScaffold(
                        transferUiState = uiState,
                        settingsState = settingsState,
                        backupState = backupState,
                        friends = friends,
                        profileItems = profileItems,
                        profilePicPath = profilePicPath,
                        selectedFriend = selectedFriend,
                        permissionsGranted = permissionsGranted,
                        onSendClick = {
                            Log.d(TAG, "onSendClick")
                            if (!permissionsGranted) {
                                viewModel.appendLog(
                                    "⚠️ Permissions required before sending.",
                                    isError = true
                                )
                                permissionLauncher.launch(requiredPermissions())
                            } else {
                                filePicker.launch(arrayOf("*/*"))
                            }
                        },
                        onResetClick = {
                            Log.d(TAG, "onResetClick")
                            viewModel.reset()
                        },
                        onResetToReceive = {
                            Log.d(TAG, "onResetToReceive")
                            viewModel.resetToReceive()
                        },
                        onResendBoop = { boop ->
                            Log.d(TAG, "onResendBoop: ${boop.fileName} uri=${boop.fileUri}")
                            boop.fileUri?.let { uri ->
                                viewModel.prepareSend()
                                viewModel.startSending(uri)
                            }
                        },
                        onDismissPayload = {
                            viewModel.dismissPayloadSheet()
                        },
                        onDismissError = {
                            viewModel.dismissError()
                        },
                        onDismissNfcWarning = {
                            viewModel.dismissNfcWarning()
                        },
                        onDismissWifiWarning = {
                            viewModel.dismissWifiWarning()
                        },
                        onDismissHotspotWarning = {
                            viewModel.dismissHotspotWarning()
                        },
                        onApproveTransfer = {
                            Log.d(TAG, "onApproveTransfer")
                            viewModel.approveIncomingTransfer(becomeFriends = false)
                        },
                        onApproveAndBefriend = {
                            Log.d(TAG, "onApproveAndBefriend")
                            viewModel.approveIncomingTransfer(becomeFriends = true)
                        },
                        onRejectTransfer = {
                            Log.d(TAG, "onRejectTransfer")
                            viewModel.rejectIncomingTransfer()
                        },
                        onAcceptFriendRequest = {
                            Log.d(TAG, "onAcceptFriendRequest")
                            viewModel.acceptFriendRequest()
                        },
                        onRejectFriendRequest = {
                            Log.d(TAG, "onRejectFriendRequest")
                            viewModel.rejectFriendRequest()
                        },
                        onSaveReceivedProfile = {
                            Log.d(TAG, "onSaveReceivedProfile")
                            viewModel.saveReceivedProfileAsFriend()
                        },
                        onDismissReceivedProfile = {
                            viewModel.dismissReceivedProfile()
                        },
                        onNotificationsToggle = settingsViewModel::setNotificationsEnabled,
                        onVibrationToggle = settingsViewModel::setVibrationEnabled,
                        onDisplayNameChange = settingsViewModel::setDisplayName,
                        onDarkModeToggle = settingsViewModel::setDarkMode,
                        onReceivePermissionChange = settingsViewModel::setReceivePermission,
                        onExportData = { uri, password ->
                            backupViewModel.exportData(uri, password)
                        },
                        onImportData = { uri, password ->
                            backupViewModel.importData(uri, password)
                        },
                        onDismissBackupMessage = {
                            backupViewModel.dismissMessage()
                        },
                        onProfilePicPick = { uri ->
                            profileViewModel.setProfilePic(uri)
                        },
                        onAddProfileItem = { type, label, value, size ->
                            profileViewModel.addProfileItem(type, label, value, size)
                        },
                        onEditProfileItem = { item ->
                            profileViewModel.updateProfileItem(item)
                        },
                        onDeleteProfileItem = { id ->
                            profileViewModel.deleteProfileItem(id)
                        },
                        onReorderProfileItems = { items ->
                            profileViewModel.reorderProfileItems(items)
                        },
                        onFriendClick = { friend ->
                            viewModel.selectFriend(friend.id)
                        },
                        onSelectFriend = { id ->
                            viewModel.selectFriend(id)
                        },
                        onRemoveFriend = { id ->
                            viewModel.removeFriend(id)
                        },
                        onShareProfileClick = {
                            Log.d(TAG, "onShareProfileClick")
                            val profileData = com.shashsam.boop.transfer.ProfileData(
                                ulid = com.shashsam.boop.utils.getOrCreateUlid(this@MainActivity),
                                displayName = settingsState.displayName,
                                profileItemsJson = profileViewModel.buildProfileJson(),
                                profilePicBytes = profileViewModel.getProfilePicFile()?.readBytes()
                            )
                            viewModel.prepareProfileShare(profileData)
                        },
                        onCancelProfileShare = {
                            viewModel.cancelProfileShare()
                        },
                        onReshowWarnings = {
                            viewModel.reshowWarnings()
                        },
                        onBioChange = { bio ->
                            settingsViewModel.setBio(bio)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        viewModel.wifiDirectManager.register()
        nfcReader.enableForegroundDispatch(this)

        // Re-check NFC/Wi-Fi state on resume (user may have toggled in system settings)
        if (nfcReader.isAvailable) {
            val nfcDisabled = !nfcReader.isEnabled
            viewModel.setNfcWarning(nfcDisabled)
            // If issue resolved, clear dismissed flag so it can re-trigger next time
            if (!nfcDisabled) viewModel.clearNfcWarningDismissed()
        }
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
        if (wifiManager != null) {
            val wifiDisabled = !wifiManager.isWifiEnabled
            viewModel.setWifiWarning(wifiDisabled)
            if (!wifiDisabled) viewModel.clearWifiWarningDismissed()

            val hotspotOn = isHotspotEnabled(wifiManager)
            viewModel.setHotspotWarning(hotspotOn)
            if (!hotspotOn) viewModel.clearHotspotWarningDismissed()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        viewModel.wifiDirectManager.unregister()
        nfcReader.disableForegroundDispatch(this)
        nfcReader.disableReaderMode(this)
    }

    /**
     * Handles incoming share intents (ACTION_SEND / ACTION_SEND_MULTIPLE).
     * Extracts URIs and starts the send flow.
     */
    @Suppress("DEPRECATION")
    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    Log.d(TAG, "ACTION_SEND received: $uri")
                    viewModel.prepareSend()
                    viewModel.startSending(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    Log.d(TAG, "ACTION_SEND_MULTIPLE received: ${uris.size} files")
                    viewModel.prepareSend()
                    viewModel.startSendingMultiple(uris)
                }
            }
        }
    }

    /**
     * Checks whether the Wi-Fi hotspot (tethering) is currently enabled
     * via reflection on [WifiManager.isWifiApEnabled]. Returns false if
     * the method is not available on this device.
     */
    private fun isHotspotEnabled(wifiManager: WifiManager): Boolean {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as? Boolean ?: false
        } catch (e: Exception) {
            Log.d(TAG, "Could not check hotspot state via reflection", e)
            false
        }
    }

    /**
     * Handles foreground-dispatch NFC intents and deep-link intents that arrive
     * while the activity is already in the foreground.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: action=${intent.action}")
        val details = nfcReader.parseIntent(intent)
        if (details != null) {
            Log.d(TAG, "NFC payload via onNewIntent: $details")
            viewModel.onNfcPayloadReceived(details)
        }
        // Handle share sheet intents arriving while activity is running
        handleShareIntent(intent)
    }
}
