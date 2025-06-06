package com.example.aibuddy.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedEyes() {
    val blinkDuration = 2000 // Adjust blink duration as needed
    val eyeOpenValue = 1f
    val eyeClosedValue = 0.1f // Adjust closed eye height as needed

    val eyeHeight = remember { Animatable(eyeOpenValue) }

    LaunchedEffect(key1 = true) {
        while (true) {
            eyeHeight.animateTo(
                targetValue = eyeClosedValue,
                animationSpec = tween(
                    durationMillis = blinkDuration / 20,
                    easing = LinearEasing
                )
            )
            eyeHeight.animateTo(
                targetValue = eyeOpenValue,
                animationSpec = tween(
                    durationMillis = blinkDuration / 20,
                    easing = LinearEasing
                )
            )
            delay(blinkDuration.toLong())
        }
    }

    Canvas(modifier = Modifier.size(100.dp)) {
        val eyeWidth = size.width / 3
        val eyeCenterY = size.height / 2

        // Left eye
        drawOval(
            color = Color.Black,
            topLeft = Offset(eyeWidth / 2, eyeCenterY - (eyeWidth / 2) * eyeHeight.value),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeWidth * eyeHeight.value)
        )

        // Right eye
        drawOval(
            color = Color.Black,
            topLeft = Offset(size.width - eyeWidth * 1.5f, eyeCenterY - (eyeWidth / 2) * eyeHeight.value),
            size = androidx.compose.ui.geometry.Size(eyeWidth, eyeWidth * eyeHeight.value)
        )
    }
}
