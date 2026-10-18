package com.sentinel.music.presentation.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.api.AddTrackToPlaylistRequest
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.api.PlaylistDto
import com.sentinel.music.data.api.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddToPlaylistDialog(
    track: TrackDto,
    api: MusicApiService,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<PlaylistDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val list = withContext(Dispatchers.IO) { api.getPlaylists() }
            playlists = list
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    fun add(playlist: PlaylistDto) {
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.addTrackToPlaylist(playlist.id, AddTrackToPlaylistRequest(trackId = track.id))
                }
                onSuccess()
            } catch (e: Exception) {
                e.printStackTrace()
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Добавить в плейлист", fontWeight = FontWeight.Bold)
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF8A2BE2))
                }
            } else if (playlists.isEmpty()) {
                Text("У вас пока нет плейлистов. Создайте плейлист на вкладке «Плейлисты».", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(playlists) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { add(pl) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color(0xFFBB86FC))
                            Spacer(Modifier.width(12.dp))
                            Text(pl.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(color = Color(0xFF222222))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
