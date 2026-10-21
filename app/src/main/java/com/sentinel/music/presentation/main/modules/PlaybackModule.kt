package com.sentinel.music.presentation.main.modules

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.RemoteDeviceDto
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.remote.RemoteController
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.PlaybackRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль управления воспроизведением, очередью, ползунком прогресса и MediaController
 */
class PlaybackModule(
    private val scope: CoroutineScope,
    private val api: MusicApiService,
    private val sessionManager: SessionManager,
    private val offlineManager: OfflineManager,
    private val lyricsRepository: LyricsRepository,
    private val remoteController: RemoteController,
    private val dspModule: DspModule,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val onSyncRemoteState: (RemoteDeviceDto?) -> Unit,
    private val onStreamNext: (String?, Float?) -> Unit
) {
    var mediaController: MediaController? = null
        private set

    private var progressJob: Job? = null

    private data class PendingPlayback(
        val track: TrackDto,
        val upcomingList: List<TrackDto>?,
        val startPositionMs: Long,
        val autoPlay: Boolean
    )

    private var pendingPlayback: PendingPlayback? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                val controller = mediaController
                if (controller == null || controller.currentMediaItem == null) return
                if (!controller.playWhenReady) return // Не переключать трек, если плеер на паузе или восстанавливает состояние
                if (controller.duration > 0 && controller.currentPosition < controller.duration - 2000L) {
                    return
                }

                if (uiStateFlow.value.repeatMode == PlaybackRepeatMode.ONE) {
                    mediaController?.seekTo(0)
                    mediaController?.play()
                } else {
                    playNext()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (uiStateFlow.value.activeDevice != null) {
                // Если сейчас активно удаленное воспроизведение на ПК, изменения локального плеера игнорируются
                return
            }
            uiStateFlow.update { it.copy(isPlaying = isPlaying) }
            val current = uiStateFlow.value
            if (current.activeDevice == null && current.currentTrack != null) {
                remoteController.broadcastLocalState(
                    isPlaying = isPlaying,
                    currentTrack = current.currentTrack,
                    positionMs = mediaController?.currentPosition ?: current.currentProgressMs,
                    durationMs = current.durationMs,
                    queue = current.queue,
                    isShuffle = current.isShuffleEnabled,
                    repeatMode = current.repeatMode.name.lowercase(),
                    force = true
                )
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mode = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
                Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
                else -> PlaybackRepeatMode.OFF
            }
            uiStateFlow.update { it.copy(repeatMode = mode) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            uiStateFlow.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId?.toIntOrNull() ?: return
            val current = uiStateFlow.value
            if (current.currentTrack?.id == trackId) return

            val nextTrack = current.queue.find { it.id == trackId }
                ?: current.tracks.find { it.id == trackId }
                ?: current.recentTracks.find { it.id == trackId }
                ?: return

            val updatedQueue = current.queue.filterNot { it.id == trackId }
            val nextDur = (nextTrack.duration * 1000).toLong().coerceAtLeast(1L)
            uiStateFlow.update {
                it.copy(
                    currentTrack = nextTrack,
                    durationMs = nextDur,
                    isLiked = nextTrack.isLiked,
                    isDisliked = nextTrack.isDisliked,
                    queue = updatedQueue,
                    lyrics = emptyList()
                )
            }

            if (current.activeDevice == null) {
                remoteController.broadcastLocalState(
                    isPlaying = mediaController?.isPlaying == true,
                    currentTrack = nextTrack,
                    positionMs = 0L,
                    durationMs = nextDur,
                    queue = updatedQueue,
                    isShuffle = current.isShuffleEnabled,
                    repeatMode = current.repeatMode.name.lowercase(),
                    force = true
                )
            }

            dspModule.applyVolumeGain(nextTrack.gainDb)

            val token = sessionManager.token ?: ""
            if (uiStateFlow.value.isDownloadMode) {
                scope.launch(Dispatchers.IO) {
                    offlineManager.downloadTrack(nextTrack, token)
                }
            }
            if (uiStateFlow.value.preloadNextTrack && updatedQueue.isNotEmpty()) {
                val nextInLine = updatedQueue.first()
                if (uiStateFlow.value.isDownloadMode && !offlineManager.isTrackDownloaded(nextInLine.id, nextInLine.audioFormat)) {
                    scope.launch(Dispatchers.IO) {
                        offlineManager.downloadTrack(nextInLine, token)
                    }
                }
            }

            scope.launch {
                val fetchedLyrics = lyricsRepository.getLyrics(nextTrack.id)
                if (uiStateFlow.value.currentTrack?.id == nextTrack.id) {
                    uiStateFlow.update { it.copy(lyrics = fetchedLyrics) }
                }
            }
        }
    }

    private fun applyPlaybackToController(
        controller: MediaController,
        mediaItems: List<MediaItem>,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        try {
            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(mediaItems, 0, startPositionMs)
            controller.repeatMode = when (uiStateFlow.value.repeatMode) {
                PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
                PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
                PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            }
            controller.shuffleModeEnabled = uiStateFlow.value.isShuffleEnabled
            controller.prepare()
            if (autoPlay) {
                controller.play()
            } else {
                controller.pause()
            }
        } catch (e: Exception) {
            android.util.Log.w("PlaybackModule", "Ошибка установки медиа-элементов: ${e.message}")
        }
    }

    fun bindController(controller: MediaController?) {
        if (mediaController == controller) return
        mediaController?.removeListener(playerListener)
        mediaController = controller
        mediaController?.addListener(playerListener)
        if (controller != null) {
            val pending = pendingPlayback
            if (pending != null) {
                val token = sessionManager.token ?: ""
                val serverUrl = sessionManager.serverUrl
                val allTracks = listOf(pending.track) + (pending.upcomingList ?: emptyList())
                val mediaItems = allTracks.map { buildMediaItem(it, token, serverUrl) }
                applyPlaybackToController(controller, mediaItems, pending.startPositionMs, pending.autoPlay)
                pendingPlayback = null
            } else {
                val state = uiStateFlow.value
                val curTrack = state.currentTrack
                if (curTrack != null && state.activeDevice == null) {
                    val currentExoId = controller.currentMediaItem?.mediaId?.toIntOrNull()
                    if (currentExoId != curTrack.id) {
                        val token = sessionManager.token ?: ""
                        val serverUrl = sessionManager.serverUrl
                        val allTracks = listOf(curTrack) + state.queue
                        val mediaItems = allTracks.map { buildMediaItem(it, token, serverUrl) }
                        applyPlaybackToController(controller, mediaItems, state.currentProgressMs, state.isPlaying)
                    } else {
                        val mode = when (controller.repeatMode) {
                            Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
                            Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
                            else -> PlaybackRepeatMode.OFF
                        }
                        val pos = controller.currentPosition
                        uiStateFlow.update {
                            it.copy(
                                isPlaying = controller.isPlaying,
                                currentProgressMs = if (controller.isPlaying || pos > 0L) pos else it.currentProgressMs,
                                durationMs = if (controller.duration > 0) controller.duration else it.durationMs,
                                repeatMode = mode,
                                isShuffleEnabled = controller.shuffleModeEnabled
                            )
                        }
                    }
                }
            }
            onSyncRemoteState(uiStateFlow.value.activeDevice)
        }
    }

    fun buildMediaItem(track: TrackDto, token: String, serverUrl: String): MediaItem {
        val coverUrl = "$serverUrl/api/covers/${track.id}?token=$token"
        val extras = Bundle().apply {
            putBoolean("IS_LIKED", track.isLiked)
            putBoolean("IS_DISLIKED", track.isDisliked)
        }
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album ?: "Sentinel Music")
            .setArtworkUri(Uri.parse(coverUrl))
            .setDisplayTitle(track.title)
            .setExtras(extras)
            .build()

        val playUri = offlineManager.getPlaybackUri(track.id, track.audioFormat, token)
        return MediaItem.Builder()
            .setUri(playUri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    fun prepareLocalPlayback(
        track: TrackDto,
        upcomingList: List<TrackDto>? = null,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = false
    ) {
        val token = sessionManager.token ?: ""
        val serverUrl = sessionManager.serverUrl

        // Нормализация громкости (EBU R128 gain_db)
        dspModule.applyVolumeGain(track.gainDb)

        val queue = upcomingList ?: run {
            val currentTracks = uiStateFlow.value.tracks
            val idx = currentTracks.indexOfFirst { it.id == track.id }
            if (idx != -1 && idx < currentTracks.size - 1) {
                currentTracks.subList(idx + 1, minOf(idx + 30, currentTracks.size))
            } else {
                uiStateFlow.value.queue
            }
        }

        val allTracks = listOf(track) + queue
        val mediaItems = allTracks.map { buildMediaItem(it, token, serverUrl) }

        val controller = mediaController
        if (controller != null) {
            applyPlaybackToController(controller, mediaItems, startPositionMs, autoPlay)
            pendingPlayback = null
        } else {
            pendingPlayback = PendingPlayback(track, queue, startPositionMs, autoPlay)
        }

        val dur = (track.duration * 1000).toLong().coerceAtLeast(1L)
        uiStateFlow.update {
            it.copy(
                activeDevice = null,
                currentTrack = track,
                isPlaying = autoPlay,
                currentProgressMs = startPositionMs,
                durationMs = dur,
                isLiked = track.isLiked,
                isDisliked = track.isDisliked,
                queue = queue,
                isStreamActive = false,
                lyrics = emptyList()
            )
        }

        remoteController.broadcastLocalState(
            isPlaying = autoPlay,
            currentTrack = track,
            positionMs = startPositionMs,
            durationMs = dur,
            queue = queue,
            isShuffle = uiStateFlow.value.isShuffleEnabled,
            repeatMode = uiStateFlow.value.repeatMode.name.lowercase(),
            force = true
        )

        if (uiStateFlow.value.isDownloadMode) {
            scope.launch(Dispatchers.IO) {
                offlineManager.downloadTrack(track, token)
            }
        }

        scope.launch {
            val fetchedLyrics = lyricsRepository.getLyrics(track.id)
            if (uiStateFlow.value.currentTrack?.id == track.id) {
                uiStateFlow.update { it.copy(lyrics = fetchedLyrics) }
            }
        }
    }

    fun playTrack(track: TrackDto, upcomingList: List<TrackDto>? = null, isFromStream: Boolean = false) {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            val fullQueue = if (upcomingList != null) {
                if (upcomingList.any { it.id == track.id }) upcomingList else listOf(track) + upcomingList
            } else listOf(track)
            remoteController.playTrack(track, queue = fullQueue, autoPlay = true)
            val updatedDev = active.copy(
                currentTrack = track,
                isPlaying = true,
                positionMs = 0L,
                durationMs = (track.duration * 1000).toLong().coerceAtLeast(1L),
                queue = fullQueue
            )
            uiStateFlow.update {
                it.copy(
                    activeDevice = updatedDev,
                    currentTrack = track,
                    isPlaying = true,
                    currentProgressMs = 0L,
                    durationMs = (track.duration * 1000).toLong().coerceAtLeast(1L),
                    queue = fullQueue,
                    isLiked = track.isLiked,
                    isDisliked = track.isDisliked,
                    isStreamActive = isFromStream
                )
            }
            onSyncRemoteState(updatedDev)
            scope.launch {
                val fetchedLyrics = lyricsRepository.getLyrics(track.id)
                if (uiStateFlow.value.currentTrack?.id == track.id) {
                    uiStateFlow.update { it.copy(lyrics = fetchedLyrics) }
                }
            }
            return
        }

        prepareLocalPlayback(
            track = track,
            upcomingList = if (isFromStream) emptyList() else upcomingList,
            startPositionMs = 0L,
            autoPlay = true
        )
        if (isFromStream) {
            uiStateFlow.update { it.copy(isStreamActive = true) }
        }
    }

    fun play() {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.play()
            val updatedDev = active.copy(isPlaying = true)
            uiStateFlow.update { it.copy(isPlaying = true, activeDevice = updatedDev) }
            onSyncRemoteState(updatedDev)
            return
        }
        val controller = mediaController
        if (controller != null) {
            val state = uiStateFlow.value
            if (controller.currentMediaItem == null && state.currentTrack != null) {
                val token = sessionManager.token ?: ""
                val serverUrl = sessionManager.serverUrl
                val allTracks = listOf(state.currentTrack) + state.queue
                val mediaItems = allTracks.map { buildMediaItem(it, token, serverUrl) }
                applyPlaybackToController(controller, mediaItems, state.currentProgressMs, autoPlay = true)
                return
            }
            controller.play()
        }
    }

    fun pause() {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.pause()
            val updatedDev = active.copy(isPlaying = false)
            uiStateFlow.update { it.copy(isPlaying = false, activeDevice = updatedDev) }
            onSyncRemoteState(updatedDev)
            return
        }
        mediaController?.pause()
    }

    fun playPause() {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.togglePlay()
            val nextPlaying = !uiStateFlow.value.isPlaying
            val updatedDev = active.copy(isPlaying = nextPlaying)
            uiStateFlow.update { it.copy(isPlaying = nextPlaying, activeDevice = updatedDev) }
            onSyncRemoteState(updatedDev)
            return
        }
        val controller = mediaController
        if (controller != null) {
            val state = uiStateFlow.value
            if (controller.currentMediaItem == null && state.currentTrack != null) {
                val token = sessionManager.token ?: ""
                val serverUrl = sessionManager.serverUrl
                val allTracks = listOf(state.currentTrack) + state.queue
                val mediaItems = allTracks.map { buildMediaItem(it, token, serverUrl) }
                applyPlaybackToController(controller, mediaItems, state.currentProgressMs, autoPlay = true)
                return
            }
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    fun playNext(customMood: String? = null, customRatio: Float? = null) {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.next()
            return
        }
        val state = uiStateFlow.value
        val controller = mediaController
        if (state.isStreamActive) {
            onStreamNext(customMood, customRatio)
        } else {
            if (controller != null && controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                return
            }

            if (state.queue.isNotEmpty()) {
                val nextTrack = if (state.isShuffleEnabled) {
                    state.queue.random()
                } else {
                    state.queue.first()
                }
                val remaining = state.queue.filterNot { it.id == nextTrack.id }
                playTrack(nextTrack, upcomingList = remaining, isFromStream = false)
                return
            }

            val list = state.tracks
            if (list.isNotEmpty()) {
                if (state.isShuffleEnabled) {
                    val candidateList = list.filterNot { it.id == state.currentTrack?.id }
                    val nextTrack = if (candidateList.isNotEmpty()) candidateList.random() else list.random()
                    playTrack(nextTrack, isFromStream = false)
                } else {
                    val idx = list.indexOfFirst { it.id == state.currentTrack?.id }
                    if (idx != -1 && idx < list.size - 1) {
                        playTrack(list[idx + 1], isFromStream = false)
                    } else if (state.repeatMode == PlaybackRepeatMode.ALL) {
                        playTrack(list[0], isFromStream = false)
                    }
                }
            }
        }
    }

    fun playPrev() {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.prev()
            return
        }
        val controller = mediaController
        if (controller != null && controller.currentPosition > 3000L) {
            controller.seekTo(0)
            return
        }
        if (controller != null && controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            return
        }
        val list = uiStateFlow.value.tracks
        val idx = list.indexOfFirst { it.id == uiStateFlow.value.currentTrack?.id }
        if (idx > 0) {
            playTrack(list[idx - 1])
        }
    }

    fun seekTo(positionMs: Long) {
        val active = uiStateFlow.value.activeDevice
        if (active != null) {
            remoteController.seekTo(positionMs)
            val updatedDev = active.copy(positionMs = positionMs)
            uiStateFlow.update { it.copy(currentProgressMs = positionMs, activeDevice = updatedDev) }
            onSyncRemoteState(updatedDev)
            return
        }
        mediaController?.seekTo(positionMs)
        uiStateFlow.update { it.copy(currentProgressMs = positionMs) }
        val curTrack = uiStateFlow.value.currentTrack
        if (curTrack != null) {
            remoteController.broadcastLocalState(
                isPlaying = mediaController?.isPlaying == true,
                currentTrack = curTrack,
                positionMs = positionMs,
                durationMs = uiStateFlow.value.durationMs,
                queue = uiStateFlow.value.queue,
                isShuffle = uiStateFlow.value.isShuffleEnabled,
                repeatMode = uiStateFlow.value.repeatMode.name.lowercase(),
                force = true
            )
        }
    }

    fun toggleRepeatMode() {
        val active = uiStateFlow.value.activeDevice
        val nextMode = when (uiStateFlow.value.repeatMode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        if (active != null) {
            remoteController.toggleRepeat()
            uiStateFlow.update { it.copy(repeatMode = nextMode) }
            return
        }
        val exoMode = when (nextMode) {
            PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
            PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        mediaController?.repeatMode = exoMode
        uiStateFlow.update { it.copy(repeatMode = nextMode) }
    }

    fun toggleShuffleMode() {
        val active = uiStateFlow.value.activeDevice
        val nextShuffle = !uiStateFlow.value.isShuffleEnabled
        if (active != null) {
            remoteController.toggleShuffle()
            uiStateFlow.update { it.copy(isShuffleEnabled = nextShuffle) }
            return
        }
        mediaController?.shuffleModeEnabled = nextShuffle
        uiStateFlow.update { it.copy(isShuffleEnabled = nextShuffle) }
    }

    fun removeFromQueue(index: Int) {
        uiStateFlow.update { current ->
            if (index in current.queue.indices) {
                val mutable = current.queue.toMutableList()
                mutable.removeAt(index)
                current.copy(queue = mutable)
            } else current
        }
    }

    fun clearQueue() {
        uiStateFlow.update { it.copy(queue = emptyList()) }
    }

    fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val active = uiStateFlow.value.activeDevice
                if (active != null) {
                    if (active.isPlaying) {
                        uiStateFlow.update {
                            val nextPos = (it.currentProgressMs + 500L).coerceAtMost(it.durationMs)
                            it.copy(currentProgressMs = nextPos)
                        }
                    }
                    delay(500L)
                } else {
                    val controller = mediaController
                    if (controller != null) {
                        val isPlaying = controller.isPlaying
                        val currentPos = controller.currentPosition
                        val duration = if (controller.duration > 0) controller.duration else uiStateFlow.value.durationMs

                        uiStateFlow.update {
                            it.copy(
                                isPlaying = isPlaying,
                                currentProgressMs = if (isPlaying || currentPos > 0L) currentPos else it.currentProgressMs,
                                durationMs = duration
                            )
                        }

                        if (isPlaying && uiStateFlow.value.currentTrack != null && uiStateFlow.value.activeDevice == null) {
                            remoteController.broadcastLocalState(
                                isPlaying = true,
                                currentTrack = uiStateFlow.value.currentTrack,
                                positionMs = currentPos,
                                durationMs = duration,
                                queue = uiStateFlow.value.queue,
                                isShuffle = uiStateFlow.value.isShuffleEnabled,
                                repeatMode = uiStateFlow.value.repeatMode.name.lowercase(),
                                force = false
                            )
                        }
                        delay(if (isPlaying) 1000L else 1000L)
                    } else {
                        delay(500L)
                    }
                }
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        mediaController?.removeListener(playerListener)
        mediaController = null
    }
}
