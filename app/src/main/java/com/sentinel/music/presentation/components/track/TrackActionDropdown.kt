package com.sentinel.music.presentation.components.track

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sentinel.music.data.model.TrackDto

/**
 * Переиспользуемое контекстное меню действий с треком (3 точки ⋮).
 * Содержит:
 * - Играть следующим
 * - Добавить в плейлист
 * - Не тот трек / Заменить (поиск альтернативных релизов/качества)
 * - Скачать в кеш / Удалить из кеша
 * - Удалить с сервера
 */
@Composable
fun TrackActionDropdown(
    track: TrackDto,
    isDownloaded: Boolean,
    onPlayNextClick: ((TrackDto) -> Unit)? = null,
    onAddToPlaylistClick: ((TrackDto) -> Unit)? = null,
    onReplaceTrackClick: ((TrackDto) -> Unit)? = null,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDeleteFromServerClick: ((TrackDto) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val hasMenu = onPlayNextClick != null ||
            onAddToPlaylistClick != null ||
            onReplaceTrackClick != null ||
            onDownloadTrack != null ||
            onDeleteDownloadedTrack != null ||
            onDeleteFromServerClick != null

    if (!hasMenu) return

    Box(modifier = modifier) {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Опции трека",
                tint = Color.Gray
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.background(Color(0xFF24202C))
        ) {
            if (onPlayNextClick != null) {
                DropdownMenuItem(
                    text = { Text("Играть следующим", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = Color(0xFFBB86FC)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onPlayNextClick(track)
                    }
                )
            }

            if (onAddToPlaylistClick != null) {
                DropdownMenuItem(
                    text = { Text("Добавить в плейлист", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = Color.White
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylistClick(track)
                    }
                )
            }

            if (onReplaceTrackClick != null) {
                DropdownMenuItem(
                    text = { Text("Не тот трек / Заменить", color = Color.White) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FindReplace,
                            contentDescription = null,
                            tint = Color(0xFFD8B4FE)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onReplaceTrackClick(track)
                    }
                )
            }

            if (isDownloaded) {
                if (onDeleteDownloadedTrack != null) {
                    DropdownMenuItem(
                        text = { Text("Удалить из кеша", color = Color(0xFFFF8A80)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFFF8A80)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteDownloadedTrack(track)
                        }
                    )
                }
            } else {
                if (onDownloadTrack != null) {
                    DropdownMenuItem(
                        text = { Text("Скачать в кеш", color = Color.White) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = null,
                                tint = Color(0xFF1DB954)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDownloadTrack(track)
                        }
                    )
                }
            }

            if (onDeleteFromServerClick != null) {
                DropdownMenuItem(
                    text = { Text("Удалить с сервера", color = Color(0xFFFF5252)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFFF5252)
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDeleteFromServerClick(track)
                    }
                )
            }
        }
    }
}
