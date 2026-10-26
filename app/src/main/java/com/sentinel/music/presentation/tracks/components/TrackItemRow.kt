package com.sentinel.music.presentation.tracks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.components.DownloadStatusIndicator
import com.sentinel.music.presentation.components.media.TrackCover
import com.sentinel.music.presentation.components.track.TrackActionDropdown

@Composable
fun TrackItemRow(
    track: TrackDto,
    isPlayingCurrent: Boolean,
    isDownloaded: Boolean,
    serverUrl: String,
    token: String,
    isDownloading: Boolean = false,
    onPlayNextClick: ((TrackDto) -> Unit)? = null,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDeleteFromServerClick: ((TrackDto) -> Unit)? = null,
    onTrackClick: () -> Unit,
    onLikeTrack: (TrackDto) -> Unit,
    onAddToPlaylistClick: (TrackDto) -> Unit,
    onReplaceTrackClick: (TrackDto) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlayingCurrent) Color(0x228A2BE2) else Color.Transparent)
            .clickable(onClick = onTrackClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackCover(
            trackId = track.id,
            serverUrl = serverUrl,
            token = token,
            size = 48.dp,
            shapeRadius = 8.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.safeTitle,
                color = if (isPlayingCurrent) Color(0xFFBB86FC) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.safeArtist,
                    modifier = Modifier.weight(1f, fill = false),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val format = track.safeAudioFormat
                if (format.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = format.uppercase(),
                        fontSize = 10.sp,
                        color = if (format.equals("flac", ignoreCase = true)) Color(0xFF1DB954) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (isDownloaded || isDownloading) {
                    Spacer(Modifier.width(6.dp))
                    DownloadStatusIndicator(
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        showText = true
                    )
                }
            }
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLikeTrack(track)
            }
        ) {
            Icon(
                imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Лайк",
                tint = if (track.isLiked) Color(0xFFFF3366) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        TrackActionDropdown(
            track = track,
            isDownloaded = isDownloaded,
            onPlayNextClick = onPlayNextClick,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onReplaceTrackClick = onReplaceTrackClick,
            onDownloadTrack = onDownloadTrack,
            onDeleteDownloadedTrack = onDeleteDownloadedTrack,
            onDeleteFromServerClick = onDeleteFromServerClick
        )
    }
}
