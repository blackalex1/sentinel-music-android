package com.sentinel.music.presentation.main

import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.domain.models.LyricLine
import com.sentinel.music.player.EqualizerState
import com.sentinel.music.presentation.navigation.MainTab

enum class TrackFilterMode(val label: String, val moodKey: String? = null) {
    ALL("Все"),
    FLAC_ONLY("Только FLAC"),
    LIKED("Любимые"),
    OFFLINE("Оффлайн"),
    MOOD_ENERGY("⚡ Бодрое", "energy"),
    MOOD_CHILL("☕ Спокойное", "chill"),
    MOOD_HAPPY("😄 Радостное", "happy"),
    MOOD_SAD("🌧️ Грустное", "sad")
}

enum class PlaybackRepeatMode(val title: String) {
    OFF("Повтор выключен"),
    ALL("Повторять все"),
    ONE("Повторять один трек")
}

enum class SearchTab(val title: String) {
    LIBRARY("В медиатеке"),
    NEW_TRACKS("Новые треки")
}

data class MainUiState(
    val selectedTab: MainTab = MainTab.HOME,
    val tracks: List<TrackDto> = emptyList(),
    val totalServerTracksCount: Int = 0,
    val recentTracks: List<TrackDto> = emptyList(),
    val currentTrack: TrackDto? = null,
    val queue: List<TrackDto> = emptyList(),
    val isPlaying: Boolean = false,
    val currentProgressMs: Long = 0L,
    val durationMs: Long = 1L,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val isStreamActive: Boolean = false,
    val selectedMood: String = "default",
    val selectedDiscoveryRatio: Float = 0.2f,
    val isDownloadMode: Boolean = false,
    val isLoadingTracks: Boolean = false,
    val errorMessage: String? = null,
    val trackFilter: TrackFilterMode = TrackFilterMode.ALL,
    val trackSearchQuery: String = "",
    val searchTab: SearchTab = SearchTab.LIBRARY,
    val searchResults: List<TrackDto> = emptyList(),
    val isSearching: Boolean = false,
    val newCandidateResults: List<Map<String, Any?>> = emptyList(),
    val isSearchingNewCandidates: Boolean = false,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val isShuffleEnabled: Boolean = false,
    val isLoudnessNormalizationEnabled: Boolean = true,
    val loudnessTargetLufs: Float = -14.0f,
    val currentLimiterMode: String = "soft",
    val equalizerState: EqualizerState = EqualizerState(),
    val downloadedTrackIds: Set<Int> = emptySet(),
    val downloadingTrackIds: Set<Int> = emptySet(),
    val isFullDownloading: Boolean = false,
    val fullDownloadProgress: Float = 0f,
    val fullDownloadCurrentIndex: Int = 0,
    val fullDownloadTotal: Int = 0,
    val fullDownloadCurrentTrackName: String = "",
    val fullDownloadCompletedCount: Int = 0,
    val fullDownloadFailedCount: Int = 0,
    val offlineTracksSizeBytes: Long = 0L,
    val streamCacheSizeBytes: Long = 0L,
    val maxStreamCacheSizeMb: Int = 500,
    val downloadOnlyOnWifi: Boolean = false,
    val preloadNextTrack: Boolean = true,
    val availableDevices: List<com.sentinel.music.data.model.RemoteDeviceDto> = emptyList(),
    val activeDevice: com.sentinel.music.data.model.RemoteDeviceDto? = null,
    val isDevicesDialogOpen: Boolean = false,
    val hasPc: Boolean = false,
    val isPcOnline: Boolean = false,
    val discoveredPc: com.sentinel.music.data.model.RemoteDeviceDto? = null,
    val showStartupDeviceChooser: Boolean = false,
    val isPcPlaybackHandoffEnabled: Boolean = true,
    val currentUsername: String = "",
    val isCurrentUserAdmin: Boolean = false
)
