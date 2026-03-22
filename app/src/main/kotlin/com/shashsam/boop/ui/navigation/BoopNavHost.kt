package com.shashsam.boop.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
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
    permissionsGranted: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onResetClick: () -> Unit,
    onResendBoop: (com.shashsam.boop.ui.models.RecentBoop) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onLocationToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = BoopRoute.Home.route,
        modifier = modifier
    ) {
        composable(BoopRoute.Home.route) {
            HomeScreen(
                permissionsGranted = permissionsGranted,
                transferUiState = transferUiState,
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick,
                onResetClick = onResetClick,
                onSettingsClick = {
                    Log.d(TAG, "Navigating to Settings")
                    navController.navigate(BoopRoute.Settings.route)
                },
                onNfcGuideClick = {
                    Log.d(TAG, "Navigating to NFC Guide")
                    navController.navigate(BoopRoute.NfcGuide.route)
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
            ProfileScreen()
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

        composable(BoopRoute.Settings.route) {
            SettingsScreen(
                settingsState = settingsState,
                onBackClick = {
                    Log.d(TAG, "Settings back click")
                    navController.popBackStack()
                },
                onNotificationsToggle = onNotificationsToggle,
                onLocationToggle = onLocationToggle,
                onVibrationToggle = onVibrationToggle,
                onSoundToggle = onSoundToggle,
                onDisplayNameChange = onDisplayNameChange,
                onDarkModeToggle = onDarkModeToggle
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
