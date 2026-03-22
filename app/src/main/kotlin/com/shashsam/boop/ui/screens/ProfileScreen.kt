package com.shashsam.boop.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import com.shashsam.boop.ui.theme.BoopTheme

private const val TAG = "ProfileScreen"

/**
 * Stub screen for the Profile tab.
 *
 * Displays a centered placeholder with a person icon and instructional text.
 * Styled to match the dark geometric / neo-brutalist Boop design system.
 *
 * @param modifier Modifier for the root container.
 */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Log.d(TAG, "ProfileScreen composed")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Icon ─────────────────────────────────────────────────────────
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = "Profile",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Title ────────────────────────────────────────────────────────
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Body text ────────────────────────────────────────────────────
        Text(
            text = "Device info and preferences",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ProfileScreenPreview() {
    BoopTheme(darkTheme = true) {
        ProfileScreen()
    }
}
