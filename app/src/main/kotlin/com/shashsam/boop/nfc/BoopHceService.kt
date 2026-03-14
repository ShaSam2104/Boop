package com.shashsam.boop.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC Host-Based Card Emulation (HCE) service.
 *
 * This service runs on the **Sender** device. When the Receiver taps their phone
 * against the Sender, the NFC stack routes the ISO 7816 SELECT AID command here.
 * We respond with a compact NDEF payload that contains:
 *  1. The Sender's Wi-Fi Direct MAC address and listening port.
 *  2. An Android Application Record (AAR) to ensure the app opens on the Receiver
 *     (or the Play Store if the app is not installed).
 *
 * NOTE: Full NDEF construction and Wi-Fi Direct integration will be wired up in
 * Phase 2. This stub satisfies the AndroidManifest `<service>` declaration.
 */
class BoopHceService : HostApduService() {

    companion object {
        private const val TAG = "BoopHceService"

        // Proprietary AID: 0xF0 prefix = proprietary range (ISO 7816)
        private val BOOP_AID = byteArrayOf(
            0xF0.toByte(), 0x42, 0x6F, 0x6F, 0x70, 0x01
        )
        private val SELECT_OK_SW = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_CMD_SW = byteArrayOf(0x00, 0x00)
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu.toHex()}")
        // TODO Phase 2: Parse SELECT AID and return NDEF Wi-Fi Direct handshake payload
        return SELECT_OK_SW
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated reason=$reason")
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02X".format(byte) }
}
