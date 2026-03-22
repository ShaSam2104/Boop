package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shashsam.boop.ui.components.NfcAntennaGuide
import com.shashsam.boop.ui.components.NfcAntennaPosition
import com.shashsam.boop.ui.components.rememberNfcAntennaPosition
import com.shashsam.boop.ui.theme.BoopTheme
import com.shashsam.boop.ui.theme.NeoBrutalistButton
import com.shashsam.boop.utils.rememberBoopHaptics

private const val TAG = "NfcGuideScreen"

/**
 * Full-screen dialog presenting the NFC antenna location guide.
 *
 * Matches the "NFC Guide - Solid Geometric V3" design spec: pure black
 * background, accent yellow highlights, neo-brutalist button, and the
 * reusable [NfcAntennaGuide] phone visualization with pulsing antenna dot.
 *
 * @param onDismiss Callback invoked when the user taps the "Got it" button.
 * @param modifier  Modifier for the root container.
 */
@Composable
fun NfcGuideScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Log.d(TAG, "NfcGuideScreen composed")

    val haptics = rememberBoopHaptics()
    val antennaPosition = rememberNfcAntennaPosition()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Title ────────────────────────────────────────────────────────
        Text(
            text = "NFC Guide",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Large instruction ────────────────────────────────────────────
        Text(
            text = "Tap phones\ntogether here",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Subtext ──────────────────────────────────────────────────────
        Text(
            text = "Align the top of your phone with the other device to Boop!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Phone visualization ──────────────────────────────────────────
        NfcAntennaGuide(antennaPosition = antennaPosition)

        Spacer(modifier = Modifier.height(12.dp))

        // ── "contactless" label with NFC icon ────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Nfc,
                contentDescription = "NFC",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "contactless",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // ── "Got it" button ──────────────────────────────────────────────
        NeoBrutalistButton(onClick = {
            Log.d(TAG, "Got it button tapped — dismissing NFC guide")
            haptics.click()
            onDismiss()
        }) {
            Text(
                text = "Got it",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NfcGuideScreenPreview() {
    BoopTheme(darkTheme = true) {
        NfcGuideScreen(onDismiss = {})
    }
}
