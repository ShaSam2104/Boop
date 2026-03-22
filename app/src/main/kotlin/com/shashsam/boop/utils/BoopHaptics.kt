package com.shashsam.boop.utils

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** CompositionLocal controlling whether haptic feedback is enabled in-app. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Provides themed haptic feedback that respects the in-app vibration toggle.
 */
class BoopHaptics(private val view: View, private val enabled: Boolean) {

    /** Light tick — for toggle switches, small interactions. */
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Medium click — for button presses, mode selection. */
    fun click() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** Heavy press — for the main CTA (BOOP IT). */
    fun heavy() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/** Remember a [BoopHaptics] instance that respects [LocalHapticsEnabled]. */
@Composable
fun rememberBoopHaptics(): BoopHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { BoopHaptics(view, enabled) }
}
