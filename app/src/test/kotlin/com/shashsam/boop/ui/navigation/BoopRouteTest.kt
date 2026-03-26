package com.shashsam.boop.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BoopRouteTest {

    @Test
    fun `Home route is home`() {
        assertEquals("home", BoopRoute.Home.route)
    }

    @Test
    fun `History route is history`() {
        assertEquals("history", BoopRoute.History.route)
    }

    @Test
    fun `Profile route is profile`() {
        assertEquals("profile", BoopRoute.Profile.route)
    }

    @Test
    fun `TransferProgress route is transfer_progress`() {
        assertEquals("transfer_progress", BoopRoute.TransferProgress.route)
    }

    @Test
    fun `NfcGuide route is nfc_guide`() {
        assertEquals("nfc_guide", BoopRoute.NfcGuide.route)
    }

    @Test
    fun `Settings route is settings`() {
        assertEquals("settings", BoopRoute.Settings.route)
    }

    @Test
    fun `FriendProfile route contains friendId argument`() {
        assertEquals("friend_profile/{friendId}", BoopRoute.FriendProfile.route)
    }

    @Test
    fun `FriendProfile createRoute produces correct path`() {
        assertEquals("friend_profile/42", BoopRoute.FriendProfile.createRoute(42L))
    }

    @Test
    fun `all routes are unique`() {
        val routes = listOf(
            BoopRoute.Home,
            BoopRoute.History,
            BoopRoute.Profile,
            BoopRoute.TransferProgress,
            BoopRoute.NfcGuide,
            BoopRoute.Settings,
            BoopRoute.FriendProfile
        ).map { it.route }
        assertEquals("All routes should be unique", routes.size, routes.toSet().size)
    }
}
