package com.shashsam.boop.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC Host-Based Card Emulation (HCE) service for the **Sender** device.
 *
 * Implements two protocols:
 *
 * ### 1. NDEF Type 4 Tag (ISO 14443-4 / ISO 7816-4)
 * Registered for NDEF Application AID `D2760000850101`. The Android NFC stack on the
 * Receiver reads the NDEF message during tag discovery, so `NDEF_DISCOVERED` fires
 * immediately — even on cold start.
 *
 * Sequence: SELECT NDEF AID → SELECT CC file (`E103`) → READ BINARY CC →
 * SELECT NDEF file (`E104`) → READ BINARY NDEF → payload delivered.
 *
 * ### 2. Proprietary Boop AID (legacy / foreground reader mode)
 * Registered for [BOOP_AID] (`F0426F6F7001`). When the Receiver app is in the
 * foreground with reader mode active, [NfcReader] selects this AID directly and
 * receives the NDEF bytes + `9000` in one shot.
 *
 * ## Setting connection details
 * Before HCE broadcasts, [WifiDirectManager][com.shashsam.boop.wifi.WifiDirectManager]
 * must set [connectionMac] and [connectionPort] via the companion object.
 */
class BoopHceService : HostApduService() {

    companion object {
        private const val TAG = "BoopHceService"

        /** MIME type used in the custom NDEF record. */
        const val BOOP_MIME_TYPE = "application/com.shashsam.boop"

        /** Default TCP port the Sender's ServerSocket listens on. */
        const val DEFAULT_PORT = 8765

        /** Proprietary AID — 0xF0 prefix denotes the proprietary range (ISO 7816-5). */
        val BOOP_AID: ByteArray = byteArrayOf(
            0xF0.toByte(), 0x42, 0x6F, 0x6F, 0x70, 0x01
        )

        /** NDEF Application AID per NFC Forum Type 4 Tag specification. */
        val NDEF_AID: ByteArray = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )

        /** Capability Container file identifier. */
        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)

        /** NDEF file identifier. */
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        private val SELECT_OK_SW = byteArrayOf(0x90.toByte(), 0x00)

        /** Returned for unrecognised commands (INS NOT SUPPORTED per ISO 7816-4). */
        private val UNKNOWN_CMD_SW = byteArrayOf(0x6D.toByte(), 0x00)

        /** FILE NOT FOUND status word. */
        private val FILE_NOT_FOUND_SW = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        private const val SELECT_INS: Byte = 0xA4.toByte()
        private const val READ_BINARY_INS: Byte = 0xB0.toByte()

        /**
         * Capability Container (15 bytes) for NDEF Type 4 Tag v2.0.
         *
         * Layout:
         * - 00 0F: CC length (15)
         * - 20:    Mapping version 2.0
         * - 00 FF: Max R-APDU data size (255)
         * - 00 FF: Max C-APDU data size (255)
         * - 04 06: NDEF File Control TLV (type=04, length=06)
         *   - E1 04: NDEF file identifier
         *   - 04 00: Max NDEF file size (1024)
         *   - 00:    Read access open
         *   - FF:    Write access denied
         */
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,                         // CC length
            0x20,                               // Mapping version 2.0
            0x00, 0xFF.toByte(),                // Max R-APDU
            0x00, 0xFF.toByte(),                // Max C-APDU
            0x04, 0x06,                         // NDEF File Control TLV
            0xE1.toByte(), 0x04,                // NDEF file ID
            0x04, 0x00,                         // Max NDEF size (1024)
            0x00,                               // Read access: open
            0xFF.toByte()                       // Write access: denied
        )

        /**
         * Wi-Fi Direct MAC address of this device.
         * Set by [WifiDirectManager][com.shashsam.boop.wifi.WifiDirectManager]
         * once the group is created.
         */
        @Volatile var connectionMac: String = "00:00:00:00:00:00"

        /**
         * TCP port the Sender's ServerSocket listens on.
         * Set alongside [connectionMac].
         */
        @Volatile var connectionPort: Int = DEFAULT_PORT

        /**
         * Wi-Fi Direct group SSID (e.g. "DIRECT-xx-DeviceName").
         * Set by [WifiDirectManager][com.shashsam.boop.wifi.WifiDirectManager]
         * once the group is created.
         */
        @Volatile var connectionSsid: String = ""

        /**
         * Pre-shared key or connection token for the Wi-Fi Direct group.
         * Set alongside [connectionSsid].
         */
        @Volatile var connectionToken: String = ""

        /**
         * Number of files to transfer. Set before multi-file sends.
         * Defaults to 1 for single-file transfers.
         */
        @Volatile var connectionFileCount: Int = 1

        /**
         * Sender's display name from app settings.
         * Transmitted to Receiver so friends list shows the chosen name.
         */
        @Volatile var connectionDisplayName: String = ""

        /**
         * Connection type: "file" for file transfers, "profile" for NFC profile sharing.
         */
        @Volatile var connectionType: String = "file"

        /**
         * Sender's ULID (persistent unique identity).
         * Set from SharedPreferences before HCE broadcasts.
         */
        @Volatile var connectionUlid: String = ""
    }

    /** Which application AID was selected. */
    private enum class SelectedApp { NONE, BOOP, NDEF_TAG }

    /** Which file is selected within the NDEF Tag application. */
    private enum class SelectedFile { NONE, CC, NDEF }

    private var selectedApp = SelectedApp.NONE
    private var selectedFile = SelectedFile.NONE

    /** Cached NDEF file bytes: [2-byte length][NDEF message bytes]. */
    private var ndefFileBytes: ByteArray? = null

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu.toHex()}")

        if (commandApdu.size < 4) {
            Log.w(TAG, "APDU too short (${commandApdu.size} bytes) — returning error SW")
            return UNKNOWN_CMD_SW
        }

        val ins = commandApdu[1]
        return when (ins) {
            SELECT_INS -> handleSelect(commandApdu)
            READ_BINARY_INS -> handleReadBinary(commandApdu)
            else -> {
                Log.w(TAG, "Unknown INS 0x${"%02X".format(ins)} — returning error SW")
                UNKNOWN_CMD_SW
            }
        }
    }

    /**
     * Handles SELECT commands (INS = A4).
     * - P1=04: Select by AID (NDEF AID or Boop AID)
     * - P1=00, P2=0C: Select by file ID (CC or NDEF file)
     */
    private fun handleSelect(apdu: ByteArray): ByteArray {
        val p1 = apdu[2]
        val p2 = apdu[3]

        // SELECT by AID (P1=04)
        if (p1 == 0x04.toByte()) {
            if (apdu.size < 5) return UNKNOWN_CMD_SW
            val lc = apdu[4].toInt() and 0xFF
            if (apdu.size < 5 + lc) return UNKNOWN_CMD_SW
            val aid = apdu.copyOfRange(5, 5 + lc)

            return when {
                aid.contentEquals(NDEF_AID) -> {
                    Log.d(TAG, "SELECT NDEF Application AID")
                    selectedApp = SelectedApp.NDEF_TAG
                    selectedFile = SelectedFile.NONE
                    // Rebuild NDEF file on each new application select so payload is fresh
                    ndefFileBytes = buildNdefFile()
                    SELECT_OK_SW
                }
                aid.contentEquals(BOOP_AID) -> {
                    Log.d(TAG, "SELECT Boop AID — returning NDEF payload directly")
                    selectedApp = SelectedApp.BOOP
                    selectedFile = SelectedFile.NONE
                    try {
                        val ndefBytes = buildNdefPayload()
                        Log.d(TAG, "NDEF payload (${ndefBytes.size} bytes)")
                        ndefBytes + SELECT_OK_SW
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to build NDEF payload", e)
                        UNKNOWN_CMD_SW
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown AID: ${aid.toHex()}")
                    FILE_NOT_FOUND_SW
                }
            }
        }

        // SELECT by file ID (P1=00, P2=0C) — only valid within NDEF Tag app
        if (p1 == 0x00.toByte() && p2 == 0x0C.toByte() && selectedApp == SelectedApp.NDEF_TAG) {
            if (apdu.size < 7) return UNKNOWN_CMD_SW
            val fileId = apdu.copyOfRange(5, 7)
            return when {
                fileId.contentEquals(CC_FILE_ID) -> {
                    Log.d(TAG, "SELECT CC file (E103)")
                    selectedFile = SelectedFile.CC
                    SELECT_OK_SW
                }
                fileId.contentEquals(NDEF_FILE_ID) -> {
                    Log.d(TAG, "SELECT NDEF file (E104)")
                    selectedFile = SelectedFile.NDEF
                    SELECT_OK_SW
                }
                else -> {
                    Log.w(TAG, "Unknown file ID: ${fileId.toHex()}")
                    FILE_NOT_FOUND_SW
                }
            }
        }

        Log.w(TAG, "Unsupported SELECT variant P1=${"%02X".format(p1)} P2=${"%02X".format(p2)}")
        return UNKNOWN_CMD_SW
    }

    /**
     * Handles READ BINARY commands (INS = B0).
     * P1+P2 = offset, Le = expected length.
     * Returns the requested slice of the currently selected file.
     */
    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        if (selectedApp != SelectedApp.NDEF_TAG) {
            Log.w(TAG, "READ BINARY outside NDEF Tag app context")
            return UNKNOWN_CMD_SW
        }

        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le = if (apdu.size >= 5) apdu[4].toInt() and 0xFF else 0

        val fileData = when (selectedFile) {
            SelectedFile.CC -> {
                Log.d(TAG, "READ BINARY CC offset=$offset le=$le")
                CC_FILE
            }
            SelectedFile.NDEF -> {
                Log.d(TAG, "READ BINARY NDEF offset=$offset le=$le")
                ndefFileBytes ?: run {
                    Log.e(TAG, "NDEF file bytes not available")
                    return UNKNOWN_CMD_SW
                }
            }
            SelectedFile.NONE -> {
                Log.w(TAG, "READ BINARY with no file selected")
                return UNKNOWN_CMD_SW
            }
        }

        if (offset >= fileData.size) {
            // Return empty data with success — some readers read past end
            return SELECT_OK_SW
        }

        val end = minOf(offset + if (le == 0) 256 else le, fileData.size)
        return fileData.copyOfRange(offset, end) + SELECT_OK_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated reason=$reason")
        selectedApp = SelectedApp.NONE
        selectedFile = SelectedFile.NONE
    }

    /**
     * Builds the NDEF file contents: `[2-byte NDEF message length][NDEF message bytes]`.
     * This is the format expected by the Type 4 Tag READ BINARY sequence.
     */
    private fun buildNdefFile(): ByteArray {
        val ndefBytes = buildNdefPayload()
        val length = ndefBytes.size
        Log.d(TAG, "buildNdefFile: NDEF message $length bytes")
        return byteArrayOf(
            ((length shr 8) and 0xFF).toByte(),
            (length and 0xFF).toByte()
        ) + ndefBytes
    }

    /**
     * Builds a serialised [NdefMessage] containing:
     * 1. A MIME record with the Wi-Fi Direct MAC and TCP port as a JSON string.
     * 2. An Android Application Record (AAR) ensuring Boop is launched on the Receiver.
     */
    private fun buildNdefPayload(): ByteArray {
        val jsonObj = org.json.JSONObject().apply {
            put("mac", connectionMac)
            put("port", connectionPort)
            put("ssid", connectionSsid)
            put("token", connectionToken)
            put("fileCount", connectionFileCount)
            if (connectionDisplayName.isNotBlank()) {
                put("displayName", connectionDisplayName)
            }
            put("type", connectionType)
            if (connectionUlid.isNotBlank()) {
                put("ulid", connectionUlid)
            }
        }
        val json = jsonObj.toString()
        Log.d(TAG, "NDEF JSON payload: $json")

        val mimeRecord = NdefRecord.createMime(
            BOOP_MIME_TYPE,
            json.toByteArray(Charsets.UTF_8)
        )
        val aarRecord = NdefRecord.createApplicationRecord("com.shashsam.boop")
        return NdefMessage(arrayOf(mimeRecord, aarRecord)).toByteArray()
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02X".format(byte) }
}
