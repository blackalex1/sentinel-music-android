package com.sentinel.music.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.components.DownloadStatusIndicator
import com.sentinel.music.presentation.components.media.TrackCover
import com.sentinel.music.presentation.components.track.TrackActionDropdown

/**
 * Строка отображения недавнего трека на главном экране.
 */
@Composable
fun RecentTrackRow(
    track: TrackDto,
    isPlayingCurrent: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    serverUrl: String,
    token: String,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylistClick: ((TrackDto) -> Unit)? = null,
    onReplaceTrackClick: ((TrackDto) -> Unit)? = null,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isPlayingCurrent) Color(0x228A2BE2) else Color.Transparent)
                .clickable { onClick() }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackCover(
                trackId = track.id,
                serverUrl = serverUrl,
                token = token,
                size = 52.dp,
                shapeRadius = 10.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    color = if (isPlayingCurrent) Color(0xFFBB86FC) else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        color = Color.Gray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.audioFormat.equals("flac", ignoreCase = true)) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "FLAC",
                            fontSize = 10.sp,
                            color = Color(0xFF1DB954),
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

            // Кнопка Лайка
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Лайк",
                    tint = if (track.isLiked) Color(0xFFFF3366) else Color.Gray,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Контекстное меню действий (Добавить в плейлист / Заменить трек / Скачать в кеш)
            TrackActionDropdown(
                track = track,
                isDownloaded = isDownloaded,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onReplaceTrackClick = onReplaceTrackClick,
                onDownloadTrack = onDownloadTrack,
                onDeleteDownloadedTrack = onDeleteDownloadedTrack
            )
        }

        HorizontalDivider(color = Color(0xFF1F1F1F))
    }
}
