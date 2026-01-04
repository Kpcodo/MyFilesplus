package com.mfp.filemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.compositionLocalOf

val LocalAnimationSpeed = compositionLocalOf { 1.0f }

enum class ButtonState { Pressed, Idle }

fun Modifier.bounceClick(
    scaleDown: Float = 0.70f,
    onClick: (() -> Unit)? = null
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val scale by animateFloatAsState(if (buttonState == ButtonState.Pressed) scaleDown else 1f, label = "Bounce Animation")

    val view = androidx.compose.ui.platform.LocalView.current
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
        .then(if (onClick != null) Modifier.clickable(onClick = { 
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            onClick() 
        }) else Modifier)
}

fun Modifier.animateEnter(
    delayMillis: Int = 0
) = composed {
    var visible by remember { mutableStateOf(false) }
    val speed = LocalAnimationSpeed.current
    // If speed is 0 or very fast, snap. But generally we multiply duration.
    // Speed < 1.0 means slower (longer duration). Speed > 1.0 means faster (shorter duration).
    // Actually, usually "Speed 2x" means twice as fast (half duration).
    // Let's assume the value is a multiplier where 2.0 = 2x speed.
    val durationMult = if (speed > 0) 1f / speed else 0f

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (speed >= 10f) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(
            durationMillis = (300 * durationMult).toInt(), 
            delayMillis = (delayMillis * durationMult).toInt()
        ),
        label = "AlphaAnimation"
    )
    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        // Spring doesn't easily take duration, but stiffness affects speed. 
        // Higher stiffness = faster.
        // Let's adjust stiffness based on speed? Or just leave spring as is for "physics" feel 
        // but maybe adjust damping if needed.
        // However, if user wants "Fast", spring should be stiffer.
        animationSpec = if (speed >= 10f) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(
            dampingRatio = 0.8f, 
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow * speed // Approximate scaling
        ),
        label = "TranslationAnimation"
    )

    LaunchedEffect(Unit) {
        visible = true
    }

    this
        .graphicsLayer {
            this.alpha = alpha
            this.translationY = translationY
        }
}
