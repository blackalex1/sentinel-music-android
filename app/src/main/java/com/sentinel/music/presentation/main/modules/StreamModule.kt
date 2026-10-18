package com.sentinel.music.presentation.main.modules

import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.SearchTab
import com.sentinel.music.presentation.main.TrackFilterMode
import com.sentinel.music.presentation.navigation.MainTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль потокового радио "Моя волна", выбора настроений и умных миксов
 */
class StreamModule(
    private val scope: CoroutineScope,
    private val api: MusicApiService,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val onPlayTrack: (TrackDto, List<TrackDto>?, Boolean) -> Unit
) {
    fun startStream(customMood: String? = null, customRatio: Float? = null) {
        val state = uiStateFlow.value
        uiStateFlow.update { it.copy(isStreamActive = true, queue = emptyList()) }
        scope.launch {
            try {
                val m = customMood ?: state.selectedMood
                val r = customRatio ?: state.selectedDiscoveryRatio
                val next = withContext(Dispatchers.IO) {
                    api.getNextStreamTrack(
                        currentTrackId = state.currentTrack?.id,
                        mood = m,
                        discoveryRatio = r
                    )
                }
                onPlayTrack(next, null, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopStream() {
        uiStateFlow.update { it.copy(isStreamActive = false) }
    }

    fun setMood(mood: String) {
        uiStateFlow.update { it.copy(selectedMood = mood) }
        if (uiStateFlow.value.isStreamActive) {
            startStream(customMood = mood)
        }
    }

    fun playMoodMix(moodKey: String) {
        val moodTracks = uiStateFlow.value.tracks.filter { it.matchesMood(moodKey) }.shuffled()
        if (moodTracks.isNotEmpty()) {
            onPlayTrack(moodTracks.first(), moodTracks.drop(1), false)
        } else {
            startStream(customMood = moodKey)
        }
    }

    fun selectMoodInLibrary(moodKey: String) {
        val filterMode = when (moodKey) {
            "energy" -> TrackFilterMode.MOOD_ENERGY
            "chill" -> TrackFilterMode.MOOD_CHILL
            "happy" -> TrackFilterMode.MOOD_HAPPY
            "sad" -> TrackFilterMode.MOOD_SAD
            else -> TrackFilterMode.ALL
        }
        uiStateFlow.update {
            it.copy(
                selectedTab = MainTab.TRACKS,
                trackFilter = filterMode,
                searchTab = SearchTab.LIBRARY
            )
        }
    }

    fun setDiscoveryRatio(ratio: Float) {
        uiStateFlow.update { it.copy(selectedDiscoveryRatio = ratio) }
        if (uiStateFlow.value.isStreamActive) {
            startStream(customRatio = ratio)
        }
    }
}
