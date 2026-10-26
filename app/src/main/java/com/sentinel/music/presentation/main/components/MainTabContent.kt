package com.sentinel.music.presentation.main.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.home.HomeScreen
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.MainViewModel
import com.sentinel.music.presentation.navigation.MainTab
import com.sentinel.music.presentation.playlists.PlaylistsScreen
import com.sentinel.music.presentation.settings.SettingsScreen
import com.sentinel.music.presentation.stream.StreamScreen
import com.sentinel.music.presentation.tracks.TracksScreen

@Composable
fun MainTabContent(
    state: MainUiState,
    viewModel: MainViewModel,
    sessionManager: SessionManager,
    serverUrl: String,
    token: String,
    api: MusicApiService,
    offlineManager: OfflineManager,
    isSearchVisible: Boolean,
    onCloseSearch: () -> Unit,
    onHideSearch: () -> Unit,
    onOpenLyrics: () -> Unit,
    onSelectTrackForPlaylist: (TrackDto) -> Unit,
    onSelectTrackForReplace: (TrackDto) -> Unit,
    onSelectTrackForDelete: (TrackDto) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        when (state.selectedTab) {
            MainTab.HOME -> {
                HomeScreen(
                    recentTracks = state.recentTracks,
                    allTracks = state.tracks,
                    currentTrack = state.currentTrack,
                    serverUrl = serverUrl,
                    token = token,
                    downloadedTrackIds = state.downloadedTrackIds,
                    downloadingTrackIds = state.downloadingTrackIds,
                    isRefreshing = state.isLoadingTracks,
                    onRefresh = { viewModel.loadInitialData() },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onStartStreamClick = {
                        viewModel.startStream()
                        viewModel.setTab(MainTab.STREAM)
                    },
                    onMoodClick = { moodKey -> viewModel.selectMoodInLibrary(moodKey) },
                    onPlayMoodMix = { moodKey -> viewModel.playMoodMix(moodKey) },
                    onLikeTrack = { viewModel.toggleLike(it) },
                    onPlayNextClick = {
                        viewModel.playTrackNext(it)
                        Toast.makeText(context, "«${it.title}» будет воспроизведен следующим", Toast.LENGTH_SHORT).show()
                    },
                    onAddToPlaylistClick = onSelectTrackForPlaylist,
                    onReplaceTrackClick = onSelectTrackForReplace,
                    onDownloadTrack = { viewModel.downloadTrack(it) },
                    onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) },
                    onDeleteFromServerClick = onSelectTrackForDelete
                )
            }
            MainTab.STREAM -> {
                StreamScreen(
                    currentTrack = state.currentTrack,
                    isPlaying = state.isPlaying,
                    isLiked = state.isLiked,
                    isDisliked = state.isDisliked,
                    selectedMood = state.selectedMood,
                    selectedDiscoveryRatio = state.selectedDiscoveryRatio,
                    serverUrl = serverUrl,
                    token = token,
                    isStreamActive = state.isStreamActive,
                    onStartStream = { viewModel.startStream() },
                    onMoodSelected = { mood -> viewModel.setMood(mood) },
                    onDiscoveryRatioSelected = { ratio -> viewModel.setDiscoveryRatio(ratio) },
                    onPlayPause = { viewModel.playPause() },
                    onNextStreamTrack = { viewModel.playNext() },
                    onLike = { viewModel.toggleLike() },
                    onDislike = { viewModel.toggleDislike() },
                    onOpenLyricsClick = onOpenLyrics
                )
            }
            MainTab.TRACKS -> {
                TracksScreen(
                    tracks = state.tracks,
                    currentTrack = state.currentTrack,
                    isPlaying = state.isPlaying,
                    serverUrl = serverUrl,
                    token = token,
                    offlineManager = offlineManager,
                    downloadedTrackIds = state.downloadedTrackIds,
                    downloadingTrackIds = state.downloadingTrackIds,
                    initialFilter = state.trackFilter,
                    isSearchVisible = isSearchVisible,
                    onCloseSearch = onCloseSearch,
                    onHideSearch = onHideSearch,
                    searchQuery = state.trackSearchQuery,
                    searchTab = state.searchTab,
                    searchResults = state.searchResults,
                    isSearching = state.isSearching,
                    newCandidates = state.newCandidateResults,
                    isSearchingNewCandidates = state.isSearchingNewCandidates,
                    isLoadingTracks = state.isLoadingTracks,
                    onRefresh = { viewModel.loadInitialData() },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSearchTabChange = { viewModel.setSearchTab(it) },
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    onLikeTrack = { viewModel.toggleLike(it) },
                    onPlayNextClick = {
                        viewModel.playTrackNext(it)
                        Toast.makeText(context, "«${it.title}» будет воспроизведен следующим", Toast.LENGTH_SHORT).show()
                    },
                    onAddToPlaylistClick = onSelectTrackForPlaylist,
                    onReplaceTrackClick = onSelectTrackForReplace,
                    onDownloadTrack = { viewModel.downloadTrack(it) },
                    onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) },
                    onDeleteFromServerClick = onSelectTrackForDelete,
                    onDownloadCandidate = { candidate, onComplete ->
                        viewModel.downloadNewCandidate(candidate) { success, message ->
                            onComplete(success, message)
                        }
                    },
                    onPreviewCandidate = { candidate ->
                        viewModel.playCandidatePreview(candidate)
                    }
                )
            }
            MainTab.PLAYLISTS -> {
                PlaylistsScreen(
                    api = api,
                    serverUrl = serverUrl,
                    token = token,
                    downloadedTrackIds = state.downloadedTrackIds,
                    downloadingTrackIds = state.downloadingTrackIds,
                    onTrackClick = { track, queue -> viewModel.playTrack(track, queue) },
                    currentTrack = state.currentTrack,
                    onPlayNextClick = {
                        viewModel.playTrackNext(it)
                        Toast.makeText(context, "«${it.title}» будет воспроизведен следующим", Toast.LENGTH_SHORT).show()
                    },
                    onAddToPlaylistClick = onSelectTrackForPlaylist,
                    onReplaceTrackClick = onSelectTrackForReplace,
                    onDownloadTrack = { viewModel.downloadTrack(it) },
                    onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) },
                    onDeleteFromServerClick = onSelectTrackForDelete
                )
            }
            MainTab.SETTINGS -> {
                SettingsScreen(
                    viewModel = viewModel,
                    sessionManager = sessionManager,
                    onLogout = onLogout
                )
            }
        }
    }
}
