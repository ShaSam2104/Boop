package com.shashsam.boop.ui.navigation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.shashsam.boop.data.FriendEntity
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.screens.FriendProfileScreen
import com.shashsam.boop.ui.screens.HistoryScreen
import com.shashsam.boop.ui.screens.HomeScreen
import com.shashsam.boop.ui.screens.NfcGuideScreen
import com.shashsam.boop.ui.screens.ProfileScreen
import com.shashsam.boop.ui.screens.SettingsScreen
import com.shashsam.boop.ui.screens.TransferProgressScreen
import com.shashsam.boop.ui.viewmodels.SettingsUiState
import com.shashsam.boop.ui.viewmodels.TransferUiState

private const val TAG = "BoopNavHost"

/**
 * Navigation host that maps [BoopRoute]s to screen composables.
 */
@Composable
fun BoopNavHost(
    navController: NavHostController,
    transferUiState: TransferUiState,
    settingsState: SettingsUiState,
    friends: List<FriendEntity>,
    profileItems: List<ProfileItemEntity>,
    profilePicPath: String?,
    selectedFriend: FriendEntity?,
    permissionsGranted: Boolean,
    onSendClick: () -> Unit,
    onResetClick: () -> Unit,
    onResendBoop: (com.shashsam.boop.ui.models.RecentBoop) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onReceivePermissionChange: (String) -> Unit,
    onProfilePicPick: (android.net.Uri) -> Unit,
    onAddProfileItem: (String, String, String, String) -> Unit,
    onEditProfileItem: (ProfileItemEntity) -> Unit,
    onDeleteProfileItem: (Long) -> Unit,
    onReorderProfileItems: (List<ProfileItemEntity>) -> Unit,
    onFriendClick: (FriendEntity) -> Unit,
    onSelectFriend: (Long) -> Unit,
    onRemoveFriend: (Long) -> Unit,
    onShareProfileClick: () -> Unit,
    onCancelProfileShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BoopRoute.Home.route,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        composable(BoopRoute.Home.route) {
            HomeScreen(
                permissionsGranted = permissionsGranted,
                transferUiState = transferUiState,
                onSendClick = onSendClick,
                onResetClick = onResetClick,
                onNfcGuideClick = {
                    Log.d(TAG, "Navigating to NFC Guide")
                    navController.navigate(BoopRoute.NfcGuide.route)
                },
                onViewAllClick = {
                    Log.d(TAG, "Navigating to History (View All)")
                    navController.navigate(BoopRoute.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        composable(BoopRoute.History.route) {
            HistoryScreen(
                recentTransfers = transferUiState.recentTransfers,
                onResend = onResendBoop
            )
        }

        composable(BoopRoute.Profile.route) {
            ProfileScreen(
                settingsState = settingsState,
                friends = friends,
                profileItems = profileItems,
                profilePicPath = profilePicPath,
                onSettingsClick = {
                    Log.d(TAG, "Navigating to Settings from Profile")
                    navController.navigate(BoopRoute.Settings.route) {
                        popUpTo(BoopRoute.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDisplayNameChange = onDisplayNameChange,
                onProfilePicPick = onProfilePicPick,
                onAddItem = onAddProfileItem,
                onEditItem = onEditProfileItem,
                onDeleteItem = onDeleteProfileItem,
                onReorderItems = onReorderProfileItems,
                onFriendClick = { friend ->
                    onFriendClick(friend)
                    navController.navigate(BoopRoute.FriendProfile.createRoute(friend.id))
                },
                onShareProfileClick = onShareProfileClick
            )
        }

        composable(BoopRoute.Settings.route) {
            BackHandler {
                Log.d(TAG, "System back on Settings -> Profile tab")
                navController.navigate(BoopRoute.Profile.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            SettingsScreen(
                settingsState = settingsState,
                onBackClick = {
                    Log.d(TAG, "Settings back -> Profile tab")
                    navController.navigate(BoopRoute.Profile.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNotificationsToggle = onNotificationsToggle,
                onVibrationToggle = onVibrationToggle,
                onSoundToggle = onSoundToggle,
                onDarkModeToggle = onDarkModeToggle,
                onReceivePermissionChange = onReceivePermissionChange
            )
        }

        composable(BoopRoute.TransferProgress.route) {
            TransferProgressScreen(
                transferUiState = transferUiState,
                onBackClick = {
                    Log.d(TAG, "TransferProgress back click")
                    onResetClick()
                    navController.popBackStack()
                },
                onCancelClick = {
                    Log.d(TAG, "Transfer cancelled")
                    onResetClick()
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = BoopRoute.FriendProfile.route,
            arguments = listOf(navArgument("friendId") { type = NavType.LongType })
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getLong("friendId") ?: -1L
            // Trigger friend loading
            androidx.compose.runtime.LaunchedEffect(friendId) {
                onSelectFriend(friendId)
            }
            FriendProfileScreen(
                friend = selectedFriend,
                onBackClick = {
                    Log.d(TAG, "FriendProfile back click")
                    navController.popBackStack()
                },
                onRemoveFriend = { id ->
                    onRemoveFriend(id)
                    navController.popBackStack()
                }
            )
        }

        dialog(
            route = BoopRoute.NfcGuide.route,
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            NfcGuideScreen(
                onDismiss = {
                    Log.d(TAG, "NFC Guide dismissed")
                    navController.popBackStack()
                }
            )
        }
    }
}
