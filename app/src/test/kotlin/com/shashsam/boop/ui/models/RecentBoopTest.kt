package com.shashsam.boop.ui.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentBoopTest {

    @Test
    fun `RecentBoop stores all constructor fields`() {
        val boop = RecentBoop(
            fileName = "photo.jpg",
            fileSize = 2_048_000L,
            mimeType = "image/jpeg",
            timestamp = 1_700_000_000_000L,
            wasSender = true
        )
        assertEquals("photo.jpg", boop.fileName)
        assertEquals(2_048_000L, boop.fileSize)
        assertEquals("image/jpeg", boop.mimeType)
        assertEquals(1_700_000_000_000L, boop.timestamp)
        assertTrue("wasSender should be true", boop.wasSender)
    }

    @Test
    fun `RecentBoop receiver has wasSender false`() {
        val boop = RecentBoop("doc.pdf", 500L, "application/pdf", 0L, wasSender = false)
        assertFalse("wasSender should be false for receiver", boop.wasSender)
    }

    @Test
    fun `RecentBoop equality works for identical data`() {
        val a = RecentBoop("a.zip", 100L, "application/zip", 999L, true)
        val b = RecentBoop("a.zip", 100L, "application/zip", 999L, true)
        assertEquals(a, b)
    }

    @Test
    fun `RecentBoop inequality when fileName differs`() {
        val a = RecentBoop("a.zip", 100L, "", 0L, true)
        val b = RecentBoop("b.zip", 100L, "", 0L, true)
        assertNotEquals(a, b)
    }

    @Test
    fun `RecentBoop copy changes only specified field`() {
        val original = RecentBoop("file.txt", 1024L, "text/plain", 1L, false)
        val copied = original.copy(wasSender = true)
        assertEquals("file.txt", copied.fileName)
        assertEquals(1024L, copied.fileSize)
        assertTrue(copied.wasSender)
    }
}
