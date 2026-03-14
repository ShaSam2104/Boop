package com.shashsam.boop.ui.components

import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val TAG = "NfcAntennaGuide"

/**
 * Normalized NFC antenna position within the device body.
 *
 * Coordinates are in `[0, 1]` range where `(0, 0)` is the **top-left** corner
 * of the phone back and `(1, 1)` is the **bottom-right**.
 *
 * @param x          Horizontal position (0 = left, 1 = right).
 * @param y          Vertical position (0 = top, 1 = bottom).
 * @param isFallback `true` when using a generic estimate because the device
 *                   is running Android 13 or lower (API < 34).
 */
data class NfcAntennaPosition(
    val x: Float,
    val y: Float,
    val isFallback: Boolean
)

// ─── Antenna position resolver ──────────────────────────────────────────────

/**
 * Resolves the physical NFC antenna position for the current device.
 *
 * On Android 14+ (API 34) this calls [NfcAdapter.getNfcAntennaInfo] and maps
 * the millimetre coordinates to a normalized `[0, 1]` range. On older devices,
 * or when the API returns `null`, a **top-center fallback** is used because
 * that is the most common antenna placement.
 *
 * The result is [remember]ed so the (potentially expensive) system query runs
 * only once per composition lifetime.
 */
@Composable
fun rememberNfcAntennaPosition(): NfcAntennaPosition {
    val context = LocalContext.current
    return remember {
        resolveAntennaPosition(NfcAdapter.getDefaultAdapter(context))
    }
}

/**
 * Pure logic for resolving the antenna position — extracted so the nullable
 * handling and Log.d proof are clearly separated from Compose state.
 */
internal fun resolveAntennaPosition(adapter: NfcAdapter?): NfcAntennaPosition {
    // Default: top-center — the most common NFC antenna location on Android phones.
    val fallback = NfcAntennaPosition(x = 0.5f, y = 0.18f, isFallback = true)

    // ── Guard: API level ────────────────────────────────────────────────────
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Log.d(TAG, "API ${Build.VERSION.SDK_INT} < 34 — using fallback antenna position (top-center)")
        return fallback
    }

    // ── Guard: NfcAdapter availability ──────────────────────────────────────
    if (adapter == null) {
        Log.d(TAG, "NfcAdapter is null — using fallback antenna position")
        return fallback
    }

    // ── API 34+: query real antenna info ────────────────────────────────────
    val antennaInfo = adapter.nfcAntennaInfo
    if (antennaInfo == null) {
        Log.d(TAG, "getNfcAntennaInfo() returned null — using fallback antenna position")
        return fallback
    }

    val deviceWidth = antennaInfo.deviceWidth   // millimetres
    val deviceHeight = antennaInfo.deviceHeight // millimetres
    Log.d(
        TAG,
        "Device dimensions: ${deviceWidth}mm x ${deviceHeight}mm, foldable=${antennaInfo.isDeviceFoldable}"
    )

    val antennas = antennaInfo.availableNfcAntennas
    if (antennas.isEmpty()) {
        Log.d(TAG, "availableNfcAntennas is empty — using fallback position")
        return fallback
    }

    // Use the first (primary) antenna.
    val antenna = antennas.first()
    val locationX = antenna.locationX // mm from left edge
    val locationY = antenna.locationY // mm from bottom edge

    // ── Iterative Proof: log the raw coordinates from the API ───────────────
    Log.d(TAG, "antennaInfo.locationX=${locationX}mm, antennaInfo.locationY=${locationY}mm")

    // Normalize to [0, 1].  The API measures Y from the *bottom*, but our
    // Canvas origin is at the *top*, so we flip.
    val normalizedX = if (deviceWidth > 0) locationX.toFloat() / deviceWidth else 0.5f
    val normalizedY = if (deviceHeight > 0) 1f - (locationY.toFloat() / deviceHeight) else 0.18f

    Log.d(TAG, "Normalized antenna position: x=$normalizedX, y=$normalizedY")

    return NfcAntennaPosition(
        x = normalizedX.coerceIn(0.08f, 0.92f),
        y = normalizedY.coerceIn(0.08f, 0.92f),
        isFallback = false
    )
}

// ─── Compose UI ─────────────────────────────────────────────────────────────

/**
 * Draws a stylized back-of-phone outline with a pulsing Material 3 ripple
 * indicator at the NFC antenna location.
 *
 * When [NfcAntennaPosition.isFallback] is `true`, a hint label is shown
 * below the illustration.
 */
@Composable
fun NfcAntennaGuide(
    antennaPosition: NfcAntennaPosition,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // Three ripple rings, staggered by ~667 ms to create a continuous pulse.
    val transition = rememberInfiniteTransition(label = "nfcPulse")

    val ripple1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1"
    )
    val ripple2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple2"
    )
    val ripple3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple3"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(130.dp)
                .height(240.dp)
        ) {
            val w = size.width
            val h = size.height
            val bodyCorner = CornerRadius(24.dp.toPx())

            // ── Phone body ──────────────────────────────────────────────
            drawRoundRect(
                color = surfaceVariant,
                size = Size(w, h),
                cornerRadius = bodyCorner
            )
            drawRoundRect(
                color = outline,
                size = Size(w, h),
                cornerRadius = bodyCorner,
                style = Stroke(width = 2.dp.toPx())
            )

            // ── Side buttons (right edge: power) ────────────────────────
            val btnWidth = 3.dp.toPx()
            val powerBtnH = 28.dp.toPx()
            val powerBtnY = h * 0.25f
            drawRoundRect(
                color = outline.copy(alpha = 0.5f),
                topLeft = Offset(w - 0.5.dp.toPx(), powerBtnY),
                size = Size(btnWidth, powerBtnH),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            // Volume buttons (left edge — flush to body to avoid negative-offset clipping)
            val volBtnH = 20.dp.toPx()
            drawRoundRect(
                color = outline.copy(alpha = 0.5f),
                topLeft = Offset(0f, h * 0.22f),
                size = Size(btnWidth, volBtnH),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )
            drawRoundRect(
                color = outline.copy(alpha = 0.5f),
                topLeft = Offset(0f, h * 0.22f + volBtnH + 6.dp.toPx()),
                size = Size(btnWidth, volBtnH),
                cornerRadius = CornerRadius(1.5.dp.toPx())
            )

            // ── Camera module (top-left, back view) ─────────────────────
            val camW = 34.dp.toPx()
            val camH = 34.dp.toPx()
            val camX = w * 0.12f
            val camY = h * 0.035f
            val camCorner = CornerRadius(9.dp.toPx())

            drawRoundRect(
                color = outline.copy(alpha = 0.1f),
                topLeft = Offset(camX, camY),
                size = Size(camW, camH),
                cornerRadius = camCorner
            )
            drawRoundRect(
                color = outline.copy(alpha = 0.35f),
                topLeft = Offset(camX, camY),
                size = Size(camW, camH),
                cornerRadius = camCorner,
                style = Stroke(width = 1.dp.toPx())
            )
            // Lenses
            val lensR = 4.5.dp.toPx()
            drawCircle(
                color = outline.copy(alpha = 0.3f),
                radius = lensR,
                center = Offset(camX + camW * 0.35f, camY + camH * 0.35f)
            )
            drawCircle(
                color = outline.copy(alpha = 0.3f),
                radius = lensR,
                center = Offset(camX + camW * 0.65f, camY + camH * 0.65f)
            )
            // Flash
            val flashR = 2.5.dp.toPx()
            drawCircle(
                color = outline.copy(alpha = 0.2f),
                radius = flashR,
                center = Offset(camX + camW * 0.65f, camY + camH * 0.2f)
            )

            // ── NFC antenna indicator ───────────────────────────────────
            val ax = w * antennaPosition.x
            val ay = h * antennaPosition.y
            val maxRippleR = 34.dp.toPx()

            // Pulsing concentric ripple rings
            drawRippleRing(primary, ripple1, ax, ay, maxRippleR)
            drawRippleRing(primary, ripple2, ax, ay, maxRippleR)
            drawRippleRing(primary, ripple3, ax, ay, maxRippleR)

            // Soft glow behind center dot
            drawCircle(
                color = primary.copy(alpha = 0.15f),
                radius = 12.dp.toPx(),
                center = Offset(ax, ay)
            )
            // Center dot
            drawCircle(
                color = primary,
                radius = 5.dp.toPx(),
                center = Offset(ax, ay)
            )

            // Dashed crosshair (subtle alignment guide)
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f
            )
            val crossHairAlpha = 0.18f
            // Horizontal
            drawLine(
                color = primary.copy(alpha = crossHairAlpha),
                start = Offset(12.dp.toPx(), ay),
                end = Offset(w - 12.dp.toPx(), ay),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
            // Vertical
            drawLine(
                color = primary.copy(alpha = crossHairAlpha),
                start = Offset(ax, 12.dp.toPx()),
                end = Offset(ax, h - 12.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "NFC Sweet Spot",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = onSurfaceVariant
        )

        if (antennaPosition.isFallback) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tap the top back of your device",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Draws a single expanding ripple ring at [cx], [cy].
 *
 * @param progress Animation progress in `[0, 1]` — controls both the radius
 *                 (0 → [maxRadius]) and the alpha (opaque → transparent).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRippleRing(
    color: Color,
    progress: Float,
    cx: Float,
    cy: Float,
    maxRadius: Float
) {
    val radius = maxRadius * progress
    val alpha = (1f - progress) * 0.5f
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )
}
