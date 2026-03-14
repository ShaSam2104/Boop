package com.shashsam.boop.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "WifiDirectManager"

/** The fixed IP address of the Wi-Fi Direct Group Owner on Android (always this value). */
const val GROUP_OWNER_IP = "192.168.49.1"

/**
 * Represents the current state of the Wi-Fi Direct connection.
 */
sealed class WifiDirectState {
    /** No active connection and no pending operation. */
    object Idle : WifiDirectState()

    /** A P2P group is being created (Sender side). */
    object CreatingGroup : WifiDirectState()

    /**
     * A P2P group was created and the HCE service is ready to advertise via NFC.
     *
     * @param deviceMac        Wi-Fi Direct MAC of this (Sender) device.
     * @param groupOwnerAddress IP of the Group Owner (always [GROUP_OWNER_IP]).
     */
    data class GroupCreated(
        val deviceMac: String,
        val groupOwnerAddress: String
    ) : WifiDirectState()

    /** Connecting to the Sender (Receiver side). */
    object Connecting : WifiDirectState()

    /**
     * P2P connection is established.
     *
     * @param groupOwnerAddress IP of the Group Owner.
     * @param isGroupOwner      Whether this device is the Group Owner.
     */
    data class Connected(
        val groupOwnerAddress: String,
        val isGroupOwner: Boolean
    ) : WifiDirectState()

    /** Connection was torn down or never formed. */
    object Disconnected : WifiDirectState()

    /** An error occurred. */
    data class Error(val message: String) : WifiDirectState()
}

/**
 * Manages the Wi-Fi Direct (Wi-Fi P2P) lifecycle for Boop.
 *
 * ### Sender side
 * 1. Call [initialize] once (e.g. in a ViewModel `init` block).
 * 2. Call [register] in `Activity.onResume` and [unregister] in `Activity.onPause`.
 * 3. Call [createGroup] — the device becomes the Group Owner.
 * 4. Pass [ownDeviceMac] to the [BoopHceService][com.shashsam.boop.nfc.BoopHceService]
 *    so it can broadcast the MAC in the NFC NDEF payload.
 *
 * ### Receiver side
 * 1. After the NFC tap delivers the Sender's MAC, call [connect].
 * 2. Observe [state]; once [WifiDirectState.Connected], open a TCP socket to
 *    [GROUP_OWNER_IP] on the port from the NFC payload.
 *
 * All state changes are published via [state].
 *
 * @param context Application context — never stored as an Activity reference.
 */
class WifiDirectManager(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null

    private val _state = MutableStateFlow<WifiDirectState>(WifiDirectState.Idle)

    /** Observable state of the Wi-Fi Direct subsystem. */
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val _ownDeviceMac = MutableStateFlow<String?>(null)

    /**
     * This device's Wi-Fi Direct MAC address.
     * Populated when [WIFI_P2P_THIS_DEVICE_CHANGED_ACTION][WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION]
     * is received after calling [initialize].
     */
    val ownDeviceMac: StateFlow<String?> = _ownDeviceMac.asStateFlow()

    private var receiverRegistered = false

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val wifiState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val enabled = wifiState == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WIFI_P2P_STATE_CHANGED: enabled=$enabled")
                    if (!enabled) {
                        _state.value = WifiDirectState.Error("Wi-Fi Direct is not enabled on this device")
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    Log.d(TAG, "WIFI_P2P_PEERS_CHANGED")
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED — requesting connection info")
                    val ch = channel ?: return
                    manager?.requestConnectionInfo(ch, connectionInfoListener)
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device: WifiP2pDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                                WifiP2pDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        }
                    device?.let {
                        Log.d(TAG, "THIS_DEVICE_CHANGED: mac=${it.deviceAddress} status=${it.status}")
                        _ownDeviceMac.value = it.deviceAddress
                    }
                }
            }
        }
    }

    private val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { info: WifiP2pInfo ->
        Log.d(TAG, "ConnectionInfo: groupFormed=${info.groupFormed} isGroupOwner=${info.isGroupOwner} ownerAddress=${info.groupOwnerAddress}")
        if (info.groupFormed) {
            val ownerAddress = info.groupOwnerAddress?.hostAddress ?: GROUP_OWNER_IP
            _state.value = WifiDirectState.Connected(
                groupOwnerAddress = ownerAddress,
                isGroupOwner = info.isGroupOwner
            )
        } else {
            Log.d(TAG, "Group not formed (or was dissolved)")
            if (_state.value is WifiDirectState.Connected) {
                _state.value = WifiDirectState.Disconnected
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Initialises the [WifiP2pManager] channel.
     * Call once — typically from a ViewModel `init` block.
     */
    fun initialize() {
        if (manager == null) {
            Log.e(TAG, "WifiP2pManager service not available on this device")
            _state.value = WifiDirectState.Error("Wi-Fi Direct is not available on this device")
            return
        }
        channel = manager.initialize(context, context.mainLooper, null)
        Log.d(TAG, "WifiP2pManager channel initialised")
    }

    /**
     * Registers the Wi-Fi Direct [BroadcastReceiver].
     * Call in `Activity.onResume`.
     */
    fun register() {
        if (receiverRegistered) return
        try {
            context.registerReceiver(broadcastReceiver, intentFilter)
            receiverRegistered = true
            Log.d(TAG, "Wi-Fi Direct BroadcastReceiver registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register BroadcastReceiver", e)
        }
    }

    /**
     * Unregisters the Wi-Fi Direct [BroadcastReceiver].
     * Call in `Activity.onPause`.
     */
    fun unregister() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(broadcastReceiver)
            receiverRegistered = false
            Log.d(TAG, "Wi-Fi Direct BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister BroadcastReceiver (already unregistered?)", e)
        }
    }

    // ─── Sender (Group Owner) ─────────────────────────────────────────────────

    /**
     * Creates a Wi-Fi Direct persistent group, making this device the **Group Owner**.
     *
     * On success, [state] transitions to [WifiDirectState.GroupCreated].
     * The actual [WifiDirectState.Connected] follows via the [WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION]
     * broadcast.
     *
     * @return `true` if the [WifiP2pManager] accepted the request; `false` otherwise.
     */
    suspend fun createGroup(): Boolean {
        val mgr = manager ?: return false.also {
            _state.value = WifiDirectState.Error("Wi-Fi Direct not available")
        }
        val ch = channel ?: return false.also {
            _state.value = WifiDirectState.Error("Channel not initialised — call initialize() first")
        }

        Log.d(TAG, "createGroup()")
        _state.value = WifiDirectState.CreatingGroup

        return suspendCancellableCoroutine { cont ->
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "createGroup onSuccess")
                    val mac = _ownDeviceMac.value
                    if (mac == null) {
                        Log.w(TAG, "Own MAC not yet received from WIFI_P2P_THIS_DEVICE_CHANGED — using placeholder")
                    }
                    _state.value = WifiDirectState.GroupCreated(
                        deviceMac = mac ?: "00:00:00:00:00:00",
                        groupOwnerAddress = GROUP_OWNER_IP
                    )
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    val msg = "createGroup failed: ${reason.toReasonString()}"
                    Log.e(TAG, msg)
                    _state.value = WifiDirectState.Error(msg)
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /**
     * Removes the current Wi-Fi Direct group.
     * Use after a transfer completes on the Sender side.
     */
    suspend fun removeGroup(): Boolean {
        val mgr = manager ?: return false
        val ch = channel ?: return false
        return suspendCancellableCoroutine { cont ->
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "removeGroup onSuccess")
                    _state.value = WifiDirectState.Disconnected
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "removeGroup failed: ${reason.toReasonString()}")
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    // ─── Receiver (Client) ────────────────────────────────────────────────────

    /**
     * Initiates a Wi-Fi Direct connection to [deviceAddress] (Receiver side).
     *
     * The call returns once the [WifiP2pManager] has queued the connection request.
     * The resulting [WifiDirectState.Connected] event arrives asynchronously via
     * [WIFI_P2P_CONNECTION_CHANGED_ACTION][WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION].
     *
     * @param deviceAddress Wi-Fi Direct MAC address of the Sender device (from NFC payload).
     * @return `true` if the request was accepted; `false` otherwise.
     */
    suspend fun connect(deviceAddress: String): Boolean {
        val mgr = manager ?: return false.also {
            _state.value = WifiDirectState.Error("Wi-Fi Direct not available")
        }
        val ch = channel ?: return false.also {
            _state.value = WifiDirectState.Error("Channel not initialised — call initialize() first")
        }

        Log.d(TAG, "connect(deviceAddress=$deviceAddress)")
        _state.value = WifiDirectState.Connecting

        val config = WifiP2pConfig().apply { this.deviceAddress = deviceAddress }
        return suspendCancellableCoroutine { cont ->
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "connect onSuccess — waiting for WIFI_P2P_CONNECTION_CHANGED broadcast")
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    val msg = "connect failed: ${reason.toReasonString()}"
                    Log.e(TAG, msg)
                    _state.value = WifiDirectState.Error(msg)
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /**
     * Disconnects from the current Wi-Fi Direct group (Receiver side).
     */
    suspend fun disconnect(): Boolean {
        val mgr = manager ?: return false
        val ch = channel ?: return false
        return suspendCancellableCoroutine { cont ->
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "disconnect onSuccess")
                    _state.value = WifiDirectState.Disconnected
                    if (cont.isActive) cont.resume(true)
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "disconnect failed: ${reason.toReasonString()}")
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /**
     * Explicitly requests connection info update.
     * Results are delivered via [connectionInfoListener] which updates [state].
     */
    fun requestConnectionInfo() {
        val ch = channel ?: return
        manager?.requestConnectionInfo(ch, connectionInfoListener)
        Log.d(TAG, "requestConnectionInfo()")
    }

    /** Resets [state] to [WifiDirectState.Idle]. */
    fun reset() {
        _state.value = WifiDirectState.Idle
    }

    /**
     * Closes the [WifiP2pManager] channel.
     * Call from `ViewModel.onCleared()`.
     */
    fun close() {
        unregister()
        channel?.close()
        channel = null
        Log.d(TAG, "Wi-Fi Direct channel closed")
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun Int.toReasonString(): String = when (this) {
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.ERROR           -> "ERROR"
        WifiP2pManager.BUSY            -> "BUSY"
        else                           -> "UNKNOWN($this)"
    }
}
