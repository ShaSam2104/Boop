package com.shashsam.boop.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [NfcReader] JSON payload parsing.
 *
 * These tests exercise [NfcReader.parsePayloadJson] directly, bypassing the
 * [android.nfc.NdefMessage] / [android.nfc.NdefRecord] classes which are stubs
 * in plain JVM unit tests. This proves the full JSON → [ConnectionDetails]
 * round-trip without requiring NFC hardware or an instrumented test environment.
 *
 * Proof mandate: these tests confirm that a mocked NDEF JSON payload is correctly
 * parsed and the extracted values match the expected output.
 */
class NfcReaderTest {

    // ─── Happy-path tests ─────────────────────────────────────────────────────

    @Test
    fun `parsePayloadJson extracts mac and port from valid JSON`() {
        val json = """{"mac":"AA:BB:CC:DD:EE:FF","port":8765}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull("Should return non-null ConnectionDetails", result)
        assertEquals("MAC must match", "AA:BB:CC:DD:EE:FF", result!!.mac)
        assertEquals("Port must match", 8765, result.port)
    }

    @Test
    fun `parsePayloadJson extracts ssid and token from full payload`() {
        val json = """{"mac":"AA:BB:CC:DD:EE:FF","port":8765,"ssid":"DIRECT-TEST","token":"12345"}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull("Should return non-null ConnectionDetails", result)
        assertEquals("MAC must match", "AA:BB:CC:DD:EE:FF", result!!.mac)
        assertEquals("Port must match", 8765, result.port)
        assertEquals("SSID must match", "DIRECT-TEST", result.ssid)
        assertEquals("Token must match", "12345", result.token)
    }

    @Test
    fun `parsePayloadJson defaults ssid and token to empty when absent`() {
        // Legacy payload with only mac and port
        val json = """{"mac":"11:22:33:44:55:66","port":9000}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull(result)
        assertEquals("11:22:33:44:55:66", result!!.mac)
        assertEquals(9000, result.port)
        assertEquals("SSID should default to empty", "", result.ssid)
        assertEquals("Token should default to empty", "", result.token)
    }

    @Test
    fun `parsePayloadJson matches exact prompt payload`() {
        // Exact JSON from the task specification
        val json = """{"mac":"00:00:00:00:00:00","port":8765,"ssid":"DIRECT-TEST","token":"12345"}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull(result)
        assertEquals("DIRECT-TEST", result!!.ssid)
        assertEquals("12345", result.token)
        assertEquals("00:00:00:00:00:00", result.mac)
        assertEquals(8765, result.port)
    }

    @Test
    fun `parsePayloadJson handles default dummy values`() {
        val json = """{"mac":"00:00:00:00:00:00","port":8765,"ssid":"","token":""}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull(result)
        assertEquals("00:00:00:00:00:00", result!!.mac)
        assertEquals(8765, result.port)
        assertEquals("", result.ssid)
        assertEquals("", result.token)
    }

    @Test
    fun `parsePayloadJson handles extra fields gracefully`() {
        val json = """{"mac":"CC:DD:EE:FF:00:11","port":1234,"ssid":"NET","token":"abc","extra":"ignored"}"""

        val result = NfcReader.parsePayloadJson(json)

        assertNotNull(result)
        assertEquals("CC:DD:EE:FF:00:11", result!!.mac)
        assertEquals(1234, result.port)
        assertEquals("NET", result.ssid)
        assertEquals("abc", result.token)
    }

    // ─── Error / edge-case tests ──────────────────────────────────────────────

    @Test
    fun `parsePayloadJson returns null for malformed JSON`() {
        val result = NfcReader.parsePayloadJson("NOT_VALID_JSON")
        assertNull("Should return null for malformed JSON", result)
    }

    @Test
    fun `parsePayloadJson returns null for empty string`() {
        val result = NfcReader.parsePayloadJson("")
        assertNull("Should return null for empty string", result)
    }

    @Test
    fun `parsePayloadJson returns null when mac is missing`() {
        val json = """{"port":8765,"ssid":"DIRECT-TEST","token":"12345"}"""
        val result = NfcReader.parsePayloadJson(json)
        assertNull("Should return null when mac is missing", result)
    }

    @Test
    fun `parsePayloadJson returns null when port is missing`() {
        val json = """{"mac":"AA:BB:CC:DD:EE:FF","ssid":"DIRECT-TEST","token":"12345"}"""
        val result = NfcReader.parsePayloadJson(json)
        assertNull("Should return null when port is missing", result)
    }

    @Test
    fun `parsePayloadJson returns null for empty JSON object`() {
        val result = NfcReader.parsePayloadJson("{}")
        assertNull("Should return null for empty JSON object", result)
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
}
