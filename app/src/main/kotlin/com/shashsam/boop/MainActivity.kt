package com.shashsam.boop

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.shashsam.boop.ui.screens.HomeScreen
import com.shashsam.boop.ui.screens.LogEntry
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.utils.allPermissionsGranted
import com.shashsam.boop.utils.rememberPermissionLauncher
import com.shashsam.boop.utils.requiredPermissions

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        enableEdgeToEdge()

        setContent {
            BoopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current

                    // ── UI state ─────────────────────────────────────────────
                    var permissionsGranted by remember {
                        mutableStateOf(allPermissionsGranted(context))
                    }
                    val statusLog = remember { mutableStateListOf<LogEntry>() }

                    // ── Permission launcher ──────────────────────────────────
                    val permissionLauncher = rememberPermissionLauncher { allGranted ->
                        Log.d(TAG, "Permission result: allGranted=$allGranted")
                        permissionsGranted = allGranted
                        if (allGranted) {
                            statusLog.add(LogEntry("✅ All permissions granted. Systems ready."))
                        } else {
                            statusLog.add(
                                LogEntry(
                                    "⚠️ Some permissions were denied. Please grant all permissions.",
                                    isError = true
                                )
                            )
                        }
                    }

                    // Request permissions on first launch if not already granted
                    LaunchedEffect(Unit) {
                        if (!permissionsGranted) {
                            Log.d(TAG, "Launching permission request")
                            statusLog.add(LogEntry("Requesting permissions…"))
                            permissionLauncher.launch(requiredPermissions())
                        } else {
                            Log.d(TAG, "All permissions already granted")
                            statusLog.add(LogEntry("✅ All permissions already granted."))
                        }
                    }

                    HomeScreen(
                        permissionsGranted = permissionsGranted,
                        statusLog = statusLog,
                        onSendClick = {
                            Log.d(TAG, "onSendClick — permissionsGranted=$permissionsGranted")
                            if (!permissionsGranted) {
                                statusLog.add(
                                    LogEntry(
                                        "⚠️ Permissions required before sending.",
                                        isError = true
                                    )
                                )
                                permissionLauncher.launch(requiredPermissions())
                            } else {
                                statusLog.add(LogEntry("📤 Send mode activated. Hold phones together…"))
                                // TODO Phase 2: Start HCE service & file picker
                            }
                        },
                        onReceiveClick = {
                            Log.d(TAG, "onReceiveClick — permissionsGranted=$permissionsGranted")
                            if (!permissionsGranted) {
                                statusLog.add(
                                    LogEntry(
                                        "⚠️ Permissions required before receiving.",
                                        isError = true
                                    )
                                )
                                permissionLauncher.launch(requiredPermissions())
                            } else {
                                statusLog.add(LogEntry("📥 Receive mode activated. Hold phones together…"))
                                // TODO Phase 2: Start NFC reader mode & Wi-Fi Direct listener
                            }
                        }
                    )
                }
            }
        }
    }
}
