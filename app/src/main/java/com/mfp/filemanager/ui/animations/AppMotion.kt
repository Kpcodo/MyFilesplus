package com.mfp.filemanager.ui.animations

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.IntOffset

val LocalMotionScale = compositionLocalOf { 1f }

@Composable
fun MotionHardwareProvider(
    content: @Composable () -> Unit
) {
    // In a real implementation this might check battery saver mode or device performance class
    // For now, we just pass through or default to 1f
    val scale = LocalAnimationSpeed.current
    CompositionLocalProvider(LocalMotionScale provides scale) {
        content()
    }
}

object AppMotion {
    object BaseDuration {
        const val Fast = 300
        const val Medium = 500
        const val Slow = 800
    }

    object Specs {
        fun <T> mechanicalSpring(scale: Float = 1f): SpringSpec<T> {
            return spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow / scale.coerceAtLeast(0.1f)
            )
        }
    }

    object Transitions {
        fun enterFromFullScreen(scale: Float): EnterTransition {
             // Zoom out from full screen
             return scaleIn(
                 initialScale = 1.1f,
                 animationSpec = tween((BaseDuration.Fast * scale).toInt())
             ) + fadeIn(animationSpec = tween((BaseDuration.Fast * scale).toInt()))
        }

        fun exitToLibrary(scale: Float): ExitTransition {
            // Shrink back to list
            return scaleOut(
                targetScale = 0.9f,
                animationSpec = tween((BaseDuration.Fast * scale).toInt())
            ) + fadeOut(animationSpec = tween((BaseDuration.Fast * scale).toInt()))
        }

        fun slideInUpEnter(scale: Float): EnterTransition {
            return slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn()
        }
    }
}
