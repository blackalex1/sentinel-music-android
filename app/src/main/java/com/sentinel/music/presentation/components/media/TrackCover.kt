package com.sentinel.music.presentation.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

@Composable
fun TrackCover(
    trackId: Int?,
    serverUrl: String,
    token: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shapeRadius: Dp = 8.dp,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val coverUrl = remember(trackId, serverUrl, token, size) {
        if (trackId == null || trackId <= 0 || serverUrl.isBlank()) {
            null
        } else {
            val clean = serverUrl.trim().removeSuffix("/")
            val base = "$clean/api/covers/$trackId"
            val queryParams = mutableListOf<String>()
            if (token.isNotBlank()) queryParams.add("token=$token")
            if (size <= 96.dp) queryParams.add("size=160")
            if (queryParams.isEmpty()) base else "$base?${queryParams.joinToString("&")}"
        }
    }

    val pxSize = remember(density, size) {
        with(density) { size.roundToPx() }
    }

    val placeholderShape = remember(shapeRadius) { RoundedCornerShape(shapeRadius) }

    Box(
        modifier = modifier
            .size(size)
            .clip(placeholderShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A2A2A), Color(0xFF1E1E1E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color(0xFF555555),
            modifier = Modifier.size(size * 0.45f)
        )

        if (coverUrl != null) {
            val imageRequest = remember(coverUrl, pxSize) {
                ImageRequest.Builder(context)
                    .data(coverUrl)
                    .crossfade(false)
                    .size(pxSize, pxSize)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowHardware(true)
                    .memoryCacheKey(coverUrl)
                    .diskCacheKey(coverUrl)
                    .build()
            }

            AsyncImage(
                model = imageRequest,
                contentDescription = "Обложка трека",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}
