package com.shashsam.boop.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

private const val TAG = "BoopPermissions"

/**
 * Returns the full list of permissions the app requires.
 *
 * The list is version-aware:
 * - API 33+  → READ_MEDIA_IMAGES / VIDEO / AUDIO + NEARBY_WIFI_DEVICES (no location needed)
 * - API 29-32→ READ_EXTERNAL_STORAGE + ACCESS_FINE_LOCATION
 * - API 26-28→ READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE + ACCESS_FINE_LOCATION
 */
fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(
        Manifest.permission.NFC
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ — granular media + nearby Wi-Fi; location not required for Wi-Fi Direct
        permissions += Manifest.permission.READ_MEDIA_IMAGES
        permissions += Manifest.permission.READ_MEDIA_VIDEO
        permissions += Manifest.permission.READ_MEDIA_AUDIO
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        // Android 8–12 — legacy storage + location required for Wi-Fi Direct peer discovery
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }

    Log.d(TAG, "Required permissions for SDK ${Build.VERSION.SDK_INT}: $permissions")
    return permissions.toTypedArray()
}

/**
 * Returns true when every permission in [requiredPermissions] has been granted.
 */
fun allPermissionsGranted(context: Context): Boolean {
    val result = requiredPermissions().all { permission ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) Log.d(TAG, "Permission NOT granted: $permission")
        granted
    }
    Log.d(TAG, "All permissions granted: $result")
    return result
}

/**
 * Compose helper that wires up [ActivityResultContracts.RequestMultiplePermissions]
 * and invokes [onResult] with a consolidated boolean.
 *
 * Usage:
 * ```
 * val launcher = rememberPermissionLauncher { granted ->
 *     if (granted) { /* proceed */ }
 * }
 * launcher.launch(requiredPermissions())
 * ```
 */
@Composable
fun rememberPermissionLauncher(
    onResult: (allGranted: Boolean) -> Unit
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
        Log.d(TAG, "Permission result map: $permissionsMap — allGranted=$allGranted")
        onResult(allGranted)
    }
}
