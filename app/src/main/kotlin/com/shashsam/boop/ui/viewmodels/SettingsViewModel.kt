package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "SettingsViewModel"
private const val PREFS_NAME = "boop_settings"

private const val KEY_NOTIFICATIONS = "notifications_enabled"
private const val KEY_VIBRATION = "vibration_enabled"
private const val KEY_SOUND = "sound_enabled"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_DARK_MODE = "dark_mode_enabled"
private const val KEY_RECEIVE_PERMISSION = "receive_permission"

/**
 * Aggregate settings state exposed to the Settings screen.
 */
data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val displayName: String = "My Device",
    val darkModeEnabled: Boolean = true,
    val receivePermission: String = "friends"
)

/**
 * ViewModel that persists user preferences via SharedPreferences.
 * Each toggle reads/writes immediately to disk.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadFromPrefs())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadFromPrefs(): SettingsUiState {
        return SettingsUiState(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            soundEnabled = prefs.getBoolean(KEY_SOUND, true),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "My Device") ?: "My Device",
            darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE, true),
            receivePermission = prefs.getString(KEY_RECEIVE_PERMISSION, "friends") ?: "friends"
        ).also { Log.d(TAG, "Loaded settings: $it") }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        Log.d(TAG, "setNotificationsEnabled=$enabled")
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
        _uiState.update { it.copy(notificationsEnabled = enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        Log.d(TAG, "setVibrationEnabled=$enabled")
        prefs.edit().putBoolean(KEY_VIBRATION, enabled).apply()
        _uiState.update { it.copy(vibrationEnabled = enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        Log.d(TAG, "setSoundEnabled=$enabled")
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun setDisplayName(name: String) {
        Log.d(TAG, "setDisplayName=$name")
        prefs.edit().putString(KEY_DISPLAY_NAME, name).apply()
        _uiState.update { it.copy(displayName = name) }
    }

    fun setDarkMode(enabled: Boolean) {
        Log.d(TAG, "setDarkMode=$enabled")
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _uiState.update { it.copy(darkModeEnabled = enabled) }
    }

    fun setReceivePermission(value: String) {
        Log.d(TAG, "setReceivePermission=$value")
        prefs.edit().putString(KEY_RECEIVE_PERMISSION, value).apply()
        _uiState.update { it.copy(receivePermission = value) }
    }
}
