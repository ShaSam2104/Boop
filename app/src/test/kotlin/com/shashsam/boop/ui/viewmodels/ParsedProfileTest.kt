package com.shashsam.boop.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedProfileTest {

    @Test
    fun `old format bare array parses items with empty bio`() {
        val json = """[{"type":"link","label":"GitHub","value":"https://github.com/test","size":"half","sortOrder":0}]"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertEquals("GitHub", result.items[0].label)
        assertEquals("", result.bio)
    }

    @Test
    fun `envelope format parses items and bio`() {
        val json = """{"items":[{"type":"email","label":"Work","value":"a@b.com","size":"full","sortOrder":0}],"bio":"hello world"}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertEquals("Work", result.items[0].label)
        assertEquals("hello world", result.bio)
    }

    @Test
    fun `envelope with empty bio`() {
        val json = """{"items":[],"bio":""}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertTrue(result.items.isEmpty())
        assertEquals("", result.bio)
    }

    @Test
    fun `null json returns empty ParsedProfile`() {
        val result = ProfileViewModel.parseProfileJson(null)
        assertTrue(result.items.isEmpty())
        assertEquals("", result.bio)
    }

    @Test
    fun `blank json returns empty ParsedProfile`() {
        val result = ProfileViewModel.parseProfileJson("  ")
        assertTrue(result.items.isEmpty())
        assertEquals("", result.bio)
    }

    @Test
    fun `envelope missing bio key returns empty bio`() {
        val json = """{"items":[{"type":"link","label":"Site","value":"https://example.com","size":"half","sortOrder":0}]}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertEquals("", result.bio)
    }
}
