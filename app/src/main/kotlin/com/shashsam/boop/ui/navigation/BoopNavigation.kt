package com.shashsam.boop.ui.navigation

/**
 * Navigation routes for the Boop app.
 */
sealed class BoopRoute(val route: String) {
    data object Home : BoopRoute("home")
    data object History : BoopRoute("history")
    data object Profile : BoopRoute("profile")
    data object TransferProgress : BoopRoute("transfer_progress")
    data object NfcGuide : BoopRoute("nfc_guide")
    data object Settings : BoopRoute("settings")
    data object FriendProfile : BoopRoute("friend_profile/{friendId}") {
        fun createRoute(friendId: Long) = "friend_profile/$friendId"
    }
}
