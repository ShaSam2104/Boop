package com.shashsam.boop.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject

/**
 * Unit tests for [NfcReader] NDEF payload parsing.
 *
 * These tests use a raw [NdefMessage] constructed in-process (no NFC hardware required)
 * to verify that [NfcReader.parseNdefBytes] correctly extracts [ConnectionDetails] from
 * the wire format produced by [BoopHceService.buildNdefPayload].
 *
 * Proof mandate: these tests confirm the full round-trip:
 * `JSON payload → NdefMessage bytes → ConnectionDetails`.
 */
class NfcReaderTest {

    /**
     * [NfcReader] is instantiated with a null adapter so it can be constructed without
     * an Android context / NFC hardware.
     */
    private val reader = NfcReader(nfcAdapter = null)

    // ─── Happy-path tests ─────────────────────────────────────────────────────

    @Test
    fun `parseNdefBytes extracts mac and port from valid MIME record`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val port = 8765
        val ndefBytes = buildBoopNdefBytes(mac, port)

        val result = reader.parseNdefBytes(ndefBytes)

        assertNotNull("parseNdefBytes should return non-null ConnectionDetails", result)
        assertEquals("MAC must match", mac, result!!.mac)
        assertEquals("Port must match", port, result.port)
    }

    @Test
    fun `parseNdefBytes handles default dummy values from Gemini prompt`() {
        // The exact payload described in the problem statement
        val mac = "00:00:00:00:00:00"
        val port = 8765
        val ndefBytes = buildBoopNdefBytes(mac, port)

        val result = reader.parseNdefBytes(ndefBytes)

        assertNotNull(result)
        assertEquals(mac, result!!.mac)
        assertEquals(port, result.port)
    }

    @Test
    fun `parseNdefBytes extracts details when AAR record appears before MIME record`() {
        // Receiver should still parse even if AAR comes first in the message
        val mac = "11:22:33:44:55:66"
        val port = 9000
        val mimeRecord = NdefRecord.createMime(
            BoopHceService.BOOP_MIME_TYPE,
            """{"mac":"$mac","port":$port}""".toByteArray(Charsets.UTF_8)
        )
        val aarRecord = NdefRecord.createApplicationRecord("com.shashsam.boop")
        // Put AAR first
        val ndefBytes = NdefMessage(arrayOf(aarRecord, mimeRecord)).toByteArray()

        val result = reader.parseNdefBytes(ndefBytes)

        assertNotNull(result)
        assertEquals(mac, result!!.mac)
        assertEquals(port, result.port)
    }

    @Test
    fun `parseNdefBytes handles extra fields in JSON payload gracefully`() {
        val mac = "CC:DD:EE:FF:00:11"
        val port = 1234
        // JSON with extra unexpected fields — should still parse known fields
        val json = """{"mac":"$mac","port":$port,"extra":"ignored"}"""
        val mimeRecord = NdefRecord.createMime(
            BoopHceService.BOOP_MIME_TYPE,
            json.toByteArray(Charsets.UTF_8)
        )
        val ndefBytes = NdefMessage(arrayOf(mimeRecord)).toByteArray()

        val result = reader.parseNdefBytes(ndefBytes)

        assertNotNull(result)
        assertEquals(mac, result!!.mac)
        assertEquals(port, result.port)
    }

    // ─── Error / edge-case tests ──────────────────────────────────────────────

    @Test
    fun `parseNdefBytes returns null for empty byte array`() {
        val result = reader.parseNdefBytes(ByteArray(0))
        assertNull("Should return null for empty bytes", result)
    }

    @Test
    fun `parseNdefBytes returns null when no Boop MIME record present`() {
        // NDEF message with only an AAR record
        val aarOnly = NdefMessage(
            arrayOf(NdefRecord.createApplicationRecord("com.shashsam.boop"))
        ).toByteArray()

        val result = reader.parseNdefBytes(aarOnly)
        assertNull("Should return null when there is no Boop MIME record", result)
    }

    @Test
    fun `parseNdefBytes returns null for malformed JSON in MIME record`() {
        val badJson = "NOT_VALID_JSON"
        val mimeRecord = NdefRecord.createMime(
            BoopHceService.BOOP_MIME_TYPE,
            badJson.toByteArray(Charsets.UTF_8)
        )
        val ndefBytes = NdefMessage(arrayOf(mimeRecord)).toByteArray()

        val result = reader.parseNdefBytes(ndefBytes)
        assertNull("Should return null for malformed JSON", result)
    }

    @Test
    fun `parseNdefBytes returns null for JSON missing required fields`() {
        val incompleteJson = """{"mac":"AA:BB:CC:DD:EE:FF"}"""  // missing "port"
        val mimeRecord = NdefRecord.createMime(
            BoopHceService.BOOP_MIME_TYPE,
            incompleteJson.toByteArray(Charsets.UTF_8)
        )
        val ndefBytes = NdefMessage(arrayOf(mimeRecord)).toByteArray()

        val result = reader.parseNdefBytes(ndefBytes)
        assertNull("Should return null when JSON is missing required fields", result)
    }

    @Test
    fun `parseNdefBytes returns null for wrong MIME type`() {
        val mimeRecord = NdefRecord.createMime(
            "application/x-wrong-mime",
            """{"mac":"AA:BB:CC:DD:EE:FF","port":8765}""".toByteArray(Charsets.UTF_8)
        )
        val ndefBytes = NdefMessage(arrayOf(mimeRecord)).toByteArray()

        val result = reader.parseNdefBytes(ndefBytes)
        assertNull("Should return null for unrecognised MIME type", result)
    }

    // ─── SELECT AID APDU builder test ─────────────────────────────────────────

    @Test
    fun `buildSelectAidApdu produces correctly structured APDU`() {
        val apdu = NfcReader.buildSelectAidApdu()

        // CLA=0x00 INS=0xA4 P1=0x04 P2=0x00 Lc=[6] [6 AID bytes] Le=0x00
        val expectedLength = 5 + BoopHceService.BOOP_AID.size + 1
        assertEquals("APDU length", expectedLength, apdu.size)
        assertEquals("CLA", 0x00.toByte(), apdu[0])
        assertEquals("INS = SELECT", 0xA4.toByte(), apdu[1])
        assertEquals("P1 = select by AID", 0x04.toByte(), apdu[2])
        assertEquals("P2", 0x00.toByte(), apdu[3])
        assertEquals("Lc = AID length", BoopHceService.BOOP_AID.size.toByte(), apdu[4])

        val aidInApdu = apdu.copyOfRange(5, 5 + BoopHceService.BOOP_AID.size)
        assert(BoopHceService.BOOP_AID.contentEquals(aidInApdu)) {
            "AID bytes in APDU must match BOOP_AID"
        }
        assertEquals("Le = 0", 0x00.toByte(), apdu.last())
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a [NdefMessage] byte array in the same format as [BoopHceService]:
     * one Boop MIME record + one AAR.
     */
    private fun buildBoopNdefBytes(mac: String, port: Int): ByteArray {
        val json = JSONObject().apply {
            put("mac", mac)
            put("port", port)
        }.toString()
        val mimeRecord = NdefRecord.createMime(
            BoopHceService.BOOP_MIME_TYPE,
            json.toByteArray(Charsets.UTF_8)
        )
        val aarRecord = NdefRecord.createApplicationRecord("com.shashsam.boop")
        return NdefMessage(arrayOf(mimeRecord, aarRecord)).toByteArray()
    }
}
