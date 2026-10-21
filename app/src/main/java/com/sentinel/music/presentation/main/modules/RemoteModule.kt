package com.sentinel.music.presentation.main.modules

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.sentinel.music.data.model.RemoteDeviceDto
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.remote.RemoteController
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.player.EqualizerManager
import com.sentinel.music.player.MusicService
import com.sentinel.music.presentation.main.MainUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Модуль интеграции с ПК (Sentinel Connect) и синхронизации с системной шторкой MusicService
 */
class RemoteModule(
    private val scope: CoroutineScope,
    private val sessionManager: SessionManager,
    private val remoteController: RemoteController,
    private val equalizerManager: EqualizerManager,
    private val lyricsRepository: LyricsRepository,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val getMediaController: () -> MediaController?,
    private val onPrepareLocalPlayback: (TrackDto, List<TrackDto>?, Long, Boolean) -> Unit
) {
    fun openDevicesDialog() {
        remoteController.refreshDevicesHttp()
        uiStateFlow.update { it.copy(isDevicesDialogOpen = true) }
    }

    fun closeDevicesDialog() {
        uiStateFlow.update { it.copy(isDevicesDialogOpen = false) }
    }

    fun dismissStartupDeviceChooser() {
        uiStateFlow.update { it.copy(showStartupDeviceChooser = false) }
    }

    fun chooseStartupPlaybackOnPc() {
        val pc = uiStateFlow.value.discoveredPc
        selectPlaybackDevice(pc)
        uiStateFlow.update { it.copy(showStartupDeviceChooser = false) }
    }

    fun chooseStartupPlaybackOnPhone() {
        selectPlaybackDevice(null)
        uiStateFlow.update { it.copy(showStartupDeviceChooser = false) }
    }

    fun selectPlaybackDevice(device: RemoteDeviceDto?) {
        val current = uiStateFlow.value
        val controller = getMediaController()

        if (device == null) {
            // Возврат воспроизведения на этот телефон
            val remoteDev = current.activeDevice
            if (remoteDev != null) {
                // Если управляли компьютером — останавливаем воспроизведение на ПК
                remoteController.pause()
            }
            val remoteTrack = remoteDev?.currentTrack ?: current.currentTrack
            val remotePos = remoteDev?.positionMs ?: current.currentProgressMs
            val remoteQueue = remoteDev?.safeQueue?.takeIf { it.isNotEmpty() } ?: current.queue

            remoteController.selectDevice(null)
            uiStateFlow.update { it.copy(activeDevice = null, isDevicesDialogOpen = false) }
            syncRemoteStateToService(null)

            val localController = getMediaController()
            if (localController != null && localController.currentMediaItem != null) {
                uiStateFlow.update {
                    it.copy(
                        isPlaying = localController.isPlaying,
                        currentProgressMs = localController.currentPosition
                    )
                }
            } else {
                val remoteTrack = remoteDev?.currentTrack ?: current.currentTrack
                val remotePos = remoteDev?.positionMs ?: current.currentProgressMs
                val remoteQueue = remoteDev?.safeQueue?.takeIf { it.isNotEmpty() } ?: current.queue
                if (remoteTrack != null) {
                    onPrepareLocalPlayback(remoteTrack, remoteQueue, remotePos, false)
                }
            }
        } else {
            // Переключение управления на ПК (БЕЗ автозапуска воспроизведения)
            val localTrack = current.currentTrack
            val localPos = current.currentProgressMs
            val localQueue = current.queue

            controller?.pause()
            remoteController.selectDevice(device)

            // Синхронизируем эквалайзер и лимитер с выбранного устройства
            device.equalizer?.let { eq ->
                equalizerManager.applyRemoteEqualizer(eq)
            }
            if (device.limiter != null) {
                uiStateFlow.update {
                    it.copy(
                        isLoudnessNormalizationEnabled = device.limiter.isLoudness,
                        currentLimiterMode = device.limiter.mode
                    )
                }
            } else if (device.equalizer?.currentLimiterMode != null) {
                uiStateFlow.update {
                    it.copy(currentLimiterMode = device.equalizer.currentLimiterMode)
                }
            }

            if (device.isPlaying && device.currentTrack != null) {
                // Если на ПК уже играет музыка — телефон подстраивается под состояние компьютера
                val dur = if (device.durationMs > 0) device.durationMs else (device.currentTrack.duration * 1000).toLong().coerceAtLeast(1L)
                val devQueue = device.safeQueue
                uiStateFlow.update {
                    it.copy(
                        activeDevice = device,
                        isDevicesDialogOpen = false,
                        currentTrack = device.currentTrack,
                        isPlaying = true,
                        currentProgressMs = device.positionMs,
                        durationMs = dur,
                        queue = if (devQueue.isNotEmpty()) devQueue else it.queue,
                        isLiked = device.currentTrack.isLiked,
                        isDisliked = device.currentTrack.isDisliked
                    )
                }
                scope.launch {
                    val fetchedLyrics = lyricsRepository.getLyrics(device.currentTrack.id)
                    if (uiStateFlow.value.currentTrack?.id == device.currentTrack.id) {
                        uiStateFlow.update { it.copy(lyrics = fetchedLyrics) }
                    }
                }
                syncRemoteStateToService(device)
            } else if (localTrack != null && current.isPcPlaybackHandoffEnabled) {
                // На ПК музыка не играет, а на телефоне играл/стоял на паузе трек.
                // Передаем текущий трек, позицию и очередь с телефона на ПК с сохранением состояния воспроизведения!
                val shouldPlay = current.isPlaying
                remoteController.transferPlaybackToDevice(
                    targetDeviceId = device.id,
                    track = localTrack,
                    queue = localQueue,
                    positionMs = localPos,
                    autoPlay = shouldPlay
                )
                val dur = (localTrack.duration * 1000).toLong().coerceAtLeast(1L)
                val updatedDev = device.copy(
                    currentTrack = localTrack,
                    positionMs = localPos,
                    durationMs = dur,
                    isPlaying = shouldPlay,
                    queue = localQueue
                )
                uiStateFlow.update {
                    it.copy(
                        activeDevice = updatedDev,
                        isDevicesDialogOpen = false,
                        currentTrack = localTrack,
                        isPlaying = shouldPlay,
                        currentProgressMs = localPos,
                        durationMs = dur,
                        queue = localQueue,
                        isLiked = localTrack.isLiked,
                        isDisliked = localTrack.isDisliked
                    )
                }
                syncRemoteStateToService(updatedDev)
            } else {
                val devTrack = device.currentTrack
                val dur = if (device.durationMs > 0) device.durationMs else (devTrack?.duration?.times(1000))?.toLong()?.coerceAtLeast(1L) ?: current.durationMs
                val devQueue = device.safeQueue
                uiStateFlow.update {
                    it.copy(
                        activeDevice = device,
                        isDevicesDialogOpen = false,
                        currentTrack = devTrack ?: it.currentTrack,
                        isPlaying = device.isPlaying,
                        currentProgressMs = device.positionMs,
                        durationMs = dur,
                        queue = if (devQueue.isNotEmpty()) devQueue else it.queue
                    )
                }
                syncRemoteStateToService(device)
            }
        }
    }

    fun syncRemoteStateToService(overrideDev: RemoteDeviceDto? = null) {
        val controller = getMediaController() ?: return
        val dev = overrideDev ?: uiStateFlow.value.activeDevice
        if (dev != null && dev.currentTrack != null) {
            val track = dev.currentTrack
            val token = sessionManager.token ?: ""
            val serverUrl = sessionManager.serverUrl
            val coverUrl = "$serverUrl/api/covers/${track.id}?token=$token"
            val dur = if (dev.durationMs > 0) dev.durationMs else (track.duration * 1000).toLong().coerceAtLeast(1L)
            val devName = dev.name.ifBlank { "Компьютер" }

            val args = Bundle().apply {
                putInt("TRACK_ID", track.id)
                putString("TITLE", track.title)
                putString("ARTIST", track.artist)
                putString("ALBUM", track.album ?: "Sentinel Music")
                putString("COVER_URL", coverUrl)
                putBoolean("IS_PLAYING", dev.isPlaying)
                putBoolean("IS_LIKED", track.isLiked)
                putBoolean("IS_DISLIKED", track.isDisliked)
                putLong("POSITION_MS", dev.positionMs)
                putLong("DURATION_MS", dur)
                putString("DEVICE_NAME", devName)
            }
            controller.sendCustomCommand(
                SessionCommand(MusicService.ACTION_SYNC_REMOTE_STATE, Bundle.EMPTY),
                args
            )
        } else {
            controller.sendCustomCommand(
                SessionCommand(MusicService.ACTION_CLEAR_REMOTE_STATE, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }
}
