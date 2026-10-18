package com.sentinel.music.presentation.components.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.domain.usecase.ObserveMediaProgressUseCase

@Composable
fun SmoothProgressBar(
    baseProgressMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit
) {
    val useCase = remember { ObserveMediaProgressUseCase() }
    val progressRatio by useCase(baseProgressMs, durationMs, isPlaying)
        .collectAsState(initial = if (durationMs > 0) (baseProgressMs.toFloat() / durationMs.toFloat()) else 0f)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val activeProgress = if (isDragging) dragProgress else progressRatio

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(durationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isDragging = true
                        dragProgress = (down.position.x / size.width).coerceIn(0f, 1f)

                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) break
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }

                        isDragging = false
                        onSeek((dragProgress * durationMs).toLong())
                    }
                }
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.surfaceVariant

            Canvas(modifier = Modifier.fillMaxSize()) {
                val barHeight = 4.dp.toPx()
                val yOffset = (size.height - barHeight) / 2

                // Флаг фоновой полосы
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(0f, yOffset),
                    size = Size(size.width, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )

                // Заполненная часть
                val activeWidth = size.width * activeProgress
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(0f, yOffset),
                    size = Size(activeWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )

                // Круглый ползунок
                drawCircle(
                    color = Color.White,
                    radius = if (isDragging) 8.dp.toPx() else 5.dp.toPx(),
                    center = Offset(activeWidth, size.height / 2)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentSec = ((activeProgress * durationMs) / 1000).toLong()
            val totalSec = (durationMs / 1000)
            Text(formatTime(currentSec), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text(formatTime(totalSec), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%d:%02d", m, s)
}
