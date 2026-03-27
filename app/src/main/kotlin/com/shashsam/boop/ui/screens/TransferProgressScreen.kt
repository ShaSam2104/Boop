package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shashsam.boop.ui.theme.BoopAccentYellow
import com.shashsam.boop.ui.theme.BoopBlack
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.SpaceGrotesk
import com.shashsam.boop.ui.viewmodels.TransferUiState
import com.shashsam.boop.utils.rememberBoopHaptics
import com.shashsam.boop.utils.toFormattedSize

private const val TAG = "TransferProgressScreen"

@Composable
fun TransferProgressScreen(
    transferUiState: TransferUiState,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "recompose — progress=${transferUiState.transferProgress} transferring=${transferUiState.isTransferring} complete=${transferUiState.transferComplete}")

    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val percentageInt = (transferUiState.transferProgress * 100).toInt()

    // Concentric circle colors from tokens
    val dashedRingColor = tokens.concentricDashed
    val outerRingColor = tokens.concentricOuter
    val innerRingColor = tokens.concentricInner

    // Rotating dashed ring during active transfer
    val ringTransition = rememberInfiniteTransition(label = "ringRotation")
    val ringRotation by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashRotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {

        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                Log.d(TAG, "Back clicked")
                onBackClick()
            }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "FILE TRANSFER",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Balance spacer (same width as back IconButton)
            Spacer(modifier = Modifier.size(48.dp))
        }

        // ── Concentric circles with percentage ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .drawBehind {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val maxRadius = minOf(size.width, size.height) / 2 - 8.dp.toPx()

                    // Dashed outer circle — rotates during active transfer
                    rotate(degrees = ringRotation, pivot = Offset(centerX, centerY)) {
                        drawCircle(
                            color = dashedRingColor,
                            radius = maxRadius,
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
                    }

                    // Dark solid ring
                    val solidRingRadius = maxRadius - 16.dp.toPx()
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (transferUiState.transferComplete) {
                    // Completion checkmark
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Transfer complete",
                        tint = BoopAccentYellow,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Large percentage
                Text(
                    text = "$percentageInt%",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 96.sp,
                        lineHeight = 104.sp,
                        letterSpacing = (-2).sp
                    ),
                    color = BoopAccentYellow
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status text — italic serif like "Boop?" on home
                Text(
                    text = if (transferUiState.transferComplete) "Complete!" else "Transferring...",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal
                    ),
                    color = BoopAccentYellow.copy(alpha = 0.8f)
                )
            }
        }

        // ── Bottom accent card ─────────────────────────────────────────────
        val fileName = transferUiState.currentFileName ?: "Unknown file"
        val bytesText = if (transferUiState.totalBytes > 0L) {
            "${transferUiState.transferredBytes.toFormattedSize()} of ${transferUiState.totalBytes.toFormattedSize()}"
        } else {
            "Calculating..."
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(BoopAccentYellow)
                .border(
                    width = 4.dp,
                    color = BoopBlack,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Multi-file counter
            if (transferUiState.totalFiles > 1) {
                Text(
                    text = "File ${transferUiState.currentFileIndex + 1} of ${transferUiState.totalFiles}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = BoopBlack.copy(alpha = 0.7f)
                )
            }

            // File info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderZip,
                        contentDescription = null,
                        tint = BoopBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = BoopBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bytesText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = BoopBlack.copy(alpha = 0.6f)
                    )
                }
            }

            // Progress labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TRANSFER PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = BoopBlack.copy(alpha = 0.7f)
                )
                Text(
                    text = if (transferUiState.transferComplete) "COMPLETE" else "IN PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = BoopBlack.copy(alpha = 0.7f)
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { transferUiState.transferProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BoopBlack,
                trackColor = BoopBlack.copy(alpha = 0.15f)
            )

            // Cancel button — dark on yellow card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BoopShapeMedium)
                    .background(BoopBlack)
                    .clickable(enabled = transferUiState.isTransferring) {
                        Log.d(TAG, "Cancel transfer clicked")
                        haptics.click()
                        onCancelClick()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = BoopAccentYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CANCEL TRANSFER",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = BoopAccentYellow
                    )
                }
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun TransferProgressScreenPreview() {
    BoopTheme(darkTheme = true) {
        TransferProgressScreen(
            transferUiState = TransferUiState(
                isTransferring = true,
                transferProgress = 0.78f,
                transferredBytes = 1_600_000_000L,
                totalBytes = 2_100_000_000L,
                currentFileName = "PROJECT_FINAL_V2.zip"
            ),
            onBackClick = {},
            onCancelClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TransferProgressScreenCompletePreview() {
    BoopTheme(darkTheme = true) {
        TransferProgressScreen(
            transferUiState = TransferUiState(
                isTransferring = false,
                transferComplete = true,
                transferProgress = 1f,
                transferredBytes = 2_100_000_000L,
                totalBytes = 2_100_000_000L,
                currentFileName = "project_report.pdf"
            ),
            onBackClick = {},
            onCancelClick = {}
        )
    }
}
