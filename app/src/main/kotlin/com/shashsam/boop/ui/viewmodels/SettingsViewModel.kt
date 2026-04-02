package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_DARK_MODE = "dark_mode_enabled"
private const val KEY_RECEIVE_PERMISSION = "receive_permission"
private const val KEY_BIO = "bio"
private const val KEY_DOWNLOAD_LOCATION = "download_location_uri"

/**
 * Aggregate settings state exposed to the Settings screen.
 */
data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val displayName: String = "My Device",
    val darkModeEnabled: Boolean = true,
    val receivePermission: String = "friends",
    val bio: String = "",
    val downloadLocationUri: String? = null,
    val downloadLocationName: String = "Downloads"
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
        val downloadUri = prefs.getString(KEY_DOWNLOAD_LOCATION, null)
        return SettingsUiState(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "My Device") ?: "My Device",
            darkModeEnabled = prefs.getBoolean(KEY_DARK_MODE, true),
            receivePermission = prefs.getString(KEY_RECEIVE_PERMISSION, "friends") ?: "friends",
            bio = prefs.getString(KEY_BIO, "") ?: "",
            downloadLocationUri = downloadUri,
            downloadLocationName = downloadUri?.let { extractFolderName(it) } ?: "Downloads"
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

    fun setBio(bio: String) {
        Log.d(TAG, "setBio=$bio")
        prefs.edit().putString(KEY_BIO, bio).apply()
        _uiState.update { it.copy(bio = bio) }
    }

    fun setDownloadLocation(uri: Uri) {
        Log.d(TAG, "setDownloadLocation=$uri")
        // Take persistable permission so the URI survives app restarts
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to take persistable URI permission", e)
        }
        val uriString = uri.toString()
        val folderName = extractFolderName(uriString)
        prefs.edit().putString(KEY_DOWNLOAD_LOCATION, uriString).apply()
        _uiState.update { it.copy(downloadLocationUri = uriString, downloadLocationName = folderName) }
    }

    fun clearDownloadLocation() {
        Log.d(TAG, "clearDownloadLocation")
        // Release persisted permission for the old URI
        val oldUri = prefs.getString(KEY_DOWNLOAD_LOCATION, null)
        if (oldUri != null) {
            try {
                getApplication<Application>().contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldUri),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release persisted URI permission", e)
            }
        }
        prefs.edit().remove(KEY_DOWNLOAD_LOCATION).apply()
        _uiState.update { it.copy(downloadLocationUri = null, downloadLocationName = "Downloads") }
    }

    private fun extractFolderName(uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(uri)
            // docId format: "primary:path/to/folder" or "home:folder"
            val path = docId.substringAfter(":")
            if (path.isNotEmpty()) path.substringAfterLast("/").ifEmpty { path } else "Selected folder"
        } catch (e: Exception) {
            "Selected folder"
        }
    }
}
