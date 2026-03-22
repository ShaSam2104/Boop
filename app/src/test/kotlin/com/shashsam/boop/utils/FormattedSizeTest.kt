package com.shashsam.boop.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattedSizeTest {

    @Test
    fun `zero bytes`() {
        assertEquals("0 B", 0L.toFormattedSize())
    }

    @Test
    fun `small byte values`() {
        assertEquals("512 B", 512L.toFormattedSize())
        assertEquals("1 B", 1L.toFormattedSize())
        assertEquals("1023 B", 1023L.toFormattedSize())
    }

    @Test
    fun `kilobyte boundary`() {
        assertEquals("1.0 KB", 1024L.toFormattedSize())
    }

    @Test
    fun `kilobyte values`() {
        assertEquals("1.5 KB", 1536L.toFormattedSize())
        assertEquals("512.0 KB", (512L * 1024).toFormattedSize())
    }

    @Test
    fun `megabyte boundary`() {
        assertEquals("1.0 MB", (1024L * 1024).toFormattedSize())
    }

    @Test
    fun `megabyte values`() {
        assertEquals("12.3 MB", (12_910_284L).toFormattedSize())  // ~12.3 * 1048576
        assertEquals("500.0 MB", (500L * 1024 * 1024).toFormattedSize())
    }

    @Test
    fun `gigabyte boundary`() {
        assertEquals("1.0 GB", (1024L * 1024 * 1024).toFormattedSize())
    }

    @Test
    fun `gigabyte values`() {
        assertEquals("2.0 GB", (2L * 1024 * 1024 * 1024).toFormattedSize())
        assertEquals("1.5 GB", (1536L * 1024 * 1024).toFormattedSize())
    }
}
