package com.sentinel.music.presentation.tracks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.presentation.main.SearchTab
import com.sentinel.music.presentation.main.TrackFilterMode
import com.sentinel.music.presentation.tracks.components.CandidateTracksContent
import com.sentinel.music.presentation.tracks.components.LibraryTracksContent
import com.sentinel.music.presentation.tracks.components.TracksSearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    tracks: List<TrackDto>,
    currentTrack: TrackDto?,
    isPlaying: Boolean = false,
    serverUrl: String,
    token: String,
    offlineManager: OfflineManager,
    downloadedTrackIds: Set<Int> = emptySet(),
    downloadingTrackIds: Set<Int> = emptySet(),
    isSearchVisible: Boolean = false,
    onCloseSearch: () -> Unit = {},
    onHideSearch: () -> Unit = onCloseSearch,
    searchQuery: String = "",
    searchTab: SearchTab = SearchTab.LIBRARY,
    searchResults: List<TrackDto> = emptyList(),
    isSearching: Boolean = false,
    newCandidates: List<Map<String, Any?>> = emptyList(),
    isSearchingNewCandidates: Boolean = false,
    isLoadingTracks: Boolean = false,
    initialFilter: TrackFilterMode = TrackFilterMode.ALL,
    onRefresh: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onSearchTabChange: (SearchTab) -> Unit = {},
    onTrackClick: (TrackDto, List<TrackDto>) -> Unit,
    onLikeTrack: (TrackDto) -> Unit,
    onPlayNextClick: ((TrackDto) -> Unit)? = null,
    onAddToPlaylistClick: (TrackDto) -> Unit = {},
    onReplaceTrackClick: (TrackDto) -> Unit = {},
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDeleteFromServerClick: ((TrackDto) -> Unit)? = null,
    onDownloadCandidate: (Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, cb -> cb(true, null) },
    onPreviewCandidate: ((Map<String, Any?>) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var selectedCandidateFilter by remember { mutableStateOf("all") }
    var downloadingCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val listState = rememberLazyListState()
    val candidateListState = rememberLazyListState()

    BackHandler(enabled = isSearchVisible || searchQuery.isNotBlank()) {
        if (isSearchVisible) {
            onCloseSearch()
        } else {
            onSearchQueryChange("")
        }
    }

    // Автоматическое скрытие поиска при листании пользователем
    val isTracksDragged by listState.interactionSource.collectIsDraggedAsState()
    val isCandidatesDragged by candidateListState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isTracksDragged, isCandidatesDragged) {
        if ((isTracksDragged || isCandidatesDragged) && isSearchVisible) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onHideSearch()
        }
    }

    val nestedScrollConnection = remember(isSearchVisible) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isSearchVisible && source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 4f) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onHideSearch()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // Вычисление отфильтрованных треков библиотеки
    val filteredTracks = remember(tracks, searchQuery, searchResults, isSearching, selectedFilter) {
        val baseList = if (searchQuery.isNotBlank()) {
            if (searchResults.isNotEmpty()) {
                searchResults
            } else if (isSearching) {
                val q = searchQuery.trim().lowercase()
                tracks.filter {
                    it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    (it.album?.lowercase()?.contains(q) == true)
                }
            } else {
                emptyList()
            }
        } else {
            tracks
        }

        when (selectedFilter) {
            TrackFilterMode.ALL -> baseList
            TrackFilterMode.MOOD_ENERGY -> baseList.filter { it.matchesMood("energy") }
            TrackFilterMode.MOOD_CHILL -> baseList.filter { it.matchesMood("chill") }
            TrackFilterMode.MOOD_HAPPY -> baseList.filter { it.matchesMood("happy") }
            TrackFilterMode.MOOD_SAD -> baseList.filter { it.matchesMood("sad") }
            TrackFilterMode.FLAC_ONLY -> baseList.filter { it.safeAudioFormat.equals("flac", ignoreCase = true) }
            TrackFilterMode.LIKED -> baseList.filter { it.isLiked }
            TrackFilterMode.OFFLINE -> baseList.filter { downloadedTrackIds.contains(it.id) }
        }
    }

    // Вычисление отфильтрованных кандидатов из плагинов
    val filteredCandidates = remember(newCandidates, selectedCandidateFilter) {
        if (selectedCandidateFilter == "all") {
            newCandidates
        } else {
            newCandidates.filter { cand ->
                val source = (cand["source_name"] as? String ?: cand["source"] as? String ?: "").lowercase()
                val format = (cand["format"] as? String ?: "").lowercase()
                val vType = (cand["version_type"] as? String ?: "").lowercase()
                when (selectedCandidateFilter) {
                    "flac" -> format == "flac" || vType.contains("flac")
                    else -> source.equals(selectedCandidateFilter, ignoreCase = true)
                }
            }
        }
    }

    // Опции фильтрации кандидатов
    val candidateFilterOptions = remember(newCandidates) {
        val list = mutableListOf(
            "all" to "Все",
            "flac" to "FLAC Lossless"
        )
        val dynamicSources = newCandidates.mapNotNull {
            val src = (it["source_name"] as? String ?: it["source"] as? String)?.trim()
            if (!src.isNullOrEmpty()) src else null
        }.distinct()
        for (src in dynamicSources) {
            val label = src.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            list.add(src.lowercase() to label)
        }
        list
    }

    // Скролл к началу при изменении запроса или фильтра
    LaunchedEffect(searchQuery, searchResults, selectedFilter) {
        if (filteredTracks.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(searchQuery, filteredCandidates, selectedCandidateFilter) {
        if (filteredCandidates.isNotEmpty()) {
            candidateListState.scrollToItem(0)
        }
    }

    LaunchedEffect(searchTab) {
        if (searchTab == SearchTab.LIBRARY && filteredTracks.isNotEmpty()) {
            listState.scrollToItem(0)
        } else if (searchTab == SearchTab.NEW_TRACKS && filteredCandidates.isNotEmpty()) {
            candidateListState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .padding(horizontal = 16.dp)
    ) {
        // Плашка поиска
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            TracksSearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                searchTab = searchTab,
                onSearchTabChange = onSearchTabChange,
                isSearching = isSearching,
                isSearchingNewCandidates = isSearchingNewCandidates,
                focusRequester = focusRequester,
                onCloseSearch = onCloseSearch
            )
        }

        if (searchTab == SearchTab.LIBRARY) {
            LibraryTracksContent(
                tracks = tracks,
                filteredTracks = filteredTracks,
                currentTrack = currentTrack,
                serverUrl = serverUrl,
                token = token,
                downloadedTrackIds = downloadedTrackIds,
                downloadingTrackIds = downloadingTrackIds,
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                searchQuery = searchQuery,
                isSearchVisible = isSearchVisible,
                isSearching = isSearching,
                isLoadingTracks = isLoadingTracks,
                listState = listState,
                onRefresh = onRefresh,
                onResetSearch = { onSearchQueryChange("") },
                onTrackClick = onTrackClick,
                onLikeTrack = onLikeTrack,
                onPlayNextClick = onPlayNextClick,
                onAddToPlaylistClick = onAddToPlaylistClick,
                onReplaceTrackClick = onReplaceTrackClick,
                onDownloadTrack = onDownloadTrack,
                onDeleteDownloadedTrack = onDeleteDownloadedTrack,
                onDeleteFromServerClick = onDeleteFromServerClick
            )
        } else {
            CandidateTracksContent(
                newCandidates = newCandidates,
                filteredCandidates = filteredCandidates,
                candidateFilterOptions = candidateFilterOptions,
                selectedCandidateFilter = selectedCandidateFilter,
                onCandidateFilterSelected = { selectedCandidateFilter = it },
                searchQuery = searchQuery,
                isSearchVisible = isSearchVisible,
                isSearchingNewCandidates = isSearchingNewCandidates,
                candidateListState = candidateListState,
                currentTrack = currentTrack,
                isPlaying = isPlaying,
                downloadingCandidateIds = downloadingCandidateIds,
                downloadedCandidateIds = downloadedCandidateIds,
                onResetSearch = { onSearchQueryChange("") },
                onPreviewCandidate = onPreviewCandidate,
                onDownloadCandidate = { cand ->
                    val cid = cand["candidate_id"] as? String ?: ""
                    if (!downloadingCandidateIds.contains(cid) && !downloadedCandidateIds.contains(cid)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        downloadingCandidateIds = downloadingCandidateIds + cid
                        onDownloadCandidate(cand) { success, msg ->
                            downloadingCandidateIds = downloadingCandidateIds - cid
                            if (success) {
                                downloadedCandidateIds = downloadedCandidateIds + cid
                                val text = msg ?: "Трек добавлен в фонотеку"
                                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val text = msg ?: "Не удалось загрузить трек"
                                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }
}
