package com.shashsam.boop

import android.content.Intent
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shashsam.boop.nfc.NfcReader
import com.shashsam.boop.ui.screens.HomeScreen
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.viewmodels.TransferViewModel
import com.shashsam.boop.utils.rememberFilePicker
import com.shashsam.boop.utils.rememberPermissionLauncher
import com.shashsam.boop.utils.rememberPermissionsState
import com.shashsam.boop.utils.requiredPermissions

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: TransferViewModel by viewModels()

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
        } else {
            Log.d(TAG, "NFC adapter ready")
            viewModel.appendLog("📡 NFC adapter ready.")
        }

        enableEdgeToEdge()

        setContent {
            BoopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    var permissionsGranted by rememberPermissionsState()

                    // ── File picker ──────────────────────────────────────────
                    val filePicker = rememberFilePicker { metadata ->
                        if (metadata != null) {
                            Log.d(TAG, "File picked: ${metadata.name} (${metadata.formattedSize})")
                            viewModel.appendLog("📄 Selected: ${metadata.name} (${metadata.formattedSize})")
                            viewModel.startSending(metadata.uri)
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
                    if (!permissionsGranted) {
                        Log.d(TAG, "Permissions not yet granted — requesting…")
                        permissionLauncher.launch(requiredPermissions())
                    }

                    HomeScreen(
                        permissionsGranted = permissionsGranted,
                        transferUiState = uiState,
                        onSendClick = {
                            Log.d(TAG, "onSendClick")
                            if (!permissionsGranted) {
                                viewModel.appendLog(
                                    "⚠️ Permissions required before sending.",
                                    isError = true
                                )
                                permissionLauncher.launch(requiredPermissions())
                            } else {
                                viewModel.prepareSend()
                                filePicker.launch(arrayOf("*/*"))
                            }
                        },
                        onReceiveClick = {
                            Log.d(TAG, "onReceiveClick")
                            if (!permissionsGranted) {
                                viewModel.appendLog(
                                    "⚠️ Permissions required before receiving.",
                                    isError = true
                                )
                                permissionLauncher.launch(requiredPermissions())
                            } else {
                                viewModel.prepareReceive()
                                nfcReader.enableReaderMode(this@MainActivity)
                            }
                        },
                        onResetClick = {
                            Log.d(TAG, "onResetClick")
                            viewModel.reset()
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

        // Enable NFC foreground dispatch for NDEF-discovered intents
        nfcReader.enableForegroundDispatch(this)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        viewModel.wifiDirectManager.unregister()
        nfcReader.disableForegroundDispatch(this)
        nfcReader.disableReaderMode(this)
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
    }
}
