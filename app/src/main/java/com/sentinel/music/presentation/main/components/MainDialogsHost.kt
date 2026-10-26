package com.sentinel.music.presentation.main.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.components.media.DevicesDialog
import com.sentinel.music.presentation.components.media.ExpandedPlayerModal
import com.sentinel.music.presentation.components.media.QueueBottomSheet
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.MainViewModel
import com.sentinel.music.presentation.playlists.AddToPlaylistDialog
import com.sentinel.music.presentation.tracks.DeleteTrackDialog
import com.sentinel.music.presentation.tracks.ReplaceTrackDialog

@Composable
fun MainDialogsHost(
    state: MainUiState,
    viewModel: MainViewModel,
    serverUrl: String,
    token: String,
    api: MusicApiService,
    isExpandedPlayerOpen: Boolean,
    initialShowLyrics: Boolean,
    onDismissExpandedPlayer: () -> Unit,
    isQueueSheetOpen: Boolean,
    onDismissQueueSheet: () -> Unit,
    trackForPlaylist: TrackDto?,
    onDismissPlaylistDialog: () -> Unit,
    trackForReplace: TrackDto?,
    onDismissReplaceDialog: () -> Unit,
    trackForDelete: TrackDto?,
    onDismissDeleteDialog: () -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onReplaceTrack: () -> Unit
) {
    val context = LocalContext.current

    // Полноэкранный плеер с караоке (плавно выезжает поверх всего экрана)
    AnimatedVisibility(
        visible = isExpandedPlayerOpen && state.currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.zIndex(10f)
    ) {
        ExpandedPlayerModal(
            track = state.currentTrack,
            isPlaying = state.isPlaying,
            isLiked = state.isLiked,
            isDisliked = state.isDisliked,
            currentProgressMs = state.currentProgressMs,
            durationMs = state.durationMs,
            lyrics = state.lyrics,
            serverUrl = serverUrl,
            token = token,
            initialShowLyrics = initialShowLyrics,
            repeatMode = state.repeatMode,
            isShuffleEnabled = state.isShuffleEnabled,
            isLoudnessNormalizationEnabled = state.isLoudnessNormalizationEnabled,
            loudnessTargetLufs = state.loudnessTargetLufs,
            currentLimiterMode = state.currentLimiterMode,
            equalizerState = state.equalizerState,
            isDownloaded = state.currentTrack?.let { state.downloadedTrackIds.contains(it.id) } ?: false,
            isDownloading = state.currentTrack?.let { state.downloadingTrackIds.contains(it.id) } ?: false,
            onToggleDownload = {
                state.currentTrack?.let { track ->
                    if (state.downloadedTrackIds.contains(track.id)) {
                        viewModel.deleteDownloadedTrack(track)
                    } else {
                        viewModel.downloadTrack(track)
                    }
                }
            },
            onDismiss = onDismissExpandedPlayer,
            onPlayPause = { viewModel.playPause() },
            onPrev = { viewModel.playPrev() },
            onNext = { viewModel.playNext() },
            onLike = { viewModel.toggleLike() },
            onDislike = { viewModel.toggleDislike() },
            onToggleRepeat = { viewModel.toggleRepeatMode() },
            onToggleShuffle = { viewModel.toggleShuffleMode() },
            onToggleLoudnessNormalization = { viewModel.setLoudnessNormalization(it) },
            onSelectLoudnessTargetLufs = { viewModel.setLoudnessTarget(it) },
            onSelectLimiterMode = { viewModel.setLimiterMode(it) },
            onToggleEqualizer = { viewModel.setEqualizerEnabled(it) },
            onSetBandLevel = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
            onSelectEqualizerPreset = { viewModel.setEqualizerPreset(it) },
            onResetEqualizer = { viewModel.resetEqualizer() },
            onSetBassBoost = { viewModel.setBassBoost(it) },
            onSetVirtualizer = { viewModel.setVirtualizer(it) },
            onSeek = { seekPosMs -> viewModel.seekTo(seekPosMs) },
            onAddToPlaylistClick = onAddToPlaylistClick,
            onOpenQueue = onOpenQueue,
            onReplaceTrack = onReplaceTrack,
            activeDeviceName = state.activeDevice?.name,
            onOpenDevices = { viewModel.openDevicesDialog() }
        )
    }

    // Диалог выбора устройства воспроизведения (Sentinel Connect)
    if (state.isDevicesDialogOpen) {
        DevicesDialog(
            availableDevices = state.availableDevices,
            activeDevice = state.activeDevice,
            onSelectDevice = { viewModel.selectPlaybackDevice(it) },
            onDismiss = { viewModel.closeDevicesDialog() }
        )
    }

    // Шторка очереди воспроизведения (Up Next)
    if (isQueueSheetOpen) {
        QueueBottomSheet(
            queue = state.queue,
            currentTrack = state.currentTrack,
            serverUrl = serverUrl,
            token = token,
            downloadedTrackIds = state.downloadedTrackIds,
            onTrackClick = { clickedTrack ->
                val idx = state.queue.indexOfFirst { it.id == clickedTrack.id }
                val remaining = if (idx != -1 && idx < state.queue.size - 1) {
                    state.queue.subList(idx + 1, state.queue.size)
                } else emptyList()
                viewModel.playTrack(clickedTrack, remaining)
            },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onDismiss = onDismissQueueSheet
        )
    }

    // Диалог добавления трека в плейлист
    if (trackForPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForPlaylist,
            api = api,
            onDismiss = onDismissPlaylistDialog,
            onSuccess = onDismissPlaylistDialog
        )
    }

    // Диалог "Не тот трек / Заменить"
    if (trackForReplace != null) {
        ReplaceTrackDialog(
            track = trackForReplace,
            api = api,
            serverUrl = serverUrl,
            token = token,
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            onPreviewCandidate = { candidate ->
                viewModel.playCandidatePreview(candidate)
            },
            onDismiss = onDismissReplaceDialog,
            onSuccess = { updatedTrack ->
                viewModel.updateReplacedTrack(updatedTrack)
                onDismissReplaceDialog()
            }
        )
    }

    // Диалог подтверждения удаления трека с сервера
    if (trackForDelete != null) {
        DeleteTrackDialog(
            track = trackForDelete,
            serverUrl = serverUrl,
            token = token,
            onDismiss = onDismissDeleteDialog,
            onConfirmDelete = { deleteFile ->
                val target = trackForDelete
                onDismissDeleteDialog()
                viewModel.deleteTrackFromServer(target, deleteFile) { success, msg ->
                    val text = msg ?: if (success) "Трек удален с сервера" else "Ошибка при удалении"
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
