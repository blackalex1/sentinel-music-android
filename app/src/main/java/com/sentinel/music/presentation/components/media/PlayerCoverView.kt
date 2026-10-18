package com.sentinel.music.presentation.components.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import kotlin.math.roundToInt

@Composable
fun PlayerCoverView(
    track: TrackDto,
    serverUrl: String,
    token: String,
    onOpenLyrics: () -> Unit,
    onNext: (() -> Unit)? = null,
    onPrev: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CoverDrag"
    )

    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
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
                        dragOffset = (dragOffset + dragAmount).coerceIn(-300f, 300f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val coverSize = if (maxHeight.isSpecified && maxHeight > 0.dp) {
            (maxHeight * 0.46f).coerceIn(140.dp, 205.dp)
        } else {
            185.dp
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset { IntOffset(animatedOffset.roundToInt(), 0) }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenLyrics() }
            ) {
                TrackCover(
                    trackId = track.id,
                    serverUrl = serverUrl,
                    token = token,
                    size = coverSize,
                    shapeRadius = 18.dp
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = track.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = track.artist,
                fontSize = 15.sp,
                color = Color.LightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (!track.album.isNullOrBlank()) {
                Text(
                    text = track.album,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

        // Кнопка быстрого открытия текста караоке
        Surface(
            onClick = onOpenLyrics,
            shape = CircleShape,
            color = Color(0x228A2BE2),
            border = BorderStroke(1.dp, Color(0x558A2BE2))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lyrics,
                    contentDescription = null,
                    tint = Color(0xFFD8B4FE),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Текст песни и караоке",
                    color = Color(0xFFE9D5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
}
