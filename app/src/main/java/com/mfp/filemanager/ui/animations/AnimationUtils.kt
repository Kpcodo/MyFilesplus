package com.mfp.filemanager.ui.animations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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

// Note: The manual pointerInput above for state might conflict with combinedClickable consuming events.
// A better way for animation state is to hook into the InteractionSource of the clickable.

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    scaleDown: Float = 0.90f,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
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
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                         view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                         onClick?.invoke()
                    },
                    onLongClick = {
                         view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                         onLongClick()
                    }
                )
            } else if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                )
            } else {
                Modifier
            }
        )
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
