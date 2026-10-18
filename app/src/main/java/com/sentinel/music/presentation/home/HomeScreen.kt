package com.sentinel.music.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.home.components.MoodCollectionsSection
import com.sentinel.music.presentation.home.components.RecentTrackRow
import com.sentinel.music.presentation.home.models.DEFAULT_HOME_MOODS
import com.sentinel.music.presentation.home.models.HomeMoodItem

// Экспорт для обратной совместимости
typealias HomeMoodItem = com.sentinel.music.presentation.home.models.HomeMoodItem
val HOME_MOODS = DEFAULT_HOME_MOODS

@Composable
fun HomeScreen(
    recentTracks: List<TrackDto>,
    allTracks: List<TrackDto> = emptyList(),
    currentTrack: TrackDto?,
    serverUrl: String,
    token: String,
    downloadedTrackIds: Set<Int> = emptySet(),
    downloadingTrackIds: Set<Int> = emptySet(),
    onTrackClick: (TrackDto, List<TrackDto>) -> Unit,
    onStartStreamClick: () -> Unit = {},
    onMoodClick: (String) -> Unit = {},
    onPlayMoodMix: (String) -> Unit = {},
    onLikeTrack: (TrackDto) -> Unit,
    onAddToPlaylistClick: ((TrackDto) -> Unit)? = null,
    onReplaceTrackClick: ((TrackDto) -> Unit)? = null,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
    ) {
        // Секция коллекций настроений
        item {
            MoodCollectionsSection(
                allTracks = allTracks,
                onMoodClick = onMoodClick,
                onPlayMoodMix = onPlayMoodMix
            )
        }

        // Заголовок "Недавно добавленные"
        item {
            Text(
                text = "Недавно добавленные треки",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            )
        }

        if (recentTracks.isEmpty()) {
            item {
                Text(
                    text = "Загрузка фонотеки...",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(
                items = recentTracks,
                key = { it.id },
                contentType = { "track" }
            ) { track ->
                val trackIndex = recentTracks.indexOfFirst { it.id == track.id }
                val upcoming = if (trackIndex != -1 && trackIndex < recentTracks.size - 1) {
                    recentTracks.subList(trackIndex + 1, recentTracks.size)
                } else emptyList()

                RecentTrackRow(
                    track = track,
                    isPlayingCurrent = currentTrack?.id == track.id,
                    isDownloaded = downloadedTrackIds.contains(track.id),
                    isDownloading = downloadingTrackIds.contains(track.id),
                    serverUrl = serverUrl,
                    token = token,
                    onClick = { onTrackClick(track, upcoming) },
                    onLikeClick = { onLikeTrack(track) },
                    onAddToPlaylistClick = onAddToPlaylistClick,
                    onReplaceTrackClick = onReplaceTrackClick,
                    onDownloadTrack = onDownloadTrack,
                    onDeleteDownloadedTrack = onDeleteDownloadedTrack
                )
            }
        }
    }
}
