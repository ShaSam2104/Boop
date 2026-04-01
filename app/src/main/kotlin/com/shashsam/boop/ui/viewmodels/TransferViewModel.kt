package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shashsam.boop.data.BoopDatabase
import com.shashsam.boop.data.FriendDao
import com.shashsam.boop.data.FriendEntity
import com.shashsam.boop.data.TransferHistoryDao
import com.shashsam.boop.data.TransferHistoryEntity
import com.shashsam.boop.nfc.BoopHceService
import com.shashsam.boop.nfc.ConnectionDetails
import com.shashsam.boop.transfer.TransferManager
import com.shashsam.boop.ui.models.LogEntry
import com.shashsam.boop.ui.models.RecentBoop
import com.shashsam.boop.utils.toFormattedSize
import com.shashsam.boop.wifi.GROUP_OWNER_IP
import com.shashsam.boop.wifi.WifiDirectManager
import com.shashsam.boop.wifi.WifiDirectState
import com.shashsam.boop.transfer.ProfileData
import com.shashsam.boop.utils.getOrCreateUlid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val TAG = "TransferViewModel"

/** Time to wait for a Wi-Fi Direct connection before showing a timeout error. */
private const val CONNECTION_TIMEOUT_MS = 10_000L

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
 * @param isWifiConnected    `true` after Wi-Fi Direct connection is established.
 * @param transferComplete   `true` after a transfer finishes successfully.
 * @param transferredBytes   Bytes transferred so far (for display during transfer).
 * @param totalBytes         Total bytes to transfer (for display during transfer).
 * @param savedFileUri       URI of the received file (Receiver side, post-completion).
 * @param error              Non-null when a terminal error has occurred; drives the error dialog.
 * @param receivedPayload   Non-null when the NFC reader has extracted [ConnectionDetails]; drives the payload BottomSheet.
 */
data class TransferUiState(
    val statusLog: List<LogEntry> = emptyList(),
    val transferProgress: Float = 0f,
    val isTransferring: Boolean = false,
    val isSendMode: Boolean = false,
    val isReceiveMode: Boolean = true,
    val isNfcBroadcasting: Boolean = false,
    val isNfcReading: Boolean = true,
    val isWifiConnecting: Boolean = false,
    val isWifiConnected: Boolean = false,
    val transferComplete: Boolean = false,
    val transferredBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val savedFileUri: Uri? = null,
    val error: String? = null,
    val receivedPayload: ConnectionDetails? = null,
    val recentTransfers: List<RecentBoop> = emptyList(),
    val currentFileName: String? = null,
    val nfcDisabledWarning: Boolean = false,
    val wifiDisabledWarning: Boolean = false,
    val hotspotWarning: Boolean = false,
    val nfcWarningDismissedThisSession: Boolean = false,
    val wifiWarningDismissedThisSession: Boolean = false,
    val hotspotWarningDismissedThisSession: Boolean = false,
    val senderFileUri: Uri? = null,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 1,
    val pendingFileUris: List<Uri> = emptyList(),
    val pendingApproval: ConnectionDetails? = null,
    val isResetting: Boolean = false,
    val pendingFriendRequest: com.shashsam.boop.transfer.ProfileData? = null,
    val friendExchangeComplete: Boolean = false,
    val isProfileShareMode: Boolean = false,
    val receivedProfile: com.shashsam.boop.transfer.ProfileData? = null
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

    private val historyDao: TransferHistoryDao =
        BoopDatabase.getInstance(application).transferHistoryDao()

    private val friendDao: FriendDao =
        BoopDatabase.getInstance(application).friendDao()

    private val profileItemDao = BoopDatabase.getInstance(application).profileItemDao()

    private val settingsPrefs = application.getSharedPreferences("boop_settings", android.content.Context.MODE_PRIVATE)

    private val localUlid: String = getOrCreateUlid(application)

    private var currentConnectionSsid: String? = null
    private var currentConnectionDisplayName: String? = null
    private var currentConnectionUlid: String? = null
    private var pendingBefriend: Boolean = false
    private var autoSaveProfileFromFriend: Boolean = false
    private var friendDecisionDeferred: CompletableDeferred<Boolean>? = null

    private val _selectedFriendId = MutableStateFlow<Long?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedFriend: StateFlow<FriendEntity?> = _selectedFriendId
        .flatMapLatest { id ->
            if (id != null) friendDao.getByIdFlow(id)
            else kotlinx.coroutines.flow.flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow(TransferUiState())

    /** Observable UI state for the home screen. */
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    /** Observable friends list for the profile screen. */
    val friends: StateFlow<List<FriendEntity>> = friendDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var transferJob: Job? = null

    init {
        wifiDirectManager.initialize()
        observeWifiDirectState()
        observeHistory()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            historyDao.getAll().collect { entities ->
                val boops = entities.map { it.toRecentBoop() }
                _uiState.update { it.copy(recentTransfers = boops) }
            }
        }
    }

    private fun TransferHistoryEntity.toRecentBoop(): RecentBoop = RecentBoop(
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        timestamp = timestamp,
        wasSender = wasSender,
        fileUri = fileUriString?.let { Uri.parse(it) },
        peerUlid = peerUlid
    )

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
                        // Publish connection details for HCE so NFC broadcasts them.
                        BoopHceService.connectionMac = wifiState.deviceMac
                        BoopHceService.connectionPort = BoopHceService.DEFAULT_PORT
                        BoopHceService.connectionSsid = wifiState.ssid
                        BoopHceService.connectionToken = wifiState.passphrase
                        // Track our own SSID so handleFriendProfileReceived can save
                        // the receiver's profile keyed by this group's SSID.
                        currentConnectionSsid = wifiState.ssid
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
                        _uiState.update { it.copy(isWifiConnecting = false, isWifiConnected = true) }
                    }

                    is WifiDirectState.Disconnected -> {
                        appendLog("🔌 Wi-Fi Direct disconnected.")
                        _uiState.update { it.copy(isWifiConnecting = false, isWifiConnected = false) }
                    }

                    is WifiDirectState.Error -> {
                        appendLog("❌ Wi-Fi Direct error: ${wifiState.message}", isError = true)
                        _uiState.update {
                            it.copy(isWifiConnecting = false, isWifiConnected = false, error = wifiState.message)
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
        BoopHceService.connectionFileCount = 1
        BoopHceService.connectionDisplayName = settingsPrefs.getString("display_name", "") ?: ""
        BoopHceService.connectionUlid = localUlid
        _uiState.update {
            it.copy(isSendMode = true, isReceiveMode = false, isNfcReading = false, error = null, transferComplete = false)
        }
        appendLog("📤 Send mode activated.")
        viewModelScope.launch {
            val ok = wifiDirectManager.createGroup()
            if (!ok) {
                appendLog("❌ Failed to create Wi-Fi Direct group.", isError = true)
                _uiState.update { it.copy(error = "Failed to create Wi-Fi Direct group. Try again.") }
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
        TransferManager.cleanup()
        val context = getApplication<Application>()
        // Take persistable URI permission so the sender file URI survives in history
        try {
            context.contentResolver.takePersistableUriPermission(
                fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable URI permission for $fileUri", e)
        }
        // Extract file name from URI for display in Recent Boops
        val fileName = resolveFileName(fileUri)
        friendDecisionDeferred = CompletableDeferred()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            appendLog("🚀 Starting file transfer…")
            _uiState.update {
                it.copy(
                    isTransferring = true,
                    transferProgress = 0f,
                    transferComplete = false,
                    currentFileName = fileName,
                    senderFileUri = fileUri,
                    pendingFileUris = listOf(fileUri)
                )
            }
            try {
                val senderProfile = buildLocalProfile()
                TransferManager.sendFilesWithFriendExchange(
                    context, listOf(fileUri), BoopHceService.DEFAULT_PORT,
                    senderProfile = senderProfile,
                    friendDecision = friendDecisionDeferred
                ).collect { progress -> handleSenderProgress(progress) }
            } finally {
                Log.d(TAG, "Send flow completed, cleaning up resources")
                cleanupTransferResources()
            }
        }
    }

    /**
     * Starts sending multiple files sequentially over a single TCP connection.
     *
     * @param fileUris URIs returned by the multi-file picker.
     */
    fun startSendingMultiple(fileUris: List<Uri>) {
        if (fileUris.isEmpty()) return
        if (fileUris.size == 1) {
            startSending(fileUris.first())
            return
        }
        Log.d(TAG, "startSendingMultiple: ${fileUris.size} files")
        TransferManager.cleanup()
        val context = getApplication<Application>()
        // Take persistable URI permissions for all files
        fileUris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable URI permission for $uri", e)
            }
        }
        val firstName = resolveFileName(fileUris.first())
        BoopHceService.connectionFileCount = fileUris.size
        friendDecisionDeferred = CompletableDeferred()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            appendLog("🚀 Starting multi-file transfer (${fileUris.size} files)…")
            _uiState.update {
                it.copy(
                    isTransferring = true,
                    transferProgress = 0f,
                    transferComplete = false,
                    currentFileName = firstName,
                    senderFileUri = fileUris.first(),
                    totalFiles = fileUris.size,
                    currentFileIndex = 0,
                    pendingFileUris = fileUris
                )
            }
            try {
                val senderProfile = buildLocalProfile()
                TransferManager.sendFilesWithFriendExchange(
                    context, fileUris, BoopHceService.DEFAULT_PORT,
                    senderProfile = senderProfile,
                    friendDecision = friendDecisionDeferred
                ).collect { progress -> handleSenderProgress(progress) }
            } finally {
                Log.d(TAG, "Multi-file send flow completed, cleaning up resources")
                cleanupTransferResources()
            }
        }
    }

    private fun handleMultiFileProgress(progress: com.shashsam.boop.transfer.TransferProgress) {
        when {
            progress.error != null -> {
                Log.e(TAG, "Multi-file transfer error: ${progress.error}")
                appendLog("❌ Transfer error: ${progress.error}", isError = true)
                _uiState.update { it.copy(isTransferring = false, error = progress.error) }
            }
            // Per-file completion: fileName is set and bytes match total
            progress.fileName != null && progress.bytesTransferred == progress.totalBytes && progress.totalBytes > 0 -> {
                val currentState = _uiState.value
                // Insert history for THIS file
                val historyUri = if (currentState.isSendMode) {
                    currentState.pendingFileUris.getOrNull(progress.fileIndex)?.toString()
                } else {
                    progress.savedUri?.toString()
                }
                val entity = TransferHistoryEntity(
                    fileName = progress.fileName,
                    fileSize = progress.totalBytes,
                    mimeType = progress.mimeType ?: "",
                    timestamp = System.currentTimeMillis(),
                    wasSender = currentState.isSendMode,
                    fileUriString = historyUri,
                    peerUlid = currentConnectionUlid
                )
                viewModelScope.launch { historyDao.insert(entity) }
                appendLog("✅ File ${progress.fileIndex + 1}/${progress.totalFiles}: ${progress.fileName}")

                if (progress.isComplete) {
                    // Last file — mark transfer as done
                    _uiState.update { state ->
                        state.copy(
                            isTransferring = false,
                            transferProgress = 1f,
                            transferComplete = true,
                            savedFileUri = progress.savedUri,
                            currentFileName = progress.fileName,
                            currentFileIndex = progress.fileIndex,
                            totalFiles = progress.totalFiles
                        )
                    }
                } else {
                    // More files to come — reset progress for next file
                    _uiState.update {
                        it.copy(
                            transferProgress = 0f,
                            currentFileName = progress.fileName,
                            currentFileIndex = progress.fileIndex,
                            totalFiles = progress.totalFiles
                        )
                    }
                }
            }
            else -> {
                // In-progress chunk update
                _uiState.update {
                    it.copy(
                        transferProgress = progress.fraction,
                        transferredBytes = progress.bytesTransferred,
                        totalBytes = progress.totalBytes,
                        currentFileIndex = progress.fileIndex,
                        totalFiles = progress.totalFiles,
                        currentFileName = progress.fileName ?: it.currentFileName
                    )
                }
            }
        }
    }

    private fun handleSenderProgress(progress: com.shashsam.boop.transfer.TransferProgress) {
        when {
            progress.friendRequest != null -> {
                Log.d(TAG, "Friend request received from: ${progress.friendRequest.displayName}")
                // Sender auto-accepts all friend requests — the sender already chose
                // to share files with this person. The receiver controls the decision
                // via the 3-button approval sheet (Accept / Accept+Befriend / Reject).
                Log.d(TAG, "Auto-accepting friend request from ${progress.friendRequest.displayName}")
                friendDecisionDeferred?.complete(true)
                friendDecisionDeferred = null
                handleFriendProfileReceived(progress.friendRequest)
                _uiState.update { it.copy(friendExchangeComplete = true) }
            }
            progress.friendProfile != null -> {
                handleFriendProfileReceived(progress.friendProfile)
                _uiState.update { it.copy(friendExchangeComplete = true) }
            }
            else -> {
                val totalFiles = _uiState.value.totalFiles
                if (totalFiles > 1) {
                    handleMultiFileProgress(progress)
                } else {
                    handleTransferProgress(progress)
                }
            }
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
        val current = _uiState.value
        // Only process NFC payloads when in Receive mode — Sender should never act on incoming NFC.
        if (current.isSendMode) {
            Log.d(TAG, "onNfcPayloadReceived ignored — device is in Send mode")
            return
        }
        // Guard against NFC payloads arriving during reset cycle
        if (current.isResetting) {
            Log.d(TAG, "onNfcPayloadReceived ignored — device is resetting")
            return
        }
        // Guard against duplicate callbacks (reader mode + foreground dispatch fire for the same tap)
        if (current.isWifiConnecting || current.isWifiConnected || current.isTransferring) {
            Log.d(TAG, "onNfcPayloadReceived ignored — already in progress (connecting=${current.isWifiConnecting} connected=${current.isWifiConnected} transferring=${current.isTransferring})")
            return
        }
        Log.d(TAG, "onNfcPayloadReceived: mac=${details.mac} port=${details.port} ssid=${details.ssid} token=${details.token} type=${details.type} ulid=${details.ulid}")
        currentConnectionSsid = details.ssid
        currentConnectionDisplayName = details.displayName.takeIf { it.isNotBlank() }
        currentConnectionUlid = details.ulid.takeIf { it.isNotBlank() }

        // Handle profile share type
        if (details.type == "profile") {
            appendLog("📲 NFC tap! Receiving profile...")
            _uiState.update { it.copy(isNfcReading = false, receivedPayload = details) }
            // Auto-save profile if sender is already a friend
            viewModelScope.launch {
                val knownFriend = details.ulid.isNotBlank() && isFriend(details.ulid)
                if (knownFriend) {
                    Log.d(TAG, "Auto-accepting profile from friend ULID=${details.ulid}")
                    autoSaveProfileFromFriend = true
                }
                proceedWithProfileReceive(details)
            }
            return
        }

        appendLog("📲 NFC tap! Sender MAC=${details.mac} Port=${details.port}")
        _uiState.update { it.copy(isNfcReading = false, receivedPayload = details) }

        // Check receive permission setting
        val permission = settingsPrefs.getString("receive_permission", "friends") ?: "friends"
        viewModelScope.launch {
            val knownFriend = permission == "friends" && isFriend(details.ulid)
            if (knownFriend) {
                Log.d(TAG, "Auto-accepting transfer from friend ULID=${details.ulid}, will reshare profiles")
                // Always exchange profiles with friends to keep them fresh
                pendingBefriend = true
                proceedWithReceive(details)
            } else {
                Log.d(TAG, "Awaiting user approval for transfer from ULID=${details.ulid}")
                _uiState.update { it.copy(pendingApproval = details) }
            }
        }
    }

    fun approveIncomingTransfer(becomeFriends: Boolean = false) {
        val details = _uiState.value.pendingApproval ?: return
        Log.d(TAG, "approveIncomingTransfer becomeFriends=$becomeFriends")
        pendingBefriend = becomeFriends
        _uiState.update { it.copy(pendingApproval = null) }
        proceedWithReceive(details)
    }

    fun rejectIncomingTransfer() {
        Log.d(TAG, "rejectIncomingTransfer")
        _uiState.update { it.copy(pendingApproval = null, receivedPayload = null) }
        currentConnectionSsid = null
        currentConnectionDisplayName = null
        prepareReceive()
    }

    private fun proceedWithReceive(details: ConnectionDetails) {
        val context = getApplication<Application>()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            // Show transfer screen immediately so the receiver gets visual feedback
            // that something is happening right after NFC tap / approval.
            _uiState.update {
                it.copy(
                    isTransferring = true,
                    transferProgress = 0f,
                    transferComplete = false,
                    pendingApproval = null,
                    totalFiles = details.fileCount
                )
            }
            try {
                // 1. Join the Sender's Wi-Fi Direct group by SSID + passphrase
                val queued = wifiDirectManager.connect(details.ssid, details.token, details.mac)
                if (!queued) {
                    appendLog("❌ Wi-Fi Direct connection request rejected.", isError = true)
                    _uiState.update { it.copy(error = "Wi-Fi Direct connection request was rejected.") }
                    return@launch
                }

                // 2. Wait for Connected state with a 10 s timeout.
                appendLog("⏳ Establishing Wi-Fi Direct link…")
                val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    wifiDirectManager.state.first { state ->
                        state is WifiDirectState.Connected || state is WifiDirectState.Error
                    }
                }

                when {
                    result == null -> {
                        Log.w(TAG, "Wi-Fi Direct connection timed out after ${CONNECTION_TIMEOUT_MS}ms")
                        appendLog("❌ Connection timed out.", isError = true)
                        _uiState.update {
                            it.copy(
                                isWifiConnecting = false,
                                error = "Wi-Fi Direct connection timed out. Move closer and try again."
                            )
                        }
                        return@launch
                    }
                    result is WifiDirectState.Error -> {
                        return@launch
                    }
                }

                // 3. Connected — open TCP receive on the Group Owner IP.
                Log.d(TAG, "Wi-Fi Direct connected — starting TCP receive on ${GROUP_OWNER_IP}:${details.port} (fileCount=${details.fileCount})")
                appendLog("🚀 Starting file receive (${GROUP_OWNER_IP}:${details.port})…")
                if (pendingBefriend) {
                    val localProfile = buildLocalProfile()
                    TransferManager.receiveFilesWithFriendExchange(
                        context, GROUP_OWNER_IP, details.port,
                        fileCount = details.fileCount,
                        becomeFriends = true,
                        localProfile = localProfile
                    ).collect { progress ->
                        if (progress.friendProfile != null) {
                            handleFriendProfileReceived(progress.friendProfile)
                            _uiState.update { it.copy(friendExchangeComplete = true) }
                        } else if (details.fileCount > 1) {
                            handleMultiFileProgress(progress)
                        } else {
                            handleTransferProgress(progress)
                        }
                    }
                } else if (details.fileCount > 1) {
                    TransferManager.receiveFiles(context, GROUP_OWNER_IP, details.port)
                        .collect { progress -> handleMultiFileProgress(progress) }
                } else {
                    TransferManager.receiveFile(context, GROUP_OWNER_IP, details.port)
                        .collect { progress -> handleTransferProgress(progress) }
                }
            } finally {
                // Clean up resources but preserve UI state so BoopScaffold can
                // show completion and navigate back before full state reset.
                Log.d(TAG, "Receive flow finished, cleaning up resources")
                cleanupTransferResources()
            }
        }
    }

    private suspend fun isFriend(ulid: String): Boolean =
        ulid.isNotBlank() && friendDao.getByUlid(ulid) != null

    // ─── Friend exchange ──────────────────────────────────────────────────────

    fun acceptFriendRequest() {
        Log.d(TAG, "acceptFriendRequest")
        val profile = _uiState.value.pendingFriendRequest
        friendDecisionDeferred?.complete(true)
        friendDecisionDeferred = null
        if (profile != null) {
            handleFriendProfileReceived(profile)
        }
        _uiState.update { it.copy(pendingFriendRequest = null, friendExchangeComplete = true) }
    }

    fun rejectFriendRequest() {
        Log.d(TAG, "rejectFriendRequest")
        friendDecisionDeferred?.complete(false)
        friendDecisionDeferred = null
        _uiState.update { it.copy(pendingFriendRequest = null) }
        // The transfer job will complete after sending NAK, triggering auto-reset
    }

    private fun handleFriendProfileReceived(profile: ProfileData) {
        val ssid = currentConnectionSsid ?: return
        val ulid = profile.ulid.takeIf { it.isNotBlank() } ?: currentConnectionUlid ?: return
        Log.d(TAG, "handleFriendProfileReceived: ${profile.displayName} ulid=$ulid")
        viewModelScope.launch {
            // Save profile pic keyed by ULID (stable across sessions)
            var picPath: String? = null
            if (profile.profilePicBytes != null) {
                try {
                    val context = getApplication<Application>()
                    val picsDir = File(context.filesDir, "friend_pics")
                    picsDir.mkdirs()
                    val picFile = File(picsDir, "${ulid}.jpg")
                    picFile.writeBytes(profile.profilePicBytes)
                    picPath = picFile.absolutePath
                    Log.d(TAG, "Friend pic saved: $picPath")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save friend pic", e)
                }
            }

            val now = System.currentTimeMillis()
            friendDao.upsertByUlid(
                FriendEntity(
                    ulid = ulid,
                    ssid = ssid,
                    displayName = profile.displayName,
                    firstSeenTimestamp = now,
                    lastSeenTimestamp = now,
                    lastInteractionTimestamp = now,
                    profileJson = profile.profileItemsJson.takeIf { it.isNotBlank() },
                    profilePicPath = picPath
                )
            )

            // Retroactively tag recent history entries with the peer ULID (sender side
            // doesn't know the peer until friend exchange completes after file transfer)
            if (currentConnectionUlid == null) {
                currentConnectionUlid = ulid
                historyDao.updatePeerUlidForRecent(ulid, now - 120_000)
            }
        }
    }

    private suspend fun buildLocalProfile(): ProfileData {
        val displayName = settingsPrefs.getString("display_name", "") ?: ""
        val profilePicPath = settingsPrefs.getString("profile_pic_path", null)
        val picBytes = profilePicPath?.let { path ->
            try {
                File(path).takeIf { it.exists() }?.readBytes()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read profile pic", e)
                null
            }
        }
        // Read profile items from DB directly to build JSON (new envelope format)
        val items = profileItemDao.getAllOnce()
        val itemsArray = org.json.JSONArray()
        for (item in items) {
            itemsArray.put(org.json.JSONObject().apply {
                put("type", item.type)
                put("label", item.label)
                put("value", item.value)
                put("size", item.size)
                put("sortOrder", item.sortOrder)
            })
        }
        val bio = settingsPrefs.getString("bio", "") ?: ""
        val profileJson = org.json.JSONObject().apply {
            put("items", itemsArray)
            put("bio", bio)
        }.toString()
        return ProfileData(
            ulid = localUlid,
            displayName = displayName,
            profileItemsJson = profileJson,
            profilePicBytes = picBytes
        )
    }

    // ─── Profile sharing via NFC ──────────────────────────────────────────

    fun prepareProfileShare(profileData: ProfileData) {
        Log.d(TAG, "prepareProfileShare()")
        TransferManager.cleanup()
        transferJob?.cancel()
        BoopHceService.connectionType = "profile"
        BoopHceService.connectionFileCount = 0
        BoopHceService.connectionDisplayName = settingsPrefs.getString("display_name", "") ?: ""
        BoopHceService.connectionUlid = localUlid
        _uiState.update {
            it.copy(
                isProfileShareMode = true,
                isSendMode = true,
                isReceiveMode = false,
                isNfcReading = false,
                error = null,
                transferComplete = false
            )
        }
        transferJob = viewModelScope.launch {
            // 1. Reset stale Wi-Fi Direct state
            wifiDirectManager.reset()
            // 2. Create group and wait for it
            val ok = wifiDirectManager.createGroup()
            if (!ok) {
                appendLog("❌ Failed to create Wi-Fi Direct group for profile share.", isError = true)
                _uiState.update { it.copy(isProfileShareMode = false) }
                return@launch
            }
            // 3. Wait for GroupCreated state so HCE has SSID/passphrase
            val groupReady = withTimeoutOrNull(10_000L) {
                wifiDirectManager.state.first { it is WifiDirectState.GroupCreated }
            }
            if (groupReady == null || groupReady !is WifiDirectState.GroupCreated) {
                appendLog("❌ Wi-Fi Direct group creation timed out.", isError = true)
                _uiState.update { it.copy(isProfileShareMode = false) }
                return@launch
            }
            // Explicitly set HCE connection details — don't rely on async observeWifiDirectState
            // which may not have processed GroupCreated yet when the NFC tap fires.
            BoopHceService.connectionMac = groupReady.deviceMac
            BoopHceService.connectionPort = BoopHceService.DEFAULT_PORT
            BoopHceService.connectionSsid = groupReady.ssid
            BoopHceService.connectionToken = groupReady.passphrase
            Log.d(TAG, "Group ready (SSID=${groupReady.ssid}), HCE details set, starting profile TCP server")
            // 4. Now start TCP server — group is ready, NFC is broadcasting
            try {
                TransferManager.sendProfile(profileData, BoopHceService.DEFAULT_PORT)
                    .collect { progress ->
                        if (progress.error != null) {
                            _uiState.update { it.copy(error = progress.error, isProfileShareMode = false) }
                        } else if (progress.isComplete) {
                            appendLog("✅ Profile shared successfully!")
                            _uiState.update { it.copy(isProfileShareMode = false, transferComplete = true) }
                        }
                    }
            } finally {
                Log.d(TAG, "Profile share flow finished, cleaning up resources")
                cleanupTransferResources()
                _uiState.update { it.copy(isProfileShareMode = false, isReceiveMode = true, isNfcReading = true, isSendMode = false) }
            }
        }
    }

    fun cancelProfileShare() {
        Log.d(TAG, "cancelProfileShare()")
        BoopHceService.connectionType = "file"
        _uiState.update { it.copy(isProfileShareMode = false) }
        resetToReceive()
    }

    fun proceedWithProfileReceive(details: ConnectionDetails) {
        Log.d(TAG, "proceedWithProfileReceive")
        val context = getApplication<Application>()
        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            try {
                val queued = wifiDirectManager.connect(details.ssid, details.token, details.mac)
                if (!queued) {
                    _uiState.update { it.copy(error = "Wi-Fi Direct connection rejected.") }
                    return@launch
                }

                val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    wifiDirectManager.state.first { state ->
                        state is WifiDirectState.Connected || state is WifiDirectState.Error
                    }
                }

                when {
                    result == null -> {
                        _uiState.update { it.copy(error = "Connection timed out.") }
                        return@launch
                    }
                    result is WifiDirectState.Error -> return@launch
                }

                TransferManager.receiveProfile(GROUP_OWNER_IP, details.port)
                    .collect { progress ->
                        if (progress.error != null) {
                            _uiState.update { it.copy(error = progress.error) }
                        } else if (progress.friendProfile != null) {
                            if (autoSaveProfileFromFriend) {
                                Log.d(TAG, "Auto-saving profile from friend: ${progress.friendProfile.displayName}")
                                handleFriendProfileReceived(progress.friendProfile)
                                autoSaveProfileFromFriend = false
                            } else {
                                _uiState.update { it.copy(receivedProfile = progress.friendProfile) }
                            }
                        }
                    }
            } finally {
                Log.d(TAG, "Profile receive flow finished, cleaning up resources")
                cleanupTransferResources()
                _uiState.update { it.copy(isReceiveMode = true, isNfcReading = true) }
            }
        }
    }

    fun saveReceivedProfileAsFriend() {
        val profile = _uiState.value.receivedProfile ?: return
        if (currentConnectionSsid == null) return
        Log.d(TAG, "saveReceivedProfileAsFriend: ${profile.displayName} ulid=${profile.ulid}")
        handleFriendProfileReceived(profile)
        _uiState.update { it.copy(receivedProfile = null) }
    }

    fun dismissReceivedProfile() {
        _uiState.update { it.copy(receivedProfile = null) }
    }

    // ─── Friend selection ─────────────────────────────────────────────────

    fun selectFriend(id: Long) {
        _selectedFriendId.value = id
    }

    fun removeFriend(id: Long) {
        viewModelScope.launch {
            friendDao.deleteById(id)
            _selectedFriendId.value = null
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
                val currentState = _uiState.value
                // For sender, use the original file URI; for receiver, use the saved URI
                val historyUri = if (currentState.isSendMode) {
                    currentState.senderFileUri?.toString()
                } else {
                    progress.savedUri?.toString()
                }
                val entity = TransferHistoryEntity(
                    fileName = progress.fileName ?: currentState.currentFileName ?: "Unknown file",
                    fileSize = progress.totalBytes,
                    mimeType = progress.mimeType ?: "",
                    timestamp = System.currentTimeMillis(),
                    wasSender = currentState.isSendMode,
                    fileUriString = historyUri,
                    peerUlid = currentConnectionUlid
                )
                viewModelScope.launch { historyDao.insert(entity) }
                _uiState.update { state ->
                    state.copy(
                        isTransferring = false,
                        transferProgress = 1f,
                        transferComplete = true,
                        savedFileUri = progress.savedUri,
                        currentFileName = progress.fileName ?: state.currentFileName
                    )
                }
            }

            else -> {
                val prevPct = (_uiState.value.transferProgress * 100).toInt()
                _uiState.update {
                    it.copy(
                        transferProgress = progress.fraction,
                        transferredBytes = progress.bytesTransferred,
                        totalBytes = progress.totalBytes
                    )
                }
                val newPct = (progress.fraction * 100).toInt()
                // Log at every 10% boundary
                if (newPct / 10 != prevPct / 10) {
                    Log.d(TAG, "Transfer $newPct% (${progress.bytesTransferred}/${progress.totalBytes})")
                }
            }
        }
    }

    fun setNfcWarning(show: Boolean) {
        _uiState.update { it.copy(nfcDisabledWarning = show) }
    }

    fun setWifiWarning(show: Boolean) {
        _uiState.update { it.copy(wifiDisabledWarning = show) }
    }

    fun dismissNfcWarning() {
        _uiState.update { it.copy(nfcWarningDismissedThisSession = true) }
    }

    fun dismissWifiWarning() {
        _uiState.update { it.copy(wifiWarningDismissedThisSession = true) }
    }

    fun setHotspotWarning(show: Boolean) {
        _uiState.update { it.copy(hotspotWarning = show) }
    }

    fun dismissHotspotWarning() {
        _uiState.update { it.copy(hotspotWarningDismissedThisSession = true) }
    }

    /** Clears dismissed flags when the underlying issue is resolved. */
    fun clearNfcWarningDismissed() {
        _uiState.update { it.copy(nfcWarningDismissedThisSession = false) }
    }

    fun clearWifiWarningDismissed() {
        _uiState.update { it.copy(wifiWarningDismissedThisSession = false) }
    }

    fun clearHotspotWarningDismissed() {
        _uiState.update { it.copy(hotspotWarningDismissedThisSession = false) }
    }

    /** Re-shows all active warning dialogs by clearing dismissed flags. */
    fun reshowWarnings() {
        _uiState.update { it.copy(
            nfcWarningDismissedThisSession = false,
            wifiWarningDismissedThisSession = false,
            hotspotWarningDismissedThisSession = false
        ) }
    }

    /** Dismisses the NFC payload BottomSheet by clearing [TransferUiState.receivedPayload]. */
    fun dismissPayloadSheet() {
        _uiState.update { it.copy(receivedPayload = null) }
    }

    /** Dismisses the error dialog and fully resets to receive mode. */
    fun dismissError() {
        _uiState.update { it.copy(error = null) }
        resetToReceive()
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

    /** Resolves a human-readable file name from a content URI. */
    private fun resolveFileName(uri: Uri): String {
        val context = getApplication<Application>()
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "Unknown file"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve file name for $uri", e)
            uri.lastPathSegment ?: "Unknown file"
        }
    }

    /** Resets the entire transfer state back to idle. */
    fun reset() {
        transferJob?.cancel()
        TransferManager.cleanup()
        currentConnectionSsid = null
        currentConnectionDisplayName = null
        currentConnectionUlid = null
        pendingBefriend = false
        autoSaveProfileFromFriend = false
        friendDecisionDeferred?.complete(false)
        friendDecisionDeferred = null
        BoopHceService.connectionType = "file"
        val recent = _uiState.value.recentTransfers
        _uiState.value = TransferUiState(recentTransfers = recent, isResetting = true)
        viewModelScope.launch {
            wifiDirectManager.reset()
            _uiState.update { it.copy(isResetting = false) }
            Log.d(TAG, "State reset to Idle")
        }
    }

    /**
     * Lightweight cleanup: closes sockets + tears down Wi-Fi Direct group.
     * Does NOT wipe UI state — call this from `finally` blocks so the UI
     * can still show the completion/error state while BoopScaffold navigates.
     */
    private suspend fun cleanupTransferResources() {
        Log.d(TAG, "cleanupTransferResources()")
        TransferManager.cleanup()
        pendingBefriend = false
        autoSaveProfileFromFriend = false
        friendDecisionDeferred?.complete(false)
        friendDecisionDeferred = null
        BoopHceService.connectionType = "file"
        wifiDirectManager.reset()
    }

    /** Resets state and re-arms receive mode so the next NFC tap works immediately. */
    fun resetToReceive() {
        transferJob?.cancel()
        TransferManager.cleanup()
        currentConnectionSsid = null
        currentConnectionDisplayName = null
        currentConnectionUlid = null
        pendingBefriend = false
        autoSaveProfileFromFriend = false
        friendDecisionDeferred?.complete(false)
        friendDecisionDeferred = null
        BoopHceService.connectionType = "file"
        val recent = _uiState.value.recentTransfers
        _uiState.value = TransferUiState(
            recentTransfers = recent,
            isResetting = true
        )
        viewModelScope.launch {
            wifiDirectManager.reset()
            // Brief delay to let Wi-Fi Direct framework fully tear down
            delay(200)
            _uiState.update { it.copy(isResetting = false, isReceiveMode = true, isNfcReading = true) }
            Log.d(TAG, "State reset to Receive mode")
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiDirectManager.close()
        Log.d(TAG, "ViewModel cleared")
    }
}
