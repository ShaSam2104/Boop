package com.shashsam.boop.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC Host-Based Card Emulation (HCE) service for the **Sender** device.
 *
 * ## Protocol
 * 1. The Receiver's NFC stack sends an ISO 7816 SELECT AID command for [BOOP_AID].
 * 2. This service responds with a serialised [NdefMessage] containing:
 *    - A custom MIME record (`application/com.shashsam.boop`) with a JSON payload
 *      carrying the Sender's Wi-Fi Direct MAC address and TCP listening port.
 *    - An Android Application Record (AAR) for `com.shashsam.boop` so that the
 *      Receiver's device opens the app — or redirects to the Play Store if not installed.
 * 3. The NDEF bytes are followed by the ISO 7816 success status word `90 00`.
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

        private val SELECT_OK_SW = byteArrayOf(0x90.toByte(), 0x00)

        /** Returned for unrecognised commands (INS NOT SUPPORTED per ISO 7816-4). */
        private val UNKNOWN_CMD_SW = byteArrayOf(0x6D.toByte(), 0x00)

        private const val SELECT_INS: Byte = 0xA4.toByte()

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
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu.toHex()}")

        // Minimum length: CLA INS P1 P2 Lc
        if (commandApdu.size < 5) {
            Log.w(TAG, "APDU too short (${commandApdu.size} bytes) — returning error SW")
            return UNKNOWN_CMD_SW
        }

        val ins = commandApdu[1]
        if (ins != SELECT_INS) {
            Log.w(TAG, "Unknown INS 0x${"%02X".format(ins)} — returning error SW")
            return UNKNOWN_CMD_SW
        }

        val aidLength = commandApdu[4].toInt() and 0xFF
        if (commandApdu.size < 5 + aidLength) {
            Log.w(TAG, "APDU truncated — returning error SW")
            return UNKNOWN_CMD_SW
        }

        val receivedAid = commandApdu.copyOfRange(5, 5 + aidLength)
        if (!receivedAid.contentEquals(BOOP_AID)) {
            Log.w(TAG, "AID mismatch: ${receivedAid.toHex()} != ${BOOP_AID.toHex()}")
            return UNKNOWN_CMD_SW
        }

        Log.d(TAG, "AID matched — building NDEF payload for mac=$connectionMac port=$connectionPort")
        return try {
            val ndefBytes = buildNdefPayload()
            Log.d(TAG, "NDEF payload (${ndefBytes.size} bytes): ${ndefBytes.toHex()}")
            ndefBytes + SELECT_OK_SW
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build NDEF payload", e)
            UNKNOWN_CMD_SW
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated reason=$reason")
    }

    /**
     * Builds a serialised [NdefMessage] containing:
     * 1. A MIME record with the Wi-Fi Direct MAC and TCP port as a JSON string.
     * 2. An Android Application Record (AAR) ensuring Boop is launched on the Receiver.
     */
    private fun buildNdefPayload(): ByteArray {
        val json = """{"mac":"$connectionMac","port":$connectionPort}"""
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
