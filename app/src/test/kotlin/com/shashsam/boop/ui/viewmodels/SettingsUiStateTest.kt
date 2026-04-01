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
    fun `default profileAnswers is empty`() {
        val state = SettingsUiState()
        assertTrue("profileAnswers default empty", state.profileAnswers.isEmpty())
    }

    @Test
    fun `copy with profileAnswers`() {
        val answers = mapOf("prefer_contact" to "Texting", "reply_speed" to "Fast")
        val state = SettingsUiState().copy(profileAnswers = answers)
        assertEquals(2, state.profileAnswers.size)
        assertEquals("Texting", state.profileAnswers["prefer_contact"])
        assertEquals("Fast", state.profileAnswers["reply_speed"])
        // Other fields unchanged
        assertEquals("My Device", state.displayName)
    }
}
