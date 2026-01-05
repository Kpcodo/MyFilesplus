package com.mfp.filemanager.ui.animations

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
    scaleDown: Float = 0.90f, // Less aggressive scale for smoother feel
    onClick: (() -> Unit)? = null
) = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    // Use a high-stiffness spring for snappy feedback, unaffected by global speed for consistency
    val scale by animateFloatAsState(
        targetValue = if (buttonState == ButtonState.Pressed) scaleDown else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "Bounce Animation"
    )

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
        .then(if (onClick != null) Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, // Disable default ripple to emphasize bounce
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        ) else Modifier)
}

fun Modifier.animateEnter(
    delayMillis: Int = 0
) = composed {
    var visible by remember { mutableStateOf(false) }
    val speed = LocalAnimationSpeed.current
    
    // Calculate stiffness based on speed preference. 
    // Speed 1.0 = StiffnessLow (200f). 
    // Higher speed = Higher stiffness (faster).
    val baseStiffness = androidx.compose.animation.core.Spring.StiffnessLow
    val effectiveStiffness = if (speed > 0) baseStiffness * (speed * speed) else baseStiffness

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            stiffness = effectiveStiffness
        ),
        label = "AlphaAnimation"
    )
    
    val translationY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 0f else 50f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = effectiveStiffness
        ),
        label = "TranslationAnimation"
    )

    LaunchedEffect(Unit) {
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis.toLong())
        }
        visible = true
    }

    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY
    }
}
