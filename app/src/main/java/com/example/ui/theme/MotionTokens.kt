package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Exact Motion System for Kanak matching better-ui specifications.
 * Authoritative easing curves, durations, bounce: 0 springs, and reduced-motion safety.
 */
object MotionCurves {
    // Exact cubic-bezier(0.2, 0, 0, 1) ease-out
    val EaseOut = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // Standard smooth ease-in-out
    val EaseInOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // Directional ease for drawers/sheets
    val EaseDrawer = CubicBezierEasing(0.32f, 0.72f, 0.0f, 1.0f)
}

object MotionDuration {
    const val ButtonPress = 150
    const val Tooltip = 150
    const val Dropdown = 150
    const val Modal = 200
    const val Drawer = 300
    const val TabSwitch = 150
    const val StaggerStep = 100
}

object MotionSprings {
    // Springs strictly configured with bounce: 0 (DampingRatioNoBouncy = 1.0f)
    val Pop = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val Gentle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    val ZeroBounce = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
}

/**
 * Composition local providing system-wide reduced motion state.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberIsReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale == 0.0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Exact button press scale feedback:
 * Scales to 0.96 over 150ms with ease-out (cubic-bezier(0.2, 0, 0, 1)).
 * Disabled controls never scale. Gated behind reduced-motion preference.
 */
fun Modifier.tactilePress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val isReducedMotion = LocalReducedMotion.current
    if (isReducedMotion || !enabled) {
        return@composed this
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        animationSpec = tween(
            durationMillis = MotionDuration.ButtonPress,
            easing = MotionCurves.EaseOut
        ),
        label = "tactile_press_scale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
