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
        assertTrue("sound default true", state.soundEnabled)
        assertTrue("location default true", state.locationEnabled)
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
        assertTrue(state.soundEnabled)
        assertTrue(state.locationEnabled)
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
            vibrationEnabled = false,
            soundEnabled = false,
            locationEnabled = false
        )
        assertFalse(state.notificationsEnabled)
        assertFalse(state.vibrationEnabled)
        assertFalse(state.soundEnabled)
        assertFalse(state.locationEnabled)
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
}
