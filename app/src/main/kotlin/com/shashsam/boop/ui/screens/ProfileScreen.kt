package com.shashsam.boop.ui.screens

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shashsam.boop.data.FriendEntity
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.components.BentoGrid
import com.shashsam.boop.ui.components.ProfileItemDialog
import com.shashsam.boop.ui.theme.BoopBrandPurple
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.NeoBrutalistButton
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.ui.viewmodels.PROFILE_QUESTIONS
import com.shashsam.boop.ui.viewmodels.SettingsUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ProfileScreen"

@Composable
fun ProfileScreen(
    settingsState: SettingsUiState,
    friends: List<FriendEntity>,
    profileItems: List<ProfileItemEntity>,
    profilePicPath: String?,
    profileAnswers: Map<String, String> = emptyMap(),
    onProfileAnswerChange: (String, String) -> Unit = { _, _ -> },
    onSettingsClick: () -> Unit = {},
    onDisplayNameChange: (String) -> Unit = {},
    onProfilePicPick: (Uri) -> Unit = {},
    onAddItem: (type: String, label: String, value: String, size: String) -> Unit = { _, _, _, _ -> },
    onEditItem: (ProfileItemEntity) -> Unit = {},
    onDeleteItem: (Long) -> Unit = {},
    onReorderItems: (List<ProfileItemEntity>) -> Unit = {},
    onFriendClick: (FriendEntity) -> Unit = {},
    onShareProfileClick: () -> Unit = {},
    onCancelProfileShare: () -> Unit = {},
    isProfileSharing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "ProfileScreen composed — friends=${friends.size} items=${profileItems.size}")

    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    var showNameDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ProfileItemEntity?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onProfilePicPick(it) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Profile card ─────────────────────────────────────────────────
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile pic — tap to change
                    val picFile = profilePicPath?.let { File(it) }
                    if (picFile != null && picFile.exists()) {
                        AsyncImage(
                            model = picFile,
                            contentDescription = "Profile picture — tap to change",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .clickable { photoPickerLauncher.launch("image/*") },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Add profile picture",
                            tint = tokens.accent,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable { photoPickerLauncher.launch("image/*") }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = settingsState.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.clickable { haptics.tick(); showNameDialog = true }
                        )
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Share Profile button ─────────────────────────────────────────
        item {
            if (isProfileSharing) {
                // Broadcasting state — pulsing NFC icon + cancel
                val pulseTransition = rememberInfiniteTransition(label = "nfc_pulse")
                val pulseAlpha by pulseTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { alpha = pulseAlpha }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Broadcasting...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = tokens.accent
                            )
                            Text(
                                text = "Hold phones together to share",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onCancelProfileShare) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel sharing",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { haptics.click(); onShareProfileClick() })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Share Profile via NFC",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = tokens.textTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── About Me section ──────────────────────────────────────────────
        item {
            Text(
                text = "About Me",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(PROFILE_QUESTIONS.size) { index ->
            val question = PROFILE_QUESTIONS[index]
            val selectedAnswer = profileAnswers[question.key]
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = question.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(question.optionA, question.optionB).forEach { option ->
                            val isSelected = selectedAnswer == option
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    haptics.tick()
                                    onProfileAnswerChange(question.key, option)
                                },
                                label = {
                                    Text(
                                        text = option,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BoopBrandPurple,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ── Bento section ────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Links",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showAddDialog = true },
                    enabled = profileItems.size < 12
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add link",
                        tint = if (profileItems.size < 12) tokens.accent else tokens.textTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (profileItems.isNotEmpty()) {
            item {
                BentoGrid(
                    items = profileItems,
                    isEditable = true,
                    onItemClick = {},
                    onEditItem = { editingItem = it },
                    onDeleteItem = onDeleteItem
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ── Friends section ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Friends",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${friends.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textSecondary
                )
            }
        }

        if (friends.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonOff,
                            contentDescription = null,
                            tint = tokens.textSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No friends yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = tokens.textSecondary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tap 'Accept + Become Friends' when receiving files to add friends",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(friends, key = { it.id }) { friend ->
                FriendCard(
                    friend = friend,
                    onClick = { onFriendClick(friend) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Display name dialog ──────────────────────────────────────────────
    if (showNameDialog) {
        DisplayNameDialog(
            currentName = settingsState.displayName,
            onConfirm = { newName ->
                onDisplayNameChange(newName)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    // ── Add profile item dialog ──────────────────────────────────────────
    if (showAddDialog) {
        ProfileItemDialog(
            onSave = { type, label, value, size ->
                onAddItem(type, label, value, size)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // ── Edit profile item dialog ─────────────────────────────────────────
    editingItem?.let { item ->
        ProfileItemDialog(
            existingItem = item,
            onSave = { type, label, value, size ->
                onEditItem(item.copy(type = type, label = label, value = value, size = size))
                editingItem = null
            },
            onDelete = {
                onDeleteItem(item.id)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
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
        containerColor = LocalBoopTokens.current.dialogSurface,
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
            NeoBrutalistButton(
                onClick = {
                    val trimmed = nameText.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                enabled = nameText.trim().isNotEmpty()
            ) {
                Text("Save", fontWeight = FontWeight.ExtraBold)
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

// ─── Friend Card ────────────────────────────────────────────────────────────

@Composable
private fun FriendCard(
    friend: FriendEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Friend profile pic or fallback
            val picFile = friend.profilePicPath?.let { File(it) }
            if (picFile != null && picFile.exists()) {
                AsyncImage(
                    model = picFile,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = tokens.accent,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${friend.transferCount} transfer${if (friend.transferCount != 1) "s" else ""} · Last seen ${dateFormat.format(Date(friend.lastSeenTimestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "View profile",
                tint = tokens.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
