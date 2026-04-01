package com.shashsam.boop.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private fun sampleData() = BackupSerializer.BackupData(
        version = 1,
        exportedAt = "2026-04-01T12:00:00Z",
        profile = BackupSerializer.ProfileBackup(
            ulid = "01ABCDEF1234567890ABCDEF",
            displayName = "Test Device",
            profilePicBase64 = BackupSerializer.encodeToBase64("fake-pic".toByteArray()),
            items = listOf(
                BackupSerializer.ProfileItemBackup("link", "GitHub", "https://github.com/test", "half", 0),
                BackupSerializer.ProfileItemBackup("email", "Work", "test@example.com", "full", 1)
            )
        ),
        friends = listOf(
            BackupSerializer.FriendBackup(
                ulid = "01FRIEND1234567890ABCDEF",
                displayName = "Friend One",
                firstSeenTimestamp = 1000L,
                lastSeenTimestamp = 2000L,
                lastInteractionTimestamp = 3000L,
                transferCount = 5,
                profileJson = """{"items":[]}""",
                profilePicBase64 = null
            )
        ),
        history = listOf(
            BackupSerializer.HistoryBackup(
                fileName = "photo.jpg",
                fileSize = 1024L,
                mimeType = "image/jpeg",
                timestamp = 5000L,
                wasSender = true,
                peerUlid = "01FRIEND1234567890ABCDEF"
            ),
            BackupSerializer.HistoryBackup(
                fileName = "doc.pdf",
                fileSize = 2048L,
                mimeType = "application/pdf",
                timestamp = 6000L,
                wasSender = false,
                peerUlid = null
            )
        )
    )

    @Test
    fun `serialize and deserialize round trip`() {
        val data = sampleData()
        val bytes = BackupSerializer.serialize(data)
        val parsed = BackupSerializer.deserialize(bytes)

        assertEquals(data.version, parsed.version)
        assertEquals(data.exportedAt, parsed.exportedAt)
        assertEquals(data.profile.ulid, parsed.profile.ulid)
        assertEquals(data.profile.displayName, parsed.profile.displayName)
        assertEquals(data.profile.profilePicBase64, parsed.profile.profilePicBase64)
        assertEquals(data.profile.items.size, parsed.profile.items.size)
        assertEquals(data.profile.items[0].type, parsed.profile.items[0].type)
        assertEquals(data.profile.items[0].label, parsed.profile.items[0].label)
        assertEquals(data.profile.items[1].value, parsed.profile.items[1].value)
    }

    @Test
    fun `friends round trip`() {
        val data = sampleData()
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))

        assertEquals(1, parsed.friends.size)
        val friend = parsed.friends[0]
        assertEquals("01FRIEND1234567890ABCDEF", friend.ulid)
        assertEquals("Friend One", friend.displayName)
        assertEquals(1000L, friend.firstSeenTimestamp)
        assertEquals(2000L, friend.lastSeenTimestamp)
        assertEquals(3000L, friend.lastInteractionTimestamp)
        assertEquals(5, friend.transferCount)
        assertEquals("""{"items":[]}""", friend.profileJson)
        assertNull(friend.profilePicBase64)
    }

    @Test
    fun `history round trip`() {
        val data = sampleData()
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))

        assertEquals(2, parsed.history.size)
        val first = parsed.history[0]
        assertEquals("photo.jpg", first.fileName)
        assertEquals(1024L, first.fileSize)
        assertEquals("image/jpeg", first.mimeType)
        assertEquals(5000L, first.timestamp)
        assertTrue(first.wasSender)
        assertEquals("01FRIEND1234567890ABCDEF", first.peerUlid)

        val second = parsed.history[1]
        assertNull(second.peerUlid)
    }

    @Test
    fun `base64 encode and decode round trip`() {
        val original = "Hello, world! 🌍".toByteArray()
        val encoded = BackupSerializer.encodeToBase64(original)
        val decoded = BackupSerializer.decodeFromBase64(encoded)
        assertTrue(original.contentEquals(decoded))
    }

    @Test
    fun `empty friends and history`() {
        val data = BackupSerializer.BackupData(
            version = 1,
            exportedAt = "2026-04-01T00:00:00Z",
            profile = BackupSerializer.ProfileBackup(
                ulid = "01TEST",
                displayName = "Empty",
                profilePicBase64 = null,
                items = emptyList()
            ),
            friends = emptyList(),
            history = emptyList()
        )

        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))
        assertEquals(0, parsed.friends.size)
        assertEquals(0, parsed.history.size)
        assertEquals(0, parsed.profile.items.size)
        assertNull(parsed.profile.profilePicBase64)
    }

    @Test
    fun `null profilePicBase64 survives round trip`() {
        val data = sampleData().copy(
            profile = sampleData().profile.copy(profilePicBase64 = null)
        )
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))
        assertNull(parsed.profile.profilePicBase64)
    }

    @Test(expected = UnsupportedVersionException::class)
    fun `future version throws UnsupportedVersionException`() {
        val json = """{"version":99,"exportedAt":"","profile":{"ulid":"","displayName":"","items":[]},"friends":[],"history":[]}"""
        BackupSerializer.deserialize(json.toByteArray())
    }

    @Test
    fun `full crypto plus serialization round trip`() {
        val data = sampleData()
        val plaintext = BackupSerializer.serialize(data)
        val encrypted = BackupCrypto.encrypt(plaintext, "my-password")
        val decrypted = BackupCrypto.decrypt(encrypted, "my-password")
        val parsed = BackupSerializer.deserialize(decrypted)

        assertEquals(data.version, parsed.version)
        assertEquals(data.profile.displayName, parsed.profile.displayName)
        assertEquals(data.friends.size, parsed.friends.size)
        assertEquals(data.history.size, parsed.history.size)
    }

    @Test
    fun `bio round trip`() {
        val data = sampleData().copy(
            profile = sampleData().profile.copy(bio = "hello world")
        )
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))
        assertEquals("hello world", parsed.profile.bio)
    }

    @Test
    fun `empty bio backward compatible`() {
        val data = sampleData().copy(
            profile = sampleData().profile.copy(bio = "")
        )
        val parsed = BackupSerializer.deserialize(BackupSerializer.serialize(data))
        assertEquals("", parsed.profile.bio)
    }
}
