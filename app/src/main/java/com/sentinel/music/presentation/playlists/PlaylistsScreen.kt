package com.sentinel.music.presentation.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.sentinel.music.presentation.components.track.TrackActionDropdown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.api.CreatePlaylistRequest
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.api.PlaylistDetailsDto
import com.sentinel.music.data.api.PlaylistDto
import com.sentinel.music.data.api.TrackDto
import com.sentinel.music.presentation.components.media.TrackCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    api: MusicApiService,
    serverUrl: String,
    token: String,
    onTrackClick: (TrackDto, List<TrackDto>) -> Unit,
    currentTrack: TrackDto?,
    downloadedTrackIds: Set<Int> = emptySet(),
    downloadingTrackIds: Set<Int> = emptySet(),
    onPlayNextClick: ((TrackDto) -> Unit)? = null,
    onAddToPlaylistClick: ((TrackDto) -> Unit)? = null,
    onReplaceTrackClick: ((TrackDto) -> Unit)? = null,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDeleteFromServerClick: ((TrackDto) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var selectedPlaylistDetails by remember { mutableStateOf<PlaylistDetailsDto?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun loadPlaylists() {
        coroutineScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { api.getPlaylists() }
                playlists = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPlaylists()
    }

    fun openPlaylist(playlist: PlaylistDto) {
        isLoading = true
        coroutineScope.launch {
            try {
                val details = withContext(Dispatchers.IO) { api.getPlaylistDetails(playlist.id) }
                selectedPlaylistDetails = details
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun createNewPlaylist() {
        val title = newPlaylistTitle.trim()
        if (title.isBlank()) return

        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.createPlaylist(CreatePlaylistRequest(title = title))
                }
                newPlaylistTitle = ""
                showCreateDialog = false
                loadPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    if (selectedPlaylistDetails != null) {
        val details = selectedPlaylistDetails!!
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Кнопка возврата к списку плейлистов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylistDetails = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к плейлистам", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(details.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Треков: ${details.tracks.size}", color = Color.Gray, fontSize = 12.sp)
                }
                if (details.tracks.isNotEmpty() && onDownloadTrack != null) {
                    FilledTonalButton(
                        onClick = {
                            details.tracks.forEach { track ->
                                if (!downloadedTrackIds.contains(track.id)) {
                                    onDownloadTrack(track)
                                }
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0x331DB954))
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("В кеш", color = Color(0xFF1DB954), fontSize = 12.sp)
                    }
                }
            }

            if (details.tracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("В этом плейлисте пока нет треков", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(details.tracks, key = { it.id }) { track ->
                        val isPlayingCurrent = currentTrack?.id == track.id
                        val isDownloaded = downloadedTrackIds.contains(track.id)
                        val isDownloading = downloadingTrackIds.contains(track.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPlayingCurrent) Color(0x228A2BE2) else Color.Transparent)
                                .clickable {
                                    val trackIndex = details.tracks.indexOfFirst { it.id == track.id }
                                    val upcoming = if (trackIndex != -1 && trackIndex < details.tracks.size - 1) {
                                        details.tracks.subList(trackIndex + 1, details.tracks.size)
                                    } else emptyList()
                                    onTrackClick(track, upcoming)
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackCover(
                                trackId = track.id,
                                serverUrl = serverUrl,
                                token = token,
                                size = 46.dp,
                                shapeRadius = 8.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    track.title,
                                    color = if (isPlayingCurrent) Color(0xFFBB86FC) else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        track.artist,
                                        modifier = Modifier.weight(1f, fill = false),
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isDownloaded || isDownloading) {
                                        Spacer(Modifier.width(6.dp))
                                        com.sentinel.music.presentation.components.DownloadStatusIndicator(
                                            isDownloaded = isDownloaded,
                                            isDownloading = isDownloading,
                                            showText = true
                                        )
                                    }
                                }
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
                        HorizontalDivider(color = Color(0xFF1E1E1E))
                    }
                }
            }
        }
    } else {
        // Список плейлистов
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Мои плейлисты",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF8A2BE2))
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Создать", color = Color.White)
                }
            }

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Нет созданных плейлистов", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp)
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openPlaylist(pl) },
                            color = Color(0xFF1C1A22)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2E263A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = Color(0xFFBB86FC),
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.title,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${pl.trackCount} треков",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }

                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новый плейлист", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistTitle,
                    onValueChange = { newPlaylistTitle = it },
                    placeholder = { Text("Название плейлиста") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { createNewPlaylist() }) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
