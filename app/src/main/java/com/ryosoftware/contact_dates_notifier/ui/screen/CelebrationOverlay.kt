package com.ryosoftware.contact_dates_notifier.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private data class Balloon(
    val x: Float,
    val y: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val size: Float,
    val swing: Float,
    val stringLength: Float
)

@Composable
fun CelebrationOverlay(
    onDismiss: () -> Unit
) {
    val colors = remember {
        listOf(
            Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
            Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA),
            Color(0xFFFCBAD3), Color(0xFFA8D8EA), Color(0xFFFFA07A),
            Color(0xFF98D8C8)
        )
    }

    var balloons by remember { mutableStateOf(listOf<Balloon>()) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (true) {
            delay(16.milliseconds)
            val now = System.nanoTime()
            val delta = (now - lastTime) / 1_000_000_000f
            lastTime = now
            if (delta <= 0f || delta > 0.5f) continue
            time += delta

            val updated = mutableListOf<Balloon>()
            if (balloons.size < 35) {
                repeat((delta * 8).toInt().coerceIn(1, 3)) {
                    updated.add(
                        Balloon(
                            x = 0.1f + Random.nextFloat() * 0.8f,
                            y = 1.2f + Random.nextFloat() * 0.15f,
                            velocityX = (Random.nextFloat() - 0.5f) * 0.08f,
                            velocityY = -(0.15f + Random.nextFloat() * 0.3f),
                            color = colors.random(),
                            size = 34f + Random.nextFloat() * 22f,
                            swing = (Random.nextFloat() - 0.5f) * 3f,
                            stringLength = 55f + Random.nextFloat() * 35f
                        )
                    )
                }
            }

            for (b in balloons) {
                val newX = b.x + b.velocityX * delta + sin(b.y * 10f + time) * 0.003f
                val newY = b.y + b.velocityY * delta
                if (newY >= -0.1f) {
                    updated.add(b.copy(x = newX, y = newY))
                }
            }

            balloons = updated
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable { onDismiss() }
    ) {
        val w = size.width
        val h = size.height
        for (b in balloons) {
            val cx = b.x * w
            val cy = b.y * h
            val r = b.size / 2f
            val alpha = ((1f - cy / h) * 0.9f).coerceIn(0.3f, 0.9f)
            val color = b.color.copy(alpha = alpha)

            val stringTop = cy + r * 0.8f
            val stringBot = stringTop + b.stringLength
            val sway = sin(b.y * 8f + time * 2f) * 3f
            val stringPath = Path().apply {
                moveTo(cx, stringTop)
                cubicTo(
                    cx + sway * 0.3f, stringTop + b.stringLength * 0.3f,
                    cx + sway * 0.7f, stringTop + b.stringLength * 0.6f,
                    cx + sway, stringBot
                )
            }
            drawPath(stringPath, color = Color(0x88FFFFFF), style = Stroke(width = 1.5f))

            drawOval(
                color = color,
                topLeft = Offset(cx - r, cy - r * 1.2f),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2.4f)
            )

            val knotSize = r * 0.15f
            val knotY = cy + r * 1.2f
            val knotPath = Path().apply {
                moveTo(cx - knotSize, knotY)
                lineTo(cx + knotSize, knotY)
                lineTo(cx, knotY + knotSize * 1.5f)
                close()
            }
            drawPath(knotPath, color = color.copy(alpha = alpha * 0.8f))
        }
    }
}
