package com.shashsam.boop.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedProfileTest {

    @Test
    fun `old format bare array parses items with empty answers`() {
        val json = """[{"type":"link","label":"GitHub","value":"https://github.com/test","size":"half","sortOrder":0}]"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertEquals("GitHub", result.items[0].label)
        assertTrue(result.answers.isEmpty())
    }

    @Test
    fun `new envelope format parses items and answers`() {
        val json = """{"items":[{"type":"email","label":"Work","value":"a@b.com","size":"full","sortOrder":0}],"answers":{"prefer_contact":"Texting","reply_speed":"Fast"}}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertEquals("Work", result.items[0].label)
        assertEquals(2, result.answers.size)
        assertEquals("Texting", result.answers["prefer_contact"])
        assertEquals("Fast", result.answers["reply_speed"])
    }

    @Test
    fun `new envelope with empty answers`() {
        val json = """{"items":[],"answers":{}}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertTrue(result.items.isEmpty())
        assertTrue(result.answers.isEmpty())
    }

    @Test
    fun `null json returns empty ParsedProfile`() {
        val result = ProfileViewModel.parseProfileJson(null)
        assertTrue(result.items.isEmpty())
        assertTrue(result.answers.isEmpty())
    }

    @Test
    fun `blank json returns empty ParsedProfile`() {
        val result = ProfileViewModel.parseProfileJson("  ")
        assertTrue(result.items.isEmpty())
        assertTrue(result.answers.isEmpty())
    }

    @Test
    fun `new envelope missing answers key returns empty answers`() {
        val json = """{"items":[{"type":"link","label":"Site","value":"https://example.com","size":"half","sortOrder":0}]}"""
        val result = ProfileViewModel.parseProfileJson(json)
        assertEquals(1, result.items.size)
        assertTrue(result.answers.isEmpty())
    }
}
