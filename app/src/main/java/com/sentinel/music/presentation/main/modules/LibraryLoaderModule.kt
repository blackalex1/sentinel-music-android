package com.sentinel.music.presentation.main.modules

import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.main.MainUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль начальной и пакетной загрузки треков медиатеки пользователя с сервера
 */
class LibraryLoaderModule(
    private val scope: CoroutineScope,
    private val api: MusicApiService,
    private val sessionManager: SessionManager,
    private val uiStateFlow: MutableStateFlow<MainUiState>
) {
    fun loadInitialData() {
        scope.launch {
            // 0. Синхронизируем актуальные данные профиля пользователя (username, isAdmin) с сервера
            try {
                val profile = withContext(Dispatchers.IO) { api.getMe() }
                sessionManager.username = profile.username
                sessionManager.isAdmin = profile.isAdmin
                uiStateFlow.update {
                    it.copy(
                        currentUsername = profile.username,
                        isCurrentUserAdmin = profile.isAdmin
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            uiStateFlow.update { it.copy(isLoadingTracks = true, errorMessage = null) }

            // 1. Загружаем точное количество треков на сервере (/api/tracks/count)
            var serverTotal = 0
            try {
                val countRes = withContext(Dispatchers.IO) { api.getTracksCount() }
                serverTotal = countRes["count"] ?: 0
                if (serverTotal > 0) {
                    uiStateFlow.update { it.copy(totalServerTracksCount = serverTotal) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Загружаем все треки медиатеки порциями по 5000
            try {
                val batchSize = 5000
                var currentOffset = 0
                val loadedList = mutableListOf<TrackDto>()

                val firstBatch = withContext(Dispatchers.IO) {
                    api.getTracks(limit = batchSize, offset = currentOffset)
                }
                loadedList.addAll(firstBatch)
                val effectiveTotal = if (serverTotal > 0) serverTotal else loadedList.size

                uiStateFlow.update {
                    it.copy(
                        tracks = loadedList.toList(),
                        isLoadingTracks = false,
                        totalServerTracksCount = effectiveTotal
                    )
                }

                // Если треков на сервере больше 5000, догружаем оставшиеся в фоне
                while (firstBatch.size == batchSize && (serverTotal == 0 || loadedList.size < serverTotal)) {
                    currentOffset += batchSize
                    val nextBatch = withContext(Dispatchers.IO) {
                        api.getTracks(limit = batchSize, offset = currentOffset)
                    }
                    if (nextBatch.isEmpty()) break
                    loadedList.addAll(nextBatch)
                    uiStateFlow.update {
                        it.copy(
                            tracks = loadedList.toList(),
                            totalServerTracksCount = maxOf(serverTotal, loadedList.size)
                        )
                    }
                    if (nextBatch.size < batchSize) break
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiStateFlow.update { it.copy(isLoadingTracks = false, errorMessage = e.localizedMessage) }
            }

            try {
                val recents = withContext(Dispatchers.IO) { api.getRecentTracks(limit = 30) }
                uiStateFlow.update { it.copy(recentTracks = recents) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
