package com.shashsam.boop.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
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
    onResetClick: () -> Unit,
    onNfcGuideClick: () -> Unit = {},
    onViewAllClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "HomeScreen recompose — permissionsGranted=$permissionsGranted")
    val tokens = LocalBoopTokens.current
    val haptics = rememberBoopHaptics()
    val context = LocalContext.current

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
            Row {
                IconButton(onClick = {
                    try {
                        val upiUri = Uri.parse("upi://pay?pa=03.shubhamshah-1@oksbi&pn=Boop&tn=Buy%20me%20a%20Chai&cu=INR")
                        context.startActivity(Intent(Intent.ACTION_VIEW, upiUri))
                    } catch (e: Exception) {
                        Log.w(TAG, "No UPI app available", e)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Buy me a Chai",
                        tint = tokens.accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onNfcGuideClick) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "NFC Guide",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

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
            isTransferring = transferUiState.isTransferring,
            onSendClick = onSendClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Instruction text ───────────────────────────────────────────────
        Text(
            text = "Tap to send files, or\nhold near another phone to receive",
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Recent Boops ───────────────────────────────────────────────────
        if (transferUiState.recentTransfers.isNotEmpty()) {
            RecentBoopsSection(
                recentTransfers = transferUiState.recentTransfers,
                onViewAllClick = onViewAllClick,
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

// ─── Morphing Aurora Blob CTA ──────────────────────────────────────────────

private const val BLOB_POINTS = 8
private const val TWO_PI = (2 * Math.PI).toFloat()

/**
 * Morphing aurora blob CTA — replaces the concentric circle CTA.
 * 8 control points on a base circle, displaced by animated sine waves at different
 * frequencies/phases. Points connected via cubic Bezier curves. Filled with a
 * rotating sweep gradient (purple <-> yellow). When active, amplitudes increase
 * and animation speeds up.
 */
@Composable
private fun ConcentricCircleCta(
    permissionsGranted: Boolean,
    isTransferring: Boolean,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberBoopHaptics()
    val isActive = true

    val infiniteTransition = rememberInfiniteTransition(label = "auroraBlob")

    // 5 animators at different speeds for organic variation
    val t1 = infiniteTransition.animateFloat(
        0f, TWO_PI,
        infiniteRepeatable(tween(if (isActive) 3000 else 5000, easing = LinearEasing), RepeatMode.Restart),
        label = "t1"
    )
    val t2 = infiniteTransition.animateFloat(
        0f, TWO_PI,
        infiniteRepeatable(tween(if (isActive) 3800 else 6000, easing = LinearEasing), RepeatMode.Restart),
        label = "t2"
    )
    val t3 = infiniteTransition.animateFloat(
        0f, TWO_PI,
        infiniteRepeatable(tween(if (isActive) 4500 else 5500, easing = LinearEasing), RepeatMode.Restart),
        label = "t3"
    )
    val t4 = infiniteTransition.animateFloat(
        0f, TWO_PI,
        infiniteRepeatable(tween(if (isActive) 3500 else 4800, easing = LinearEasing), RepeatMode.Restart),
        label = "t4"
    )
    // Gradient rotation
    val gradientRotation = infiniteTransition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "gradRot"
    )

    val amplitudeFactor = if (isActive) 1.0f else 0.5f
    val purpleColor = Color(0xFF736DEE)
    val yellowColor = BoopAccentYellow

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .drawBehind {
                val cx = size.width / 2
                val cy = size.height / 2
                val baseRadius = minOf(size.width, size.height) / 2 - 20.dp.toPx()

                // Secondary larger transparent glow blob
                val glowPath = buildBlobPath(
                    cx, cy,
                    baseRadius = baseRadius * 1.15f,
                    amplitudes = floatArrayOf(12f, 8f, 10f, 6f, 9f, 11f, 7f, 13f),
                    phases = floatArrayOf(0.3f, 1.2f, 2.1f, 0.8f, 1.7f, 2.5f, 0.5f, 1.9f),
                    t1 = t1.value, t2 = t2.value, t3 = t3.value, t4 = t4.value,
                    amplitudeFactor = amplitudeFactor * 0.7f
                )
                rotate(gradientRotation.value * 0.7f) {
                    drawPath(
                        glowPath,
                        Brush.sweepGradient(
                            colors = listOf(
                                purpleColor.copy(alpha = 0.08f),
                                yellowColor.copy(alpha = 0.06f),
                                purpleColor.copy(alpha = 0.08f)
                            ),
                            center = Offset(cx, cy)
                        )
                    )
                }

                // Primary blob
                val blobPath = buildBlobPath(
                    cx, cy,
                    baseRadius = baseRadius,
                    amplitudes = floatArrayOf(15f, 10f, 18f, 8f, 14f, 12f, 16f, 9f),
                    phases = floatArrayOf(0f, 0.8f, 1.5f, 2.3f, 0.4f, 1.1f, 2.0f, 2.8f),
                    t1 = t1.value, t2 = t2.value, t3 = t3.value, t4 = t4.value,
                    amplitudeFactor = amplitudeFactor
                )
                rotate(gradientRotation.value) {
                    drawPath(
                        blobPath,
                        Brush.sweepGradient(
                            colors = listOf(
                                purpleColor.copy(alpha = 0.25f),
                                yellowColor.copy(alpha = 0.15f),
                                purpleColor.copy(alpha = 0.20f),
                                yellowColor.copy(alpha = 0.18f),
                                purpleColor.copy(alpha = 0.25f)
                            ),
                            center = Offset(cx, cy)
                        )
                    )
                }
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
                .clickable(enabled = permissionsGranted && !isTransferring) {
                    Log.d(TAG, "BOOP IT clicked — opening file picker")
                    haptics.heavy()
                    onSendClick()
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

/**
 * Builds a closed [Path] by placing [BLOB_POINTS] control points on a circle of
 * [baseRadius], displacing each radially by the sum of sine waves, then connecting
 * adjacent points with cubic Bezier curves.
 */
private fun buildBlobPath(
    cx: Float, cy: Float,
    baseRadius: Float,
    amplitudes: FloatArray,
    phases: FloatArray,
    t1: Float, t2: Float, t3: Float, t4: Float,
    amplitudeFactor: Float
): Path {
    val points = Array(BLOB_POINTS) { i ->
        val angle = TWO_PI * i / BLOB_POINTS
        val displacement = (
            amplitudes[i] * kotlin.math.sin(t1 + phases[i]) +
            amplitudes[(i + 3) % BLOB_POINTS] * kotlin.math.sin(t2 + phases[(i + 1) % BLOB_POINTS] * 1.3f) +
            amplitudes[(i + 5) % BLOB_POINTS] * kotlin.math.sin(t3 + phases[(i + 2) % BLOB_POINTS] * 0.7f) +
            amplitudes[(i + 1) % BLOB_POINTS] * kotlin.math.sin(t4 + phases[(i + 3) % BLOB_POINTS] * 1.1f)
        ) * amplitudeFactor
        val r = baseRadius + displacement
        Offset(
            cx + r * kotlin.math.cos(angle),
            cy + r * kotlin.math.sin(angle)
        )
    }

    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until BLOB_POINTS) {
            val curr = points[i]
            val next = points[(i + 1) % BLOB_POINTS]
            val prev = points[(i - 1 + BLOB_POINTS) % BLOB_POINTS]
            val nextNext = points[(i + 2) % BLOB_POINTS]

            // Catmull-Rom to cubic Bezier control points
            val cp1x = curr.x + (next.x - prev.x) / 6f
            val cp1y = curr.y + (next.y - prev.y) / 6f
            val cp2x = next.x - (nextNext.x - curr.x) / 6f
            val cp2y = next.y - (nextNext.y - curr.y) / 6f

            cubicTo(cp1x, cp1y, cp2x, cp2y, next.x, next.y)
        }
        close()
    }
}

// ─── Recent Boops ───────────────────────────────────────────────────────────

@Composable
private fun RecentBoopsSection(
    recentTransfers: List<RecentBoop>,
    onViewAllClick: () -> Unit = {},
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
                color = tokens.accent,
                modifier = Modifier.clickable { onViewAllClick() }
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
            onResetClick = {}
        )
    }
}
