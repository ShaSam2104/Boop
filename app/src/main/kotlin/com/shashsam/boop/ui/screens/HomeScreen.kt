package com.shashsam.boop.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shashsam.boop.ui.models.RecentBoop
import com.shashsam.boop.ui.theme.BoopAccentYellow
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.GlassCard
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.SpaceGrotesk
import com.shashsam.boop.ui.theme.boopGlow
import com.shashsam.boop.ui.viewmodels.TransferUiState
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.toFormattedSize

private const val TAG = "HomeScreen"

/**
 * Home screen — "Solid Geometric Style" matching the Stitch mockup.
 *
 * Features: header with NFC icon + title, pill-style mode toggle,
 * large "Ready to Boop?" display, concentric-circle CTA button,
 * and recent transfers list.
 */
@Composable
fun HomeScreen(
    permissionsGranted: Boolean,
    transferUiState: TransferUiState,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onResetClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onNfcGuideClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HomeScreen recompose — permissionsGranted=$permissionsGranted")
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Contactless,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Boop",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Mode selector (pill toggle) ────────────────────────────────────
        ModeSelectorPill(
            isSendMode = transferUiState.isSendMode,
            isReceiveMode = transferUiState.isReceiveMode,
            isTransferring = transferUiState.isTransferring,
            enabled = permissionsGranted,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── "Ready to Boop?" text ──────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ready to",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 48.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Boop?",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 52.sp
                ),
                color = BoopAccentYellow,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Concentric circles CTA ─────────────────────────────────────────
        ConcentricCircleCta(
            permissionsGranted = permissionsGranted,
            isSendMode = transferUiState.isSendMode,
            isReceiveMode = transferUiState.isReceiveMode,
            isTransferring = transferUiState.isTransferring,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Instruction text ───────────────────────────────────────────────
        Text(
            text = "Hold your device near\nanother phone to share",
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Recent Boops ───────────────────────────────────────────────────
        if (transferUiState.recentTransfers.isNotEmpty()) {
            val context = LocalContext.current
            RecentBoopsSection(
                recentTransfers = transferUiState.recentTransfers,
                onBoopClick = { boop ->
                    boop.fileUri?.let { uri ->
                        Log.d(TAG, "Opening file: ${boop.fileName} uri=$uri")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, boop.mimeType.ifEmpty { "*/*" })
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.w(TAG, "No app to open ${boop.fileName}", e)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Mode Selector Pill ────────────────────────────────────────────────────

@Composable
private fun ModeSelectorPill(
    isSendMode: Boolean,
    isReceiveMode: Boolean,
    isTransferring: Boolean,
    enabled: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val isActionable = enabled && !isTransferring
    val containerColor = tokens.pillContainer
    val activeColor = tokens.pillActive

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Send pill
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isSendMode) Modifier.background(activeColor)
                    else Modifier
                )
                .clickable(enabled = isActionable) {
                    Log.d(TAG, "Send mode clicked")
                    haptics.click()
                    onSendClick()
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSendMode && isTransferring) "Sending..." else "Send Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSendMode) FontWeight.Bold else FontWeight.Medium,
                color = if (isSendMode) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Receive pill
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isReceiveMode) Modifier.background(activeColor)
                    else Modifier
                )
                .clickable(enabled = isActionable) {
                    Log.d(TAG, "Receive mode clicked")
                    haptics.click()
                    onReceiveClick()
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isReceiveMode && !isTransferring) "Receive Mode" else "Receive Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isReceiveMode) FontWeight.Bold else FontWeight.Medium,
                color = if (isReceiveMode) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Concentric Circle CTA ─────────────────────────────────────────────────

@Composable
private fun ConcentricCircleCta(
    permissionsGranted: Boolean,
    isSendMode: Boolean,
    isReceiveMode: Boolean,
    isTransferring: Boolean,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val isActive = isSendMode || isReceiveMode

    // Concentric circle colors from tokens
    val dashedRingColor = tokens.concentricDashed
    val outerRingColor = tokens.concentricOuter
    val innerRingColor = tokens.concentricInner

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            // Draw the dashed outer ring
            .drawBehind {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val dashedRadius = minOf(size.width, size.height) / 2 - 4.dp.toPx()

                // Dashed outer circle
                drawCircle(
                    color = dashedRingColor,
                    radius = dashedRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                            0f
                        ),
                        cap = StrokeCap.Round
                    )
                )

                // Dark solid ring
                val solidRingRadius = dashedRadius - 16.dp.toPx()
                drawCircle(
                    color = outerRingColor,
                    radius = solidRingRadius,
                    center = Offset(centerX, centerY)
                )

                // Inner darker ring
                val innerRingRadius = solidRingRadius - 8.dp.toPx()
                drawCircle(
                    color = innerRingColor,
                    radius = innerRingRadius,
                    center = Offset(centerX, centerY)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Central yellow circle button
        val buttonSize = 180.dp
        Box(
            modifier = Modifier
                .size(buttonSize)
                .then(
                    if (isActive) Modifier.boopGlow(
                        color = BoopAccentYellow.copy(alpha = 0.2f),
                        radius = 90.dp,
                        spread = 20.dp
                    ) else Modifier
                )
                .clip(CircleShape)
                .background(BoopAccentYellow)
                .border(
                    width = 3.dp,
                    color = Color.White.copy(alpha = 0.3f),
                    shape = CircleShape
                )
                .clickable(enabled = permissionsGranted) {
                    Log.d(TAG, "BOOP IT clicked — sendMode=$isSendMode receiveMode=$isReceiveMode")
                    haptics.heavy()
                    if (!isSendMode && !isReceiveMode) {
                        onSendClick()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Contactless,
                    contentDescription = "Boop",
                    tint = Color(0xFF1A1A00),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BOOP IT",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = Color(0xFF1A1A00)
                )
            }
        }
    }
}

// ─── Recent Boops ───────────────────────────────────────────────────────────

@Composable
private fun RecentBoopsSection(
    recentTransfers: List<RecentBoop>,
    onBoopClick: (RecentBoop) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Boops",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.accent
            )
        }
        recentTransfers.takeLast(3).reversed().forEach { boop ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = boop.fileUri != null) {
                            onBoopClick(boop)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (boop.wasSender) BoopAccentYellow.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (boop.wasSender) Icons.Filled.CloudUpload else Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = if (boop.wasSender) BoopAccentYellow else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = boop.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${boop.fileSize.toFormattedSize()} — ${if (boop.wasSender) "Sent" else "Received"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = tokens.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ─── Previews ───────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun HomeScreenDarkPreview() {
    BoopTheme(darkTheme = true) {
        HomeScreen(
            permissionsGranted = true,
            transferUiState = TransferUiState(
                recentTransfers = listOf(
                    RecentBoop("Photo_2024.jpg", 4_500_000L, "image/jpeg", System.currentTimeMillis(), true),
                    RecentBoop("Document.pdf", 2_100_000L, "application/pdf", System.currentTimeMillis(), false)
                )
            ),
            onSendClick = {},
            onReceiveClick = {},
            onResetClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    BoopTheme(darkTheme = false) {
        HomeScreen(
            permissionsGranted = true,
            transferUiState = TransferUiState(),
            onSendClick = {},
            onReceiveClick = {},
            onResetClick = {}
        )
    }
}
