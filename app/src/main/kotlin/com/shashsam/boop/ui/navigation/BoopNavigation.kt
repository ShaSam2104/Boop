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
    data object FriendHistory : BoopRoute("friend_history/{friendId}") {
        fun createRoute(friendId: Long) = "friend_history/$friendId"
    }
    data object FriendsList : BoopRoute("friends_list")

    companion object {
        /** Tab index for slide direction. Higher index = further right. */
        fun tabIndex(route: String?): Int = when {
            route == Home.route -> 0
            route == History.route -> 1
            route == Profile.route -> 2
            route == Settings.route -> 3
            route == FriendsList.route -> 3
            route?.startsWith("friend_profile") == true -> 3
            route?.startsWith("friend_history") == true -> 3
            else -> -1  // Non-tab routes (TransferProgress, NfcGuide)
        }
    }
}
