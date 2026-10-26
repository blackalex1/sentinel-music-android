package com.sentinel.music.presentation.main.modules

import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.player.AudioCacheManager
import com.sentinel.music.presentation.main.MainUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль управления оффлайн-режимом, скачиванием треков и кешем аудио
 */
class DownloadStorageModule(
    private val scope: CoroutineScope,
    private val sessionManager: SessionManager,
    private val offlineManager: OfflineManager,
    private val lyricsRepository: LyricsRepository,
    private val uiStateFlow: MutableStateFlow<MainUiState>
) {
    private var fullDownloadJob: Job? = null

    fun toggleDownloadMode() {
        val nextMode = !uiStateFlow.value.isDownloadMode
        offlineManager.isDownloadModeEnabled = nextMode
        uiStateFlow.update { it.copy(isDownloadMode = nextMode) }
    }

    fun downloadTrack(track: TrackDto, onComplete: ((Boolean) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val token = sessionManager.token ?: ""
            val success = offlineManager.downloadTrack(track, token, force = true)
            if (success) {
                try { lyricsRepository.getLyrics(track.id) } catch (_: Exception) {}
            }
            refreshStorageStats()
            withContext(Dispatchers.Main) {
                onComplete?.invoke(success)
            }
        }
    }

    fun deleteDownloadedTrack(track: TrackDto) {
        scope.launch(Dispatchers.IO) {
            offlineManager.deleteTrack(track.id, track.safeAudioFormat)
            refreshStorageStats()
        }
    }

    fun startFullDownload(onlyLiked: Boolean = false) {
        if (uiStateFlow.value.isFullDownloading) return

        val token = sessionManager.token ?: ""
        val library = uiStateFlow.value.tracks
        val candidates = if (onlyLiked) library.filter { it.isLiked } else library
        val missing = candidates.filter { !offlineManager.isTrackDownloaded(it.id, it.safeAudioFormat) }

        if (missing.isEmpty()) {
            uiStateFlow.update { it.copy(errorMessage = "Все выбранные треки уже скачаны в кеш") }
            return
        }

        fullDownloadJob = scope.launch(Dispatchers.IO) {
            uiStateFlow.update {
                it.copy(
                    isFullDownloading = true,
                    fullDownloadTotal = missing.size,
                    fullDownloadCurrentIndex = 0,
                    fullDownloadCompletedCount = 0,
                    fullDownloadFailedCount = 0,
                    fullDownloadProgress = 0f
                )
            }

            var completed = 0
            var failed = 0

            for ((index, track) in missing.withIndex()) {
                if (!isActive) break

                if (offlineManager.downloadOnlyOnWifi && !offlineManager.isWifiConnected()) {
                    uiStateFlow.update {
                        it.copy(
                            isFullDownloading = false,
                            errorMessage = "Скачивание приостановлено: нет подключения к Wi-Fi"
                        )
                    }
                    break
                }

                uiStateFlow.update {
                    it.copy(
                        fullDownloadCurrentIndex = index + 1,
                        fullDownloadCurrentTrackName = "${track.title} - ${track.artist}",
                        fullDownloadProgress = if (missing.isNotEmpty()) (index.toFloat() / missing.size.toFloat()) else 0f
                    )
                }

                val success = offlineManager.downloadTrack(track, token, force = true)
                if (success) {
                    completed++
                    try { lyricsRepository.getLyrics(track.id) } catch (_: Exception) {}
                } else {
                    failed++
                }

                uiStateFlow.update {
                    it.copy(
                        fullDownloadCompletedCount = completed,
                        fullDownloadFailedCount = failed,
                        fullDownloadProgress = if (missing.isNotEmpty()) ((index + 1).toFloat() / missing.size.toFloat()) else 1f
                    )
                }
            }

            uiStateFlow.update {
                it.copy(
                    isFullDownloading = false,
                    fullDownloadCurrentTrackName = "",
                    fullDownloadProgress = 1f
                )
            }
            refreshStorageStats()
        }
    }

    fun cancelFullDownload() {
        fullDownloadJob?.cancel()
        fullDownloadJob = null
        uiStateFlow.update {
            it.copy(
                isFullDownloading = false,
                fullDownloadCurrentTrackName = ""
            )
        }
    }

    fun setDownloadOnlyOnWifi(enabled: Boolean) {
        offlineManager.downloadOnlyOnWifi = enabled
        uiStateFlow.update { it.copy(downloadOnlyOnWifi = enabled) }
    }

    fun setPreloadNextTrack(enabled: Boolean) {
        offlineManager.preloadNextTrack = enabled
        uiStateFlow.update { it.copy(preloadNextTrack = enabled) }
    }

    fun setStreamCacheSizeMb(sizeMb: Int) {
        AudioCacheManager.setMaxCacheSizeMb(sessionManager.context, sizeMb)
        uiStateFlow.update { it.copy(maxStreamCacheSizeMb = sizeMb) }
        refreshStorageStats()
    }

    fun clearOfflineCache() {
        scope.launch(Dispatchers.IO) {
            offlineManager.clearOfflineCache()
            refreshStorageStats()
        }
    }

    fun clearStreamCache() {
        scope.launch(Dispatchers.IO) {
            AudioCacheManager.clearCache(sessionManager.context)
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        scope.launch(Dispatchers.IO) {
            val offlineBytes = offlineManager.getOfflineTracksSizeBytes()
            val streamBytes = AudioCacheManager.getStreamCacheSizeBytes(sessionManager.context)
            uiStateFlow.update {
                it.copy(
                    offlineTracksSizeBytes = offlineBytes,
                    streamCacheSizeBytes = streamBytes
                )
            }
        }
    }

    fun cancelAll() {
        fullDownloadJob?.cancel()
    }
}
