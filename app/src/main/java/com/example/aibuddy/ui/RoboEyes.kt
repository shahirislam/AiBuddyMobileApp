package com.example.aibuddy.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val eyeShineColor = Color(0xFF00E5FF)
val pupilColor = Color.Black

@Composable
fun RoboEyes(
    modifier: Modifier = Modifier,
    isAiSpeaking: Boolean = false,
    isListeningToUser: Boolean = false,
    baseEyeSize: Dp = 80.dp,
    eyeCornerRadius: Dp = 16.dp,
    pupilRadiusFactor: Float = 0.3f
) {
    val animatedEyeSize by animateDpAsState(
        targetValue = when {
            isAiSpeaking -> baseEyeSize * 1.2f
            isListeningToUser -> baseEyeSize * 1.1f
            else -> baseEyeSize
        },
        animationSpec = tween(durationMillis = 300), label = "eyeSizeAnimation"
    )

    val blinkOpenValue = 1.0f
    val blinkClosedValue = 0.1f
    val eyeBlinkFactor = remember { Animatable(blinkOpenValue) }

    LaunchedEffect(key1 = Unit) {
        while (true) {
            delay( (2000..5000).random().toLong()) // Random delay between blinks
            eyeBlinkFactor.animateTo(blinkClosedValue, animationSpec = tween(100))
            eyeBlinkFactor.animateTo(blinkOpenValue, animationSpec = tween(150))
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(animatedEyeSize / 3)
    ) {
        SingleRoboEye(
            eyeSize = animatedEyeSize,
            pupilRadiusFactor = pupilRadiusFactor,
            cornerRadius = eyeCornerRadius,
            blinkFactor = eyeBlinkFactor.value
        )
        SingleRoboEye(
            eyeSize = animatedEyeSize,
            pupilRadiusFactor = pupilRadiusFactor,
            cornerRadius = eyeCornerRadius,
            blinkFactor = eyeBlinkFactor.value
        )
    }
}

@Composable
fun SingleRoboEye(
    modifier: Modifier = Modifier,
    eyeSize: Dp,
    pupilRadiusFactor: Float,
    cornerRadius: Dp,
    blinkFactor: Float
) {
    Canvas(modifier = modifier.size(eyeSize)) {
        val sizePx = eyeSize.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        
        // Apply blink factor to height
        val eyeDisplayHeight = sizePx * blinkFactor
        val verticalOffset = (sizePx - eyeDisplayHeight) / 2

        // Draw the main eye shape (rounded square)
        drawRoundRect(
            color = eyeShineColor,
            topLeft = Offset(0f, verticalOffset),
            size = Size(sizePx, eyeDisplayHeight),
            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        )

        if (blinkFactor > 0.15f) {
            val pupilRadius = (sizePx / 2) * pupilRadiusFactor
            val pupilCenterY = verticalOffset + (eyeDisplayHeight / 2)
            drawCircle(
                color = pupilColor,
                radius = pupilRadius,
                center = Offset(sizePx / 2, pupilCenterY)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF333333)
@Composable
fun RoboEyesPreviewIdle() {
    RoboEyes(isAiSpeaking = false, isListeningToUser = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF333333)
@Composable
fun RoboEyesPreviewSpeaking() {
    RoboEyes(isAiSpeaking = true, isListeningToUser = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF333333)
@Composable
fun RoboEyesPreviewListening() {
    RoboEyes(isAiSpeaking = false, isListeningToUser = true)
}
