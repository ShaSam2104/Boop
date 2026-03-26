package com.shashsam.boop.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class FriendExchangeTest {

    @Test
    fun `friend request round-trip without pic`() {
        val profile = ProfileData(
            displayName = "TestUser",
            profileItemsJson = """[{"type":"link","label":"GitHub","value":"https://github.com/test"}]""",
            profilePicBytes = null
        )

        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        sendFriendRequest(out, profile)

        val inp = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        val result = readFriendRequest(inp)

        assertNotNull(result)
        assertEquals("TestUser", result!!.displayName)
        assertEquals(profile.profileItemsJson, result.profileItemsJson)
        assertNull(result.profilePicBytes)
    }

    @Test
    fun `friend request round-trip with pic`() {
        val picBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val profile = ProfileData(
            displayName = "PicUser",
            profileItemsJson = "[]",
            profilePicBytes = picBytes
        )

        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        sendFriendRequest(out, profile)

        val inp = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        val result = readFriendRequest(inp)

        assertNotNull(result)
        assertEquals("PicUser", result!!.displayName)
        assertNotNull(result.profilePicBytes)
        assertEquals(5, result.profilePicBytes!!.size)
        assertTrue(picBytes.contentEquals(result.profilePicBytes!!))
    }

    @Test
    fun `friend ACK response round-trip`() {
        val senderProfile = ProfileData("Sender", "[]", null)

        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        sendFriendResponse(out, accepted = true, profile = senderProfile)

        val inp = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        val (accepted, profile) = readFriendResponse(inp)

        assertTrue(accepted)
        assertNotNull(profile)
        assertEquals("Sender", profile!!.displayName)
    }

    @Test
    fun `friend NAK response round-trip`() {
        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)
        sendFriendResponse(out, accepted = false, profile = null)

        val inp = DataInputStream(ByteArrayInputStream(buffer.toByteArray()))
        val (accepted, profile) = readFriendResponse(inp)

        assertFalse(accepted)
        assertNull(profile)
    }

    @Test
    fun `magic constants are correct`() {
        assertEquals("BOOP_FRIEND\n", FRIEND_REQUEST_MAGIC)
        assertEquals("BOOP_FRIEND_ACK\n", FRIEND_ACK_MAGIC)
        assertEquals("BOOP_FRIEND_NAK\n", FRIEND_NAK_MAGIC)
    }

    @Test
    fun `ACK and NAK magic are same length`() {
        assertEquals(
            FRIEND_ACK_MAGIC.toByteArray(Charsets.UTF_8).size,
            FRIEND_NAK_MAGIC.toByteArray(Charsets.UTF_8).size
        )
    }

    @Test
    fun `ProfileData equality with null pic`() {
        val a = ProfileData("Name", "[]", null)
        val b = ProfileData("Name", "[]", null)
        assertEquals(a, b)
    }

    @Test
    fun `ProfileData equality with pic bytes`() {
        val bytes = byteArrayOf(1, 2, 3)
        val a = ProfileData("Name", "[]", bytes)
        val b = ProfileData("Name", "[]", bytes.clone())
        assertEquals(a, b)
    }
}
