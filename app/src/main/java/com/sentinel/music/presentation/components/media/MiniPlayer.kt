package com.sentinel.music.presentation.components.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.api.TrackDto
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    track: TrackDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    currentProgressMs: Long,
    durationMs: Long,
    serverUrl: String,
    token: String,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onClick: () -> Unit,
    onNext: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    activeDeviceName: String? = null,
    onOpenDevices: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val progressFraction = if (durationMs > 0) {
        (currentProgressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "MiniPlayerOffset"
    )

    val density = LocalDensity.current
    val swipeThreshold = with(density) { 50.dp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .offset { IntOffset(animatedOffset.roundToInt(), 0) }
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(track.id) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffset = 0f },
                    onDragEnd = {
                        if (dragOffset < -swipeThreshold) {
                            onNext?.invoke()
                        } else if (dragOffset > swipeThreshold) {
                            onPrev?.invoke()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceIn(-200f, 200f)
                    }
                )
            }
            .clickable { onClick() },
        color = Color(0xFF24202C),
        tonalElevation = 6.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Обложка трека
                TrackCover(
                    trackId = track.id,
                    serverUrl = serverUrl,
                    token = token,
                    size = 46.dp,
                    shapeRadius = 8.dp
                )

                Spacer(Modifier.width(12.dp))

                // Название и артист
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeDeviceName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Cast,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Играет на: $activeDeviceName",
                                color = Color(0xFF1DB954),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = track.artist,
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Кнопка Устройства (Sentinel Connect)
                if (onOpenDevices != null) {
                    IconButton(onClick = onOpenDevices) {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = "Устройства воспроизведения",
                            tint = if (activeDeviceName != null) Color(0xFF1DB954) else Color(0xFFBB86FC),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Кнопка Like
                IconButton(onClick = onLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Лайк",
                        tint = if (isLiked) Color(0xFFFF3366) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Кнопка Play/Pause
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Тонкая полоска прогресса снизу
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF8A2BE2),
                trackColor = Color(0x338A2BE2),
            )
        }
    }
}
