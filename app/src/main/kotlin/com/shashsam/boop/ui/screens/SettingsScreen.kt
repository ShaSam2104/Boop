package com.shashsam.boop.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shashsam.boop.ui.theme.BoopAccentYellow
import com.shashsam.boop.ui.theme.BoopBlack
import com.shashsam.boop.ui.theme.BoopBrandPurple
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.NeoBrutalistButton
import com.shashsam.boop.ui.theme.WarningAmber
import com.shashsam.boop.ui.viewmodels.BackupUiState
import com.shashsam.boop.ui.viewmodels.SettingsUiState
import com.shashsam.boop.utils.rememberBoopHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "SettingsScreen"

/**
 * Settings screen with dark neo-brutalist styling.
 *
 * Displays toggle rows for Notifications and Vibration,
 * an editable Identity row, receive permission selector,
 * an About section, and a permissions warning card.
 */
@Composable
fun SettingsScreen(
    settingsState: SettingsUiState,
    backupState: BackupUiState,
    onBackClick: () -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onReceivePermissionChange: (String) -> Unit,
    onExportData: (Uri, String) -> Unit,
    onImportData: (Uri, String) -> Unit,
    onDismissBackupMessage: () -> Unit,
    onDownloadLocationPick: (Uri) -> Unit,
    onDownloadLocationClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "SettingsScreen recompose — state=$settingsState")

    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }

    // ── Backup state ─────────────────────────────────────────────────────
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val isBusy = backupState.isExporting || backupState.isImporting

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "Export SAF uri received")
            onExportData(uri, pendingExportPassword)
            pendingExportPassword = ""
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            showImportPasswordDialog = true
        }
    }

    val downloadLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            Log.d(TAG, "Download location selected: $uri")
            onDownloadLocationPick(uri)
        }
    }

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
            IconButton(
                onClick = {
                    Log.d(TAG, "Back clicked")
                    onBackClick()
                },
                modifier = Modifier.offset(x = (-12).dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Navigate back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
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
            // ── About section ────────────────────────────────────────────────
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Boop",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Tap phones. Share files. No internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                        }
                    }

                    NeoBrutalistButton(
                        onClick = {
                            try {
                                val upiUri = Uri.parse("upi://pay?pa=03.shubhamshah-1@oksbi&pn=Boop&tn=Buy%20me%20a%20Chai&cu=INR")
                                context.startActivity(Intent(Intent.ACTION_VIEW, upiUri))
                            } catch (e: Exception) {
                                Log.w(TAG, "No UPI app available", e)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buy me a Chai",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            // Receive Permission
            SettingsNavigationRow(
                icon = Icons.Filled.Shield,
                label = "Who can send you files",
                value = if (settingsState.receivePermission == "friends") "Friends" else "No one",
                onClick = {
                    Log.d(TAG, "Receive permission row clicked")
                    showPermissionDialog = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Data section ──────────────────────────────────────────────────
            Text(
                text = "Data",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = tokens.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            // Download Location
            SettingsNavigationRow(
                icon = Icons.Filled.Folder,
                label = "Download Location",
                value = settingsState.downloadLocationName,
                onClick = {
                    Log.d(TAG, "Download location row clicked")
                    haptics.tick()
                    downloadLocationLauncher.launch(null)
                }
            )

            // Show reset option when custom location is set
            if (settingsState.downloadLocationUri != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics.tick()
                                onDownloadLocationClear()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Reset to Downloads",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = tokens.accent
                        )
                    }
                }
            }

            SettingsDivider()

            SettingsActionRow(
                icon = Icons.Filled.FileUpload,
                label = "Export Data",
                subtitle = if (backupState.isExporting) "Exporting…" else "Backup profile, friends & history",
                isLoading = backupState.isExporting,
                enabled = !isBusy,
                onClick = {
                    Log.d(TAG, "Export data clicked")
                    haptics.tick()
                    showExportPasswordDialog = true
                }
            )

            SettingsDivider()

            SettingsActionRow(
                icon = Icons.Filled.FileDownload,
                label = "Import Data",
                subtitle = if (backupState.isImporting) "Importing…" else "Restore from a backup file",
                isLoading = backupState.isImporting,
                enabled = !isBusy,
                onClick = {
                    Log.d(TAG, "Import data clicked")
                    haptics.tick()
                    importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                }
            )

            // Backup status message
            backupState.message?.let { message ->
                Spacer(modifier = Modifier.height(8.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDismissBackupMessage() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (backupState.isError) MaterialTheme.colorScheme.error else tokens.accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Warning card ────────────────────────────────────────────────
            PermissionsWarningCard()

            Spacer(modifier = Modifier.height(16.dp))

            // ── GitHub link ─────────────────────────────────────────────────
            Text(
                text = "Open source on GitHub",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textTertiary,
                modifier = Modifier
                    .clickable {
                        try {
                            val uri = Uri.parse("https://github.com/ShaSam2104/Boop")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: Exception) {
                            Log.w(TAG, "No browser available", e)
                        }
                    }
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ── Export password dialog ───────────────────────────────────────────────
    if (showExportPasswordDialog) {
        BackupPasswordDialog(
            title = "Export Backup",
            description = "Enter a password to encrypt your backup. You'll need this password to restore it.",
            actionLabel = "Export",
            onConfirm = { password ->
                showExportPasswordDialog = false
                pendingExportPassword = password
                val fileName = "boop_backup_${LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.boop"
                exportLauncher.launch(fileName)
            },
            onDismiss = { showExportPasswordDialog = false }
        )
    }

    // ── Import password dialog ──────────────────────────────────────────────
    if (showImportPasswordDialog) {
        BackupPasswordDialog(
            title = "Import Backup",
            description = "Enter the password used when this backup was created.",
            actionLabel = "Import",
            onConfirm = { password ->
                showImportPasswordDialog = false
                pendingUri?.let { uri ->
                    onImportData(uri, password)
                    pendingUri = null
                }
            },
            onDismiss = {
                showImportPasswordDialog = false
                pendingUri = null
            }
        )
    }

    // ── Receive permission dialog ───────────────────────────────────────────
    if (showPermissionDialog) {
        ReceivePermissionDialog(
            currentValue = settingsState.receivePermission,
            onSelect = { value ->
                Log.d(TAG, "Receive permission changed to=$value")
                onReceivePermissionChange(value)
                showPermissionDialog = false
            },
            onDismiss = { showPermissionDialog = false }
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
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
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
            .alpha(0.12f)
            .padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onBackground
    )
}

// ─── Settings Action Row ─────────────────────────────────────────────────

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.5f)
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
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = tokens.accent
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = tokens.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Backup Password Dialog ──────────────────────────────────────────────

@Composable
private fun BackupPasswordDialog(
    title: String,
    description: String,
    actionLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BoopShapeMedium,
        containerColor = LocalBoopTokens.current.dialogSurface,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BoopBrandPurple,
                        focusedLabelColor = BoopBrandPurple,
                        cursorColor = BoopBrandPurple
                    )
                )
            }
        },
        confirmButton = {
            NeoBrutalistButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) {
                Text(actionLabel, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                            "to discover peers and transfer files. Toggles above " +
                            "control in-app behavior only and do not revoke system permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Receive Permission Dialog ──────────────────────────────────────────────

@Composable
private fun ReceivePermissionDialog(
    currentValue: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BoopShapeMedium,
        containerColor = LocalBoopTokens.current.dialogSurface,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = BoopBrandPurple,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Who can send you files",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                PermissionOption(
                    label = "Friends",
                    description = "Auto-accept from friends",
                    selected = currentValue == "friends",
                    onClick = { onSelect("friends") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionOption(
                    label = "No one",
                    description = "Ask every time before accepting",
                    selected = currentValue == "no_one",
                    onClick = { onSelect("no_one") }
                )
            }
        },
        confirmButton = {
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

@Composable
private fun PermissionOption(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = BoopBrandPurple
            )
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
                displayName = "Pixel 8 Pro"
            ),
            backupState = BackupUiState(),
            onBackClick = {},
            onNotificationsToggle = {},
            onVibrationToggle = {},
            onDarkModeToggle = {},
            onReceivePermissionChange = {},
            onExportData = { _, _ -> },
            onImportData = { _, _ -> },
            onDismissBackupMessage = {},
            onDownloadLocationPick = {},
            onDownloadLocationClear = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenLightPreview() {
    BoopTheme(darkTheme = false) {
        SettingsScreen(
            settingsState = SettingsUiState(),
            backupState = BackupUiState(),
            onBackClick = {},
            onNotificationsToggle = {},
            onVibrationToggle = {},
            onDarkModeToggle = {},
            onReceivePermissionChange = {},
            onExportData = { _, _ -> },
            onImportData = { _, _ -> },
            onDismissBackupMessage = {},
            onDownloadLocationPick = {},
            onDownloadLocationClear = {}
        )
    }
}
