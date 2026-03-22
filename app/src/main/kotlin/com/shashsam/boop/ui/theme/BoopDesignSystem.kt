package com.shashsam.boop.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Radius constants ───────────────────────────────────────────────────────

val BoopRadiusSmall = 8.dp
val BoopRadiusMedium = 16.dp
val BoopRadiusLarge = 24.dp
val BoopRadiusXL = 32.dp

val BoopBorderEmphasis = 4.dp

// ─── Shapes ─────────────────────────────────────────────────────────────────

val BoopShapeSmall = RoundedCornerShape(BoopRadiusSmall)
val BoopShapeMedium = RoundedCornerShape(BoopRadiusMedium)
val BoopShapeLarge = RoundedCornerShape(BoopRadiusLarge)
val BoopShapeXL = RoundedCornerShape(BoopRadiusXL)

// ─── Neo-brutalist Button ───────────────────────────────────────────────────

/**
 * A button with a directional box-shadow in the accent color, matching
 * the Stitch "Solid Geometric" neo-brutalist design language.
 *
 * @param onClick     Action on click.
 * @param modifier    Modifier for the outer container.
 * @param enabled     Whether the button is interactive.
 * @param shadowColor The shadow rectangle color (default: accent yellow).
 * @param shadowOffset Shadow offset in dp (default: 6dp).
 * @param content     Button content (text, icon, etc.).
 */
@Composable
fun NeoBrutalistButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shadowColor: Color = LocalBoopTokens.current.accent,
    shadowOffset: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOffset = if (isPressed) 2.dp else shadowOffset
    val effectiveShadowColor = if (enabled) shadowColor else shadowColor.copy(alpha = 0.3f)

    Box(modifier = modifier) {
        // Shadow rectangle drawn behind
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = currentOffset, y = currentOffset)
                .clip(BoopShapeMedium)
                .background(effectiveShadowColor)
        )
        // Foreground button
        Row(
            modifier = Modifier
                .clip(BoopShapeMedium)
                .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    BorderStroke(2.dp, if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
                    BoopShapeMedium
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

// ─── Glass Card ─────────────────────────────────────────────────────────────

/**
 * A semi-transparent card with thin border, approximating a glass/frosted effect.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = LocalBoopTokens.current
    Card(
        modifier = modifier,
        shape = BoopShapeMedium,
        colors = CardDefaults.cardColors(containerColor = tokens.glassBg),
        border = BorderStroke(1.dp, tokens.glassBorder)
    ) {
        content()
    }
}

// ─── Glow Modifier ──────────────────────────────────────────────────────────

/**
 * Draws a soft glow behind the composable using the accent color.
 */
fun Modifier.boopGlow(
    color: Color = BoopGlowYellow,
    radius: Dp = 40.dp,
    spread: Dp = 10.dp
): Modifier = this.drawBehind {
    val radiusPx = radius.toPx()
    val spreadPx = spread.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-spreadPx, -spreadPx),
        size = Size(size.width + spreadPx * 2, size.height + spreadPx * 2),
        cornerRadius = CornerRadius(radiusPx)
    )
}

// ─── Bottom Navigation Bar ──────────────────────────────────────────────────

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("Home", Icons.Filled.Home, "home"),
    BottomNavItem("History", Icons.Filled.History, "history"),
    BottomNavItem("Profile", Icons.Filled.Person, "profile")
)

/**
 * Custom geometric bottom navigation bar matching the Stitch "Solid Geometric" design.
 *
 * Dark background, accent-colored selected state with rounded indicator,
 * uppercase labels, and outlined-style icons.
 */
@Composable
fun BoopBottomNavBar(
    currentRoute: String?,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBoopTokens.current
    NavigationBar(
        modifier = modifier,
        containerColor = tokens.navBarContainer,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(item) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = item.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = tokens.navIndicator
                )
            )
        }
    }
}
