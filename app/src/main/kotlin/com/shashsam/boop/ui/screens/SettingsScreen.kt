package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shashsam.boop.ui.theme.BoopAccentYellow
import com.shashsam.boop.ui.theme.BoopBlack
import com.shashsam.boop.ui.theme.BoopBrandPurple
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.WarningAmber
import com.shashsam.boop.ui.viewmodels.SettingsUiState
import com.shashsam.boop.utils.rememberBoopHaptics

private const val TAG = "SettingsScreen"

/**
 * Settings screen with dark neo-brutalist styling.
 *
 * Displays toggle rows for Notifications, Location, Vibration, and Sound,
 * an editable Identity row, and a permissions warning card at the bottom.
 */
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    onBackClick: () -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onLocationToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onSoundToggle: (Boolean) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "SettingsScreen recompose — state=$settingsState")

    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    var showNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                Log.d(TAG, "Back clicked")
                onBackClick()
            }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Scrollable settings content ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Notifications
            SettingsToggleRow(
                icon = Icons.Filled.Notifications,
                label = "Notifications",
                checked = settingsState.notificationsEnabled,
                onCheckedChange = {
                    Log.d(TAG, "Notifications toggled=$it")
                    haptics.tick()
                    onNotificationsToggle(it)
                }
            )

            SettingsDivider()

            // Location
            SettingsToggleRow(
                icon = Icons.Filled.LocationOn,
                label = "Location",
                checked = settingsState.locationEnabled,
                onCheckedChange = {
                    Log.d(TAG, "Location toggled=$it")
                    haptics.tick()
                    onLocationToggle(it)
                }
            )

            SettingsDivider()

            // Vibration
            SettingsToggleRow(
                icon = Icons.Filled.Vibration,
                label = "Vibration",
                checked = settingsState.vibrationEnabled,
                onCheckedChange = {
                    Log.d(TAG, "Vibration toggled=$it")
                    haptics.tick()
                    onVibrationToggle(it)
                }
            )

            SettingsDivider()

            // Sound
            SettingsToggleRow(
                icon = Icons.Filled.VolumeUp,
                label = "Sound",
                checked = settingsState.soundEnabled,
                onCheckedChange = {
                    Log.d(TAG, "Sound toggled=$it")
                    haptics.tick()
                    onSoundToggle(it)
                }
            )

            SettingsDivider()

            // Dark Mode
            SettingsToggleRow(
                icon = Icons.Filled.DarkMode,
                label = "Dark Mode",
                checked = settingsState.darkModeEnabled,
                onCheckedChange = {
                    Log.d(TAG, "Dark mode toggled=$it")
                    haptics.click()
                    onDarkModeToggle(it)
                }
            )

            SettingsDivider()

            // Identity
            SettingsNavigationRow(
                icon = Icons.Filled.Person,
                label = "Identity",
                value = settingsState.displayName,
                onClick = {
                    Log.d(TAG, "Identity row clicked")
                    showNameDialog = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Warning card ────────────────────────────────────────────────
            PermissionsWarningCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Display name edit dialog ────────────────────────────────────────────
    if (showNameDialog) {
        DisplayNameDialog(
            currentName = settingsState.displayName,
            onConfirm = { newName ->
                Log.d(TAG, "Display name changed to=$newName")
                onDisplayNameChange(newName)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }
}

// ─── Settings Toggle Row ────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BoopBlack,
                    checkedTrackColor = BoopAccentYellow,
                    checkedBorderColor = BoopAccentYellow,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

// ─── Settings Navigation Row ────────────────────────────────────────────────

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Edit",
                tint = tokens.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ─── Divider ────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.15f)
            .padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

// ─── Permissions Warning Card ───────────────────────────────────────────────

@Composable
private fun PermissionsWarningCard(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Boop requires NFC, Wi-Fi, and nearby device permissions " +
                            "to discover peers and transfer files. Some toggles above " +
                            "control in-app behavior only and do not revoke system permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Display Name Dialog ────────────────────────────────────────────────────

@Composable
private fun DisplayNameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameText by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BoopShapeMedium,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = BoopBrandPurple,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Edit Display Name",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                singleLine = true,
                label = { Text("Display Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BoopBrandPurple,
                    cursorColor = BoopBrandPurple,
                    focusedLabelColor = BoopBrandPurple
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = nameText.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                }
            ) {
                Text(
                    text = "Save",
                    fontWeight = FontWeight.Bold,
                    color = BoopBrandPurple
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ─── Preview ────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    BoopTheme(darkTheme = true) {
        SettingsScreen(
            settingsState = SettingsUiState(
                notificationsEnabled = true,
                vibrationEnabled = false,
                soundEnabled = true,
                locationEnabled = true,
                displayName = "Pixel 8 Pro"
            ),
            onBackClick = {},
            onNotificationsToggle = {},
            onLocationToggle = {},
            onVibrationToggle = {},
            onSoundToggle = {},
            onDisplayNameChange = {},
            onDarkModeToggle = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    BoopTheme(darkTheme = false) {
        SettingsScreen(
            settingsState = SettingsUiState(),
            onBackClick = {},
            onNotificationsToggle = {},
            onLocationToggle = {},
            onVibrationToggle = {},
            onSoundToggle = {},
            onDisplayNameChange = {},
            onDarkModeToggle = {}
        )
    }
}
