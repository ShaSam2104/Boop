package com.shashsam.boop.ui.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEntryTest {

    @Test
    fun `default LogEntry is not an error`() {
        val entry = LogEntry("Test message")
        assertEquals("Test message", entry.message)
        assertFalse("Default isError should be false", entry.isError)
    }

    @Test
    fun `error LogEntry has isError true`() {
        val entry = LogEntry("Error occurred", isError = true)
        assertTrue("isError should be true", entry.isError)
    }

    @Test
    fun `LogEntry equality works correctly`() {
        val a = LogEntry("msg", false)
        val b = LogEntry("msg", false)
        assertEquals("Same content should be equal", a, b)
    }
}
