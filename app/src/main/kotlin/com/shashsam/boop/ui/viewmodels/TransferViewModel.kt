package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shashsam.boop.nfc.BoopHceService
import com.shashsam.boop.nfc.ConnectionDetails
import com.shashsam.boop.transfer.TransferManager
import com.shashsam.boop.ui.screens.LogEntry
import com.shashsam.boop.utils.toFormattedSize
import com.shashsam.boop.wifi.GROUP_OWNER_IP
import com.shashsam.boop.wifi.WifiDirectManager
import com.shashsam.boop.wifi.WifiDirectState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TransferViewModel"

/**
 * Aggregate UI state consumed by
 * [HomeScreen][com.shashsam.boop.ui.screens.HomeScreen].
 *
 * @param statusLog          Ordered list of activity log entries.
 * @param transferProgress   Transfer progress in [0, 1]; used by [LinearProgressIndicator][androidx.compose.material3.LinearProgressIndicator].
 * @param isTransferring     `true` while a file transfer is in progress.
 * @param isSendMode         `true` when the user has activated send mode.
 * @param isReceiveMode      `true` when the user has activated receive mode.
 * @param isNfcBroadcasting  `true` while the HCE service is broadcasting.
 * @param isNfcReading       `true` while the NFC reader is waiting for a tap.
 * @param isWifiConnecting   `true` while a Wi-Fi Direct connection is being established.
 * @param transferComplete   `true` after a transfer finishes successfully.
 * @param savedFileUri       URI of the received file (Receiver side, post-completion).
 * @param error              Non-null when a terminal error has occurred.
 */
data class TransferUiState(
    val statusLog: List<LogEntry> = emptyList(),
    val transferProgress: Float = 0f,
    val isTransferring: Boolean = false,
    val isSendMode: Boolean = false,
    val isReceiveMode: Boolean = false,
    val isNfcBroadcasting: Boolean = false,
    val isNfcReading: Boolean = false,
    val isWifiConnecting: Boolean = false,
    val transferComplete: Boolean = false,
    val savedFileUri: Uri? = null,
    val error: String? = null
)

/**
 * ViewModel that owns the complete Boop transfer pipeline:
 * **NFC tap → Wi-Fi Direct connect → TCP socket file transfer**.
 *
 * All background work is scoped to [viewModelScope] so it is automatically
 * cancelled when the screen is dismissed.
 *
 * ### Sender flow
 * 1. Call [prepareSend] when "Send File" is tapped — creates the Wi-Fi Direct group
 *    and enables the HCE broadcast.
 * 2. Call [startSending] with the file URI once the user has picked a file.
 *
 * ### Receiver flow
 * 1. Call [prepareReceive] when "Receive File" is tapped — activates NFC reading.
 * 2. When the NFC reader delivers a payload, call [onNfcPayloadReceived] — this
 *    triggers the Wi-Fi Direct connection and starts the TCP receive.
 */
class TransferViewModel(application: Application) : AndroidViewModel(application) {

    /** Exposed so [MainActivity][com.shashsam.boop.MainActivity] can call register/unregister. */
    val wifiDirectManager = WifiDirectManager(application)

    private val _uiState = MutableStateFlow(TransferUiState())

    /** Observable UI state for the home screen. */
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private var transferJob: Job? = null

    init {
        wifiDirectManager.initialize()
        observeWifiDirectState()
    }

    // ─── Wi-Fi Direct observer ────────────────────────────────────────────────

    private fun observeWifiDirectState() {
        viewModelScope.launch {
            wifiDirectManager.state.collect { wifiState ->
                Log.d(TAG, "WifiDirectState: $wifiState")
                when (wifiState) {
                    is WifiDirectState.Idle -> { /* no-op */ }

                    is WifiDirectState.CreatingGroup -> {
                        appendLog("📶 Creating Wi-Fi Direct group…")
                        _uiState.update { it.copy(isWifiConnecting = true) }
                    }

                    is WifiDirectState.GroupCreated -> {
                        appendLog("✅ Wi-Fi Direct group ready (MAC: ${wifiState.deviceMac})")
                        // Publish MAC address for HCE so NFC broadcasts the correct address.
                        BoopHceService.connectionMac = wifiState.deviceMac
                        BoopHceService.connectionPort = BoopHceService.DEFAULT_PORT
                        _uiState.update {
                            it.copy(isWifiConnecting = false, isNfcBroadcasting = true)
                        }
                        appendLog("📡 Broadcasting via NFC — hold phones together to share!")
                    }

                    is WifiDirectState.Connecting -> {
                        appendLog("🔗 Connecting to Sender via Wi-Fi Direct…")
                        _uiState.update { it.copy(isWifiConnecting = true) }
                    }

                    is WifiDirectState.Connected -> {
                        appendLog("✅ Wi-Fi Direct connected (GO: ${wifiState.groupOwnerAddress})")
                        _uiState.update { it.copy(isWifiConnecting = false) }
                    }

                    is WifiDirectState.Disconnected -> {
                        appendLog("🔌 Wi-Fi Direct disconnected.")
                        _uiState.update { it.copy(isWifiConnecting = false) }
                    }

                    is WifiDirectState.Error -> {
                        appendLog("❌ Wi-Fi Direct error: ${wifiState.message}", isError = true)
                        _uiState.update {
                            it.copy(isWifiConnecting = false, error = wifiState.message)
                        }
                    }
                }
            }
        }
    }

    // ─── Send flow ────────────────────────────────────────────────────────────

    /**
     * Activates send mode: creates a Wi-Fi Direct group so this device becomes the
     * Group Owner, and begins HCE broadcasting.
     *
     * Call this when the user taps "Send File", *before* the file picker is shown.
     */
    fun prepareSend() {
        Log.d(TAG, "prepareSend()")
        _uiState.update {
            it.copy(isSendMode = true, isReceiveMode = false, error = null, transferComplete = false)
        }
        appendLog("📤 Send mode activated.")
        viewModelScope.launch {
            val ok = wifiDirectManager.createGroup()
            if (!ok) {
                appendLog("❌ Failed to create Wi-Fi Direct group.", isError = true)
            }
        }
    }

    /**
     * Starts sending [fileUri] to the connected Receiver over TCP.
     *
     * This opens a [ServerSocket][java.net.ServerSocket] and waits for the Receiver
     * to connect after the Wi-Fi Direct handshake.
     *
     * @param fileUri URI returned by the file picker (MediaStore or SAF).
     */
    fun startSending(fileUri: Uri) {
        Log.d(TAG, "startSending: $fileUri")
        val context = getApplication<Application>()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            appendLog("🚀 Starting file transfer…")
            _uiState.update {
                it.copy(isTransferring = true, transferProgress = 0f, transferComplete = false)
            }
            TransferManager.sendFile(context, fileUri, BoopHceService.DEFAULT_PORT)
                .collect { progress -> handleTransferProgress(progress) }
        }
    }

    // ─── Receive flow ─────────────────────────────────────────────────────────

    /**
     * Activates receive mode: signals the UI to enable NFC reading.
     *
     * The NFC reader itself is managed by
     * [MainActivity][com.shashsam.boop.MainActivity] because it requires an
     * [Activity][android.app.Activity] reference for foreground dispatch.
     */
    fun prepareReceive() {
        Log.d(TAG, "prepareReceive()")
        _uiState.update {
            it.copy(
                isReceiveMode = true,
                isSendMode = false,
                isNfcReading = true,
                error = null,
                transferComplete = false
            )
        }
        appendLog("📥 Receive mode activated. Hold phones together…")
    }

    /**
     * Called by [MainActivity][com.shashsam.boop.MainActivity] when the NFC reader
     * successfully parses the Sender's NDEF payload.
     *
     * Triggers the Wi-Fi Direct connection and — once connected — starts the TCP
     * file receive.
     *
     * @param details Parsed [ConnectionDetails] from the NFC tap.
     */
    fun onNfcPayloadReceived(details: ConnectionDetails) {
        Log.d(TAG, "onNfcPayloadReceived: mac=${details.mac} port=${details.port}")
        appendLog("📲 NFC tap! Sender MAC=${details.mac} Port=${details.port}")
        _uiState.update { it.copy(isNfcReading = false) }

        val context = getApplication<Application>()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            // 1. Initiate Wi-Fi Direct connection to the Sender
            val connected = wifiDirectManager.connect(details.mac)
            if (!connected) {
                appendLog("❌ Wi-Fi Direct connection failed.", isError = true)
                return@launch
            }

            // 2. Wait for WIFI_P2P_CONNECTION_CHANGED to deliver Connected state.
            appendLog("⏳ Establishing Wi-Fi Direct link…")

            // 3. Open TCP receive on the Group Owner IP
            appendLog("🚀 Starting file receive (${GROUP_OWNER_IP}:${details.port})…")
            _uiState.update {
                it.copy(isTransferring = true, transferProgress = 0f, transferComplete = false)
            }
            TransferManager.receiveFile(context, GROUP_OWNER_IP, details.port)
                .collect { progress -> handleTransferProgress(progress) }
        }
    }

    // ─── Common ───────────────────────────────────────────────────────────────

    private fun handleTransferProgress(progress: com.shashsam.boop.transfer.TransferProgress) {
        when {
            progress.error != null -> {
                Log.e(TAG, "Transfer error: ${progress.error}")
                appendLog("❌ Transfer error: ${progress.error}", isError = true)
                _uiState.update {
                    it.copy(isTransferring = false, error = progress.error)
                }
            }

            progress.isComplete -> {
                val sizeStr = progress.totalBytes.toFormattedSize()
                Log.d(TAG, "Transfer complete — $sizeStr saved to ${progress.savedUri}")
                appendLog(
                    "✅ Transfer complete! $sizeStr" +
                            (progress.savedUri?.let { " → Saved to Downloads" } ?: "")
                )
                _uiState.update {
                    it.copy(
                        isTransferring = false,
                        transferProgress = 1f,
                        transferComplete = true,
                        savedFileUri = progress.savedUri
                    )
                }
            }

            else -> {
                _uiState.update { it.copy(transferProgress = progress.fraction) }
                val pct = (progress.fraction * 100).toInt()
                val lastLoggedPct = ((_uiState.value.transferProgress - progress.fraction) * 100).toInt()
                // Log at every 10% boundary (only when crossing a new threshold)
                if (pct / 10 != lastLoggedPct / 10) {
                    Log.d(TAG, "Transfer $pct% (${progress.bytesTransferred}/${progress.totalBytes})")
                }
            }
        }
    }

    /**
     * Appends a new [LogEntry] to the status log.
     * Safe to call from any coroutine / thread.
     */
    fun appendLog(message: String, isError: Boolean = false) {
        Log.d(TAG, "log: $message")
        _uiState.update { state ->
            state.copy(statusLog = state.statusLog + LogEntry(message, isError))
        }
    }

    /** Resets the entire transfer state back to idle. */
    fun reset() {
        transferJob?.cancel()
        wifiDirectManager.reset()
        _uiState.value = TransferUiState()
        Log.d(TAG, "State reset to Idle")
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.close()
        Log.d(TAG, "ViewModel cleared")
    }
}
