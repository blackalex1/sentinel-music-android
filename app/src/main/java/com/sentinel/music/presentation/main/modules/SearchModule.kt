package com.sentinel.music.presentation.main.modules

import com.google.gson.Gson
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.SearchTab
import com.sentinel.music.presentation.main.updateTrackInCollections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль поиска по библиотеке, поиска новых кандидатов в сети и замены треков
 */
class SearchModule(
    private val scope: CoroutineScope,
    private val api: MusicApiService,
    private val sessionManager: SessionManager,
    private val offlineManager: OfflineManager,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val onPlayTrack: (TrackDto, List<TrackDto>?, Boolean) -> Unit,
    private val onRefreshStorageStats: () -> Unit
) {
    private var librarySearchJob: Job? = null
    private var candidateSearchJob: Job? = null

    fun setSearchTab(tab: SearchTab) {
        uiStateFlow.update { it.copy(searchTab = tab) }
        val q = uiStateFlow.value.trackSearchQuery.trim()
        if (q.isNotEmpty()) {
            if (tab == SearchTab.NEW_TRACKS && uiStateFlow.value.newCandidateResults.isEmpty()) {
                triggerCandidateSearch(q)
            } else if (tab == SearchTab.LIBRARY && uiStateFlow.value.searchResults.isEmpty()) {
                triggerLibrarySearch(q)
            }
        }
    }

    fun setSearchQuery(query: String) {
        uiStateFlow.update { it.copy(trackSearchQuery = query) }
        librarySearchJob?.cancel()
        candidateSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            uiStateFlow.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    newCandidateResults = emptyList(),
                    isSearchingNewCandidates = false
                )
            }
            return
        }

        triggerLibrarySearch(trimmed)
        triggerCandidateSearch(trimmed)
    }

    private fun triggerLibrarySearch(query: String) {
        librarySearchJob?.cancel()
        librarySearchJob = scope.launch {
            delay(300)
            uiStateFlow.update { it.copy(isSearching = true) }
            try {
                val results = withContext(Dispatchers.IO) {
                    api.searchTracks(query = query, limit = 100)
                }
                uiStateFlow.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                uiStateFlow.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun triggerCandidateSearch(query: String) {
        candidateSearchJob?.cancel()
        candidateSearchJob = scope.launch {
            delay(400)
            uiStateFlow.update { it.copy(isSearchingNewCandidates = true) }
            try {
                val res = withContext(Dispatchers.IO) {
                    api.searchNewCandidates(query = query, limit = 10)
                }
                @Suppress("UNCHECKED_CAST")
                val candidates = res["candidates"] as? List<Map<String, Any?>> ?: emptyList()
                uiStateFlow.update {
                    it.copy(newCandidateResults = candidates, isSearchingNewCandidates = false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                uiStateFlow.update { it.copy(isSearchingNewCandidates = false) }
            }
        }
    }

    fun clearSearch() {
        librarySearchJob?.cancel()
        candidateSearchJob?.cancel()
        uiStateFlow.update {
            it.copy(
                trackSearchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                newCandidateResults = emptyList(),
                isSearchingNewCandidates = false
            )
        }
    }

    fun downloadNewCandidate(candidate: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.downloadCandidate(mapOf("candidate" to candidate))
                }
                val status = res["status"] as? String ?: "success"
                val message = res["message"] as? String
                val gson = Gson()
                val trackJson = gson.toJson(res["track"])
                val newTrack = gson.fromJson(trackJson, TrackDto::class.java)
                if (newTrack != null) {
                    uiStateFlow.update { current ->
                        val updatedTracks = if (current.tracks.any { it.id == newTrack.id }) {
                            current.tracks.map { if (it.id == newTrack.id) newTrack else it }
                        } else {
                            listOf(newTrack) + current.tracks
                        }
                        current.copy(tracks = updatedTracks)
                    }
                }
                val isSuccess = status == "success" || status == "queued" || newTrack != null
                onComplete(isSuccess, message)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun updateReplacedTrack(updatedTrack: TrackDto) {
        uiStateFlow.update { it.updateTrackInCollections(updatedTrack.id) { updatedTrack } }

        if (uiStateFlow.value.currentTrack?.id == updatedTrack.id) {
            onPlayTrack(updatedTrack, uiStateFlow.value.queue, uiStateFlow.value.isStreamActive)
        }

        scope.launch(Dispatchers.IO) {
            val wasDownloaded = offlineManager.isTrackDownloaded(updatedTrack.id, updatedTrack.audioFormat)
            if (wasDownloaded) {
                offlineManager.deleteTrack(updatedTrack.id, updatedTrack.audioFormat)
                val token = sessionManager.token ?: ""
                if (token.isNotEmpty()) {
                    offlineManager.downloadTrack(updatedTrack, token, force = true)
                }
                onRefreshStorageStats()
            }
        }
    }

    fun cancelAll() {
        librarySearchJob?.cancel()
        candidateSearchJob?.cancel()
    }
}
