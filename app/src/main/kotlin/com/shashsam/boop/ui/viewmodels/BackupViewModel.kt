package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shashsam.boop.backup.BackupManager
import com.shashsam.boop.backup.BadMagicException
import com.shashsam.boop.backup.UnsupportedVersionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException

private const val TAG = "BackupViewModel"

data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val backupManager = BackupManager(application)

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val isBusy: Boolean get() = _uiState.value.isExporting || _uiState.value.isImporting

    fun exportData(uri: Uri, password: String) {
        if (isBusy) return
        Log.d(TAG, "exportData starting")
        _uiState.update { it.copy(isExporting = true, message = null, isError = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.export(uri, password)
                Log.d(TAG, "Export successful")
                _uiState.update { it.copy(isExporting = false, message = "Backup exported successfully", isError = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update { it.copy(isExporting = false, message = "Export failed: ${e.message}", isError = true) }
            }
        }
    }

    fun importData(uri: Uri, password: String) {
        if (isBusy) return
        Log.d(TAG, "importData starting")
        _uiState.update { it.copy(isImporting = true, message = null, isError = false) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.import(uri, password)
                Log.d(TAG, "Import successful")
                _uiState.update { it.copy(isImporting = false, message = "Backup imported successfully", isError = false) }
            } catch (e: AEADBadTagException) {
                Log.w(TAG, "Wrong password", e)
                _uiState.update { it.copy(isImporting = false, message = "Incorrect password", isError = true) }
            } catch (e: BadMagicException) {
                Log.w(TAG, "Bad magic", e)
                _uiState.update { it.copy(isImporting = false, message = e.message, isError = true) }
            } catch (e: UnsupportedVersionException) {
                Log.w(TAG, "Unsupported version", e)
                _uiState.update { it.copy(isImporting = false, message = e.message, isError = true) }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _uiState.update { it.copy(isImporting = false, message = "Import failed: ${e.message}", isError = true) }
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }
}
