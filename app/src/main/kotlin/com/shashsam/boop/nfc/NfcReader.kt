package com.shashsam.boop.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private const val TAG = "NfcReader"

/**
 * Connection details extracted from the Sender's HCE NDEF payload.
 *
 * @param mac  Wi-Fi Direct MAC address of the Sender device.
 * @param port TCP port the Sender's [ServerSocket][java.net.ServerSocket] is listening on.
 */
data class ConnectionDetails(
    val mac: String,
    val port: Int
)

/** Represents the current state of the NFC reader on the **Receiver** device. */
sealed class NfcReaderState {
    /** Not actively reading. */
    object Idle : NfcReaderState()

    /** Actively scanning for the Sender's HCE tag. */
    object Reading : NfcReaderState()

    /** Successfully read the Sender's payload. */
    data class Connected(val details: ConnectionDetails) : NfcReaderState()

    /** An error occurred during reading. */
    data class Error(val message: String) : NfcReaderState()
}

/**
 * Handles NFC operations on the **Receiver** device.
 *
 * Two complementary mechanisms are provided:
 * - **Reader Mode** ([enableReaderMode] / [disableReaderMode]): actively sends an
 *   ISO 7816 SELECT AID APDU to the Sender's [BoopHceService], parses the NDEF
 *   response, and updates [state].
 * - **Foreground Dispatch** ([enableForegroundDispatch] / [disableForegroundDispatch]):
 *   intercepts [NfcAdapter.ACTION_NDEF_DISCOVERED] and
 *   [NfcAdapter.ACTION_TECH_DISCOVERED] intents before the Android OS default
 *   handlers. Use [parseIntent] inside `Activity.onNewIntent` to extract
 *   [ConnectionDetails].
 *
 * Current state is exposed via [state] as a [StateFlow].
 */
class NfcReader(private val nfcAdapter: NfcAdapter?) {

    private val _state = MutableStateFlow<NfcReaderState>(NfcReaderState.Idle)

    /** Observable state of this NFC reader; updated on any background thread. */
    val state: StateFlow<NfcReaderState> = _state.asStateFlow()

    /** `true` if the device has an NFC adapter. */
    val isAvailable: Boolean get() = nfcAdapter != null

    /** `true` if the device has NFC and it is currently enabled. */
    val isEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    // ─── Reader Mode ──────────────────────────────────────────────────────────

    /**
     * Enables NFC Reader Mode so this device actively reads the Sender's HCE service.
     *
     * Call in [Activity.onResume] when the app is in **receive** mode.
     * The APDU exchange runs on a background thread managed by the NFC framework.
     */
    fun enableReaderMode(activity: Activity) {
        val adapter = nfcAdapter ?: run {
            Log.w(TAG, "NFC adapter not available — cannot enable reader mode")
            _state.value = NfcReaderState.Error("NFC not available on this device")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "NFC is disabled — please enable NFC in Settings")
            _state.value = NfcReaderState.Error("NFC is disabled. Please enable it in Settings.")
            return
        }
        Log.d(TAG, "Enabling NFC reader mode")
        _state.value = NfcReaderState.Reading
        adapter.enableReaderMode(
            activity,
            readerCallback,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    /**
     * Disables NFC reader mode.
     * Call in [Activity.onPause].
     */
    fun disableReaderMode(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
        if (_state.value is NfcReaderState.Reading) {
            _state.value = NfcReaderState.Idle
        }
        Log.d(TAG, "NFC reader mode disabled")
    }

    // ─── Foreground Dispatch ──────────────────────────────────────────────────

    /**
     * Enables NFC foreground dispatch so the activity intercepts
     * [NfcAdapter.ACTION_NDEF_DISCOVERED] and [NfcAdapter.ACTION_TECH_DISCOVERED]
     * intents before the OS default handlers.
     *
     * Call in [Activity.onResume].
     */
    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val intent = Intent(activity, activity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE
        else
            0
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)

        val ndefFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType(BoopHceService.BOOP_MIME_TYPE)
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                Log.e(TAG, "Malformed MIME type for foreground dispatch filter", e)
            }
        }
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val techList = arrayOf(arrayOf(IsoDep::class.java.name))

        adapter.enableForegroundDispatch(
            activity,
            pendingIntent,
            arrayOf(ndefFilter, techFilter),
            techList
        )
        Log.d(TAG, "NFC foreground dispatch enabled")
    }

    /**
     * Disables NFC foreground dispatch.
     * Call in [Activity.onPause].
     */
    fun disableForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
        Log.d(TAG, "NFC foreground dispatch disabled")
    }

    // ─── Intent Parsing ───────────────────────────────────────────────────────

    /**
     * Parses a foreground-dispatch intent and extracts [ConnectionDetails] if present.
     *
     * Handles both [NfcAdapter.ACTION_NDEF_DISCOVERED] (from NDEF-emulating tags) and
     * [NfcAdapter.ACTION_TECH_DISCOVERED] (from ISO-DEP / HCE tags).
     *
     * @return [ConnectionDetails] on success, `null` otherwise.
     */
    fun parseIntent(intent: Intent): ConnectionDetails? {
        Log.d(TAG, "parseIntent: action=${intent.action}")
        return when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> parseNdefDiscoveredIntent(intent)
            NfcAdapter.ACTION_TECH_DISCOVERED -> parseTechDiscoveredIntent(intent)
            else -> null
        }
    }

    /** Resets the reader state to [NfcReaderState.Idle]. */
    fun reset() {
        _state.value = NfcReaderState.Idle
    }

    // ─── Private implementation ───────────────────────────────────────────────

    /** Callback invoked by the NFC framework (background thread) in reader mode. */
    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        Log.d(TAG, "Tag discovered: id=${tag.id?.toHex() ?: "unknown"}")
        val details = readHceTag(tag)
        if (details != null) {
            Log.d(TAG, "Connection details received via reader mode: $details")
            _state.value = NfcReaderState.Connected(details)
        } else {
            Log.w(TAG, "Failed to read Boop NFC payload from tag")
            _state.value = NfcReaderState.Error("Could not read Boop payload from NFC tap")
        }
    }

    /**
     * Performs the ISO-DEP APDU exchange with the Sender's [BoopHceService]
     * and returns the parsed [ConnectionDetails], or `null` on failure.
     */
    private fun readHceTag(tag: Tag): ConnectionDetails? {
        val isoDep = IsoDep.get(tag) ?: run {
            Log.w(TAG, "Tag does not support ISO-DEP")
            return null
        }
        return try {
            isoDep.connect()
            isoDep.timeout = ISO_DEP_TIMEOUT_MS
            Log.d(TAG, "IsoDep connected, transmitting SELECT AID")

            val response = isoDep.transceive(buildSelectAidApdu())
            Log.d(TAG, "SELECT AID response (${response.size} bytes): ${response.toHex()}")

            if (response.size < 2) {
                Log.w(TAG, "Response too short (${response.size} bytes)")
                return null
            }
            val sw1 = response[response.size - 2]
            val sw2 = response[response.size - 1]
            if (sw1 != 0x90.toByte() || sw2 != 0x00.toByte()) {
                Log.w(TAG, "Unexpected status word: %02X%02X".format(sw1, sw2))
                return null
            }

            val ndefBytes = response.copyOfRange(0, response.size - 2)
            parseNdefBytes(ndefBytes)
        } catch (e: Exception) {
            Log.e(TAG, "ISO-DEP APDU exchange failed", e)
            null
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing IsoDep", e)
            }
        }
    }

    private fun parseNdefDiscoveredIntent(intent: Intent): ConnectionDetails? {
        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?: return null
        for (raw in rawMessages) {
            val details = parseNdefMessage(raw as NdefMessage)
            if (details != null) return details
        }
        return null
    }

    private fun parseTechDiscoveredIntent(intent: Intent): ConnectionDetails? {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
        return tag?.let { readHceTag(it) }
    }

    /**
     * Parses raw NDEF message bytes into [ConnectionDetails].
     * `internal` visibility allows direct use in unit tests.
     */
    internal fun parseNdefBytes(ndefBytes: ByteArray): ConnectionDetails? {
        return try {
            parseNdefMessage(NdefMessage(ndefBytes))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to construct NdefMessage from bytes", e)
            null
        }
    }

    private fun parseNdefMessage(ndefMessage: NdefMessage): ConnectionDetails? {
        for (record in ndefMessage.records) {
            if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                val mimeType = String(record.type, Charsets.US_ASCII)
                Log.d(TAG, "NDEF record — MIME: $mimeType")
                if (mimeType == BoopHceService.BOOP_MIME_TYPE) {
                    return parseJsonPayload(String(record.payload, Charsets.UTF_8))
                }
            }
        }
        Log.w(TAG, "No Boop MIME record found in NDEF message")
        return null
    }

    private fun parseJsonPayload(json: String): ConnectionDetails? {
        return try {
            Log.d(TAG, "Parsing JSON payload: $json")
            val obj = JSONObject(json)
            ConnectionDetails(mac = obj.getString("mac"), port = obj.getInt("port"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON payload: $json", e)
            null
        }
    }

    // ─── Companion helpers ────────────────────────────────────────────────────

    companion object {
        private const val ISO_DEP_TIMEOUT_MS = 5_000  // 5 seconds

        /**
         * Builds the ISO 7816-4 SELECT AID APDU for the Boop proprietary AID.
         *
         * Format: `CLA=0x00 INS=0xA4 P1=0x04 P2=0x00 Lc=[len] [AID bytes] Le=0x00`
         */
        fun buildSelectAidApdu(): ByteArray =
            byteArrayOf(
                0x00,                              // CLA
                0xA4.toByte(),                     // INS = SELECT
                0x04,                              // P1  = select by AID
                0x00,                              // P2
                BoopHceService.BOOP_AID.size.toByte()  // Lc
            ) + BoopHceService.BOOP_AID + byteArrayOf(0x00)  // Le = 0

        private fun ByteArray.toHex(): String =
            joinToString(separator = "") { "%02X".format(it) }
    }
}
