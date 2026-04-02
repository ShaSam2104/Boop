package com.shashsam.boop.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiStateTest {

    @Test
    fun `default SettingsUiState has all toggles enabled`() {
        val state = SettingsUiState()
        assertTrue("notifications default true", state.notificationsEnabled)
        assertTrue("vibration default true", state.vibrationEnabled)
        assertTrue("dark mode default true", state.darkModeEnabled)
    }

    @Test
    fun `default display name is My Device`() {
        val state = SettingsUiState()
        assertEquals("My Device", state.displayName)
    }

    @Test
    fun `copy toggles notifications off`() {
        val state = SettingsUiState().copy(notificationsEnabled = false)
        assertFalse(state.notificationsEnabled)
        // Other fields unchanged
        assertTrue(state.vibrationEnabled)
    }

    @Test
    fun `copy changes display name`() {
        val state = SettingsUiState().copy(displayName = "Pixel 8 Pro")
        assertEquals("Pixel 8 Pro", state.displayName)
    }

    @Test
    fun `all toggles off`() {
        val state = SettingsUiState(
            notificationsEnabled = false,
            vibrationEnabled = false
        )
        assertFalse(state.notificationsEnabled)
        assertFalse(state.vibrationEnabled)
    }

    @Test
    fun `copy toggles dark mode off`() {
        val state = SettingsUiState().copy(darkModeEnabled = false)
        assertFalse(state.darkModeEnabled)
        // Other fields unchanged
        assertTrue(state.notificationsEnabled)
    }

    @Test
    fun `equality for identical states`() {
        val a = SettingsUiState(displayName = "Test")
        val b = SettingsUiState(displayName = "Test")
        assertEquals(a, b)
    }

    @Test
    fun `default bio is empty`() {
        val state = SettingsUiState()
        assertEquals("", state.bio)
    }

    @Test
    fun `copy with bio`() {
        val state = SettingsUiState().copy(bio = "hello world")
        assertEquals("hello world", state.bio)
        // Other fields unchanged
        assertEquals("My Device", state.displayName)
    }

    @Test
    fun `default download location is Downloads`() {
        val state = SettingsUiState()
        assertEquals(null, state.downloadLocationUri)
        assertEquals("Downloads", state.downloadLocationName)
    }

    @Test
    fun `copy with custom download location`() {
        val state = SettingsUiState().copy(
            downloadLocationUri = "content://com.android.externalstorage.documents/tree/primary%3ABoopFiles",
            downloadLocationName = "BoopFiles"
        )
        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ABoopFiles", state.downloadLocationUri)
        assertEquals("BoopFiles", state.downloadLocationName)
        // Other fields unchanged
        assertEquals("My Device", state.displayName)
    }
}
