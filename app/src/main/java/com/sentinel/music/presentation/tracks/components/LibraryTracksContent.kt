package com.sentinel.music.presentation.tracks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.main.TrackFilterMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTracksContent(
    tracks: List<TrackDto>,
    filteredTracks: List<TrackDto>,
    currentTrack: TrackDto?,
    serverUrl: String,
    token: String,
    downloadedTrackIds: Set<Int>,
    downloadingTrackIds: Set<Int>,
    selectedFilter: TrackFilterMode,
    onFilterSelected: (TrackFilterMode) -> Unit,
    searchQuery: String,
    isSearchVisible: Boolean,
    isSearching: Boolean,
    isLoadingTracks: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onResetSearch: () -> Unit,
    onTrackClick: (TrackDto, List<TrackDto>) -> Unit,
    onLikeTrack: (TrackDto) -> Unit,
    onPlayNextClick: ((TrackDto) -> Unit)? = null,
    onAddToPlaylistClick: (TrackDto) -> Unit = {},
    onReplaceTrackClick: (TrackDto) -> Unit = {},
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDeleteFromServerClick: ((TrackDto) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxSize()) {
        // Чипы фильтрации (Все, Только FLAC, Любимые, Оффлайн)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackFilterMode.entries.forEach { mode ->
                val isSelected = selectedFilter == mode
                val chipAccentColor = when (mode) {
                    TrackFilterMode.MOOD_ENERGY -> Color(0xFFFF512F)
                    TrackFilterMode.MOOD_CHILL -> Color(0xFF00B4DB)
                    TrackFilterMode.MOOD_HAPPY -> Color(0xFFFFB300)
                    TrackFilterMode.MOOD_SAD -> Color(0xFF5C6BC0)
                    TrackFilterMode.FLAC_ONLY -> Color(0xFF1DB954)
                    TrackFilterMode.LIKED -> Color(0xFFFF3366)
                    else -> Color(0xFF8A2BE2)
                }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onFilterSelected(mode)
                    },
                    label = {
                        Text(
                            text = mode.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipAccentColor,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF23202A),
                        labelColor = Color.LightGray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) chipAccentColor else Color(0xFF383444),
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        // Строка статуса и количества
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        if (isSearching) "Поиск по всей фонотеке..." else "Найдено на сервере: ${filteredTracks.size}"
                    } else if (selectedFilter != TrackFilterMode.ALL) {
                        "${selectedFilter.label}: ${filteredTracks.size} из ${tracks.size}"
                    } else {
                        "Всего треков: ${filteredTracks.size}"
                    },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                if (searchQuery.isNotBlank() && !isSearchVisible) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "• Сбросить",
                        fontSize = 12.sp,
                        color = Color(0xFFBB86FC),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onResetSearch()
                        }
                    )
                }
            }

            if (isLoadingTracks) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF8A2BE2)
                )
            } else {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefresh()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Обновить медиатеку",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Список треков или пустое состояние с поддержкой свайпа для обновления (Pull-to-Refresh)
        PullToRefreshBox(
            isRefreshing = isLoadingTracks,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredTracks.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Filled.SearchOff else Icons.Filled.MusicOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Список треков пуст",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Попробуйте перейти на вкладку «Новые треки» для поиска в сети"
                            } else {
                                "В вашей коллекции пока нет добавленных треков"
                            },
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp)
                ) {
                    items(
                        items = filteredTracks,
                        key = { it.id },
                        contentType = { "track_row" }
                    ) { track ->
                        val isDownloaded = downloadedTrackIds.contains(track.id)
                        val isDownloading = downloadingTrackIds.contains(track.id)

                        TrackItemRow(
                            track = track,
                            isPlayingCurrent = currentTrack?.id == track.id,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            serverUrl = serverUrl,
                            token = token,
                            onPlayNextClick = onPlayNextClick,
                            onDownloadTrack = onDownloadTrack,
                            onDeleteDownloadedTrack = onDeleteDownloadedTrack,
                            onDeleteFromServerClick = onDeleteFromServerClick,
                            onTrackClick = {
                                val trackIndex = filteredTracks.indexOfFirst { it.id == track.id }
                                val upcoming = if (trackIndex != -1 && trackIndex < filteredTracks.size - 1) {
                                    filteredTracks.subList(trackIndex + 1, filteredTracks.size)
                                } else emptyList()
                                onTrackClick(track, upcoming)
                            },
                            onLikeTrack = onLikeTrack,
                            onAddToPlaylistClick = onAddToPlaylistClick,
                            onReplaceTrackClick = onReplaceTrackClick
                        )
                        HorizontalDivider(color = Color(0xFF1E1E1E))
                    }
                }
            }
        }
    }
}
