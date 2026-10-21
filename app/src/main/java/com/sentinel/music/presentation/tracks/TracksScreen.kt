package com.sentinel.music.presentation.tracks

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.presentation.components.media.TrackCover
import com.sentinel.music.presentation.components.track.TrackActionDropdown
import com.sentinel.music.presentation.main.SearchTab
import com.sentinel.music.presentation.main.TrackFilterMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    tracks: List<TrackDto>,
    currentTrack: TrackDto?,
    serverUrl: String,
    token: String,
    offlineManager: OfflineManager,
    downloadedTrackIds: Set<Int> = emptySet(),
    downloadingTrackIds: Set<Int> = emptySet(),
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
    onAddToPlaylistClick: (TrackDto) -> Unit = {},
    onReplaceTrackClick: (TrackDto) -> Unit = {},
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onDownloadCandidate: (Map<String, Any?>, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, cb -> cb(true, null) }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var selectedCandidateFilter by remember { mutableStateOf("all") }
    val haptic = LocalHapticFeedback.current

    // Состояния аудио превью кандидатов
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingCandidateId by remember { mutableStateOf<String?>(null) }
    var downloadingCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedCandidateIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
            previewPlayer = null
        }
    }

    // При переключении вкладок останавливаем превью
    LaunchedEffect(searchTab) {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        previewingCandidateId = null
    }

    fun togglePreview(candId: String) {
        if (previewingCandidateId == candId) {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
            previewPlayer = null
            previewingCandidateId = null
            return
        }

        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null

        val previewUrl = "${serverUrl.trimEnd('/')}/api/tracks/candidates/preview?candidate_id=$candId&token=$token"
        try {
            val mp = MediaPlayer().apply {
                setDataSource(previewUrl)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    previewingCandidateId = null
                }
                setOnErrorListener { _, _, _ ->
                    previewingCandidateId = null
                    true
                }
                prepareAsync()
            }
            previewPlayer = mp
            previewingCandidateId = candId
        } catch (e: Exception) {
            e.printStackTrace()
            previewingCandidateId = null
        }
    }

    val filteredTracks = remember(tracks, searchQuery, searchResults, isSearching, selectedFilter) {
        val baseList = if (searchQuery.isNotBlank()) {
            if (searchResults.isNotEmpty()) {
                searchResults
            } else if (isSearching) {
                // Пока сервер выполняет запрос, показываем мгновенные совпадения из памяти
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
            TrackFilterMode.FLAC_ONLY -> baseList.filter { it.audioFormat.equals("flac", ignoreCase = true) }
            TrackFilterMode.LIKED -> baseList.filter { it.isLiked }
            TrackFilterMode.OFFLINE -> baseList.filter { downloadedTrackIds.contains(it.id) }
        }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // 1. Поле поиска (общее для фонотеки и глобального поиска)
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = if (searchTab == SearchTab.LIBRARY) {
                        "Поиск в медиатеке (название, артист, слова)..."
                    } else {
                        "Поиск новых треков через плагины..."
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                IconButton(onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Фокус поиска",
                        tint = if (searchQuery.isNotEmpty()) Color(0xFFD8B4FE) else Color.Gray
                    )
                }
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        onSearchQueryChange("")
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = Color.LightGray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = 8.dp, bottom = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8A2BE2),
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // 2. Две вкладки в поиске: «В медиатеке» и «Новые треки»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(Color(0xFF1E1A26), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (searchTab == SearchTab.LIBRARY) Color(0xFF8A2BE2) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchTabChange(SearchTab.LIBRARY)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = if (searchTab == SearchTab.LIBRARY) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "В медиатеке",
                        color = if (searchTab == SearchTab.LIBRARY) Color.White else Color.LightGray,
                        fontWeight = if (searchTab == SearchTab.LIBRARY) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (searchTab == SearchTab.NEW_TRACKS) Color(0xFF8A2BE2) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchTabChange(SearchTab.NEW_TRACKS)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.TravelExplore,
                        contentDescription = null,
                        tint = if (searchTab == SearchTab.NEW_TRACKS) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Новые треки",
                        color = if (searchTab == SearchTab.NEW_TRACKS) Color.White else Color.LightGray,
                        fontWeight = if (searchTab == SearchTab.NEW_TRACKS) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Индикатор выполнения серверного поиска
        val currentSearching = if (searchTab == SearchTab.LIBRARY) isSearching else isSearchingNewCandidates
        if (currentSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(vertical = 1.dp),
                color = Color(0xFF8A2BE2),
                trackColor = Color(0x338A2BE2)
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // ====================================================================
        // ВКЛАДКА 1: В МЕДИАТЕКЕ
        // ====================================================================
        if (searchTab == SearchTab.LIBRARY) {
            // Чипы фильтрации (Все, Только FLAC, Любимые, Оффлайн)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackFilterMode.entries.forEach { mode ->
                    val isSelected = selectedFilter == mode
                    val chipAccentColor = when (mode) {
                        TrackFilterMode.MOOD_ENERGY -> Color(0xFFFF512F)
                        TrackFilterMode.MOOD_CHILL -> Color(0xFF00B4DB)
                        TrackFilterMode.MOOD_HAPPY -> Color(0xFFFFB300)
                        TrackFilterMode.MOOD_SAD -> Color(0xFF5C6BC0)
                        TrackFilterMode.FLAC_ONLY -> Color(0xFF1DB954)
                        TrackFilterMode.LIKED -> Color(0xFFFF3366)
                        else -> Color(0xFF8A2BE2)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedFilter = mode
                        },
                        label = {
                            Text(
                                text = mode.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipAccentColor,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF23202A),
                            labelColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) chipAccentColor else Color(0xFF383444),
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // Строка статуса и количества
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) {
                        if (isSearching) "Поиск по всей фонотеке..." else "Найдено на сервере: ${filteredTracks.size}"
                    } else if (selectedFilter != TrackFilterMode.ALL) {
                        "${selectedFilter.label}: ${filteredTracks.size} из ${tracks.size}"
                    } else {
                        "Всего треков: ${filteredTracks.size}"
                    },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )

                if (isLoadingTracks) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF8A2BE2)
                    )
                } else {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRefresh()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Обновить медиатеку",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Список треков или пустое состояние с поддержкой свайпа для обновления (Pull-to-Refresh)
            PullToRefreshBox(
                isRefreshing = isLoadingTracks,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredTracks.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.SearchOff,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Список треков пуст",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Попробуйте перейти на вкладку «Новые треки» для поиска в сети"
                                } else {
                                    "В вашей коллекции пока нет добавленных треков"
                                },
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp)
                    ) {
                        items(
                            items = filteredTracks,
                            key = { it.id },
                            contentType = { "track_row" }
                        ) { track ->
                            val isDownloaded = downloadedTrackIds.contains(track.id)
                            val isDownloading = downloadingTrackIds.contains(track.id)

                            TrackItemRow(
                                track = track,
                                isPlayingCurrent = currentTrack?.id == track.id,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                serverUrl = serverUrl,
                                token = token,
                                onDownloadTrack = onDownloadTrack,
                                onDeleteDownloadedTrack = onDeleteDownloadedTrack,
                                onTrackClick = {
                                    try {
                                        previewPlayer?.stop()
                                        previewPlayer?.release()
                                    } catch (_: Exception) {}
                                    previewPlayer = null
                                    previewingCandidateId = null

                                    val trackIndex = filteredTracks.indexOfFirst { it.id == track.id }
                                    val upcoming = if (trackIndex != -1 && trackIndex < filteredTracks.size - 1) {
                                        filteredTracks.subList(trackIndex + 1, filteredTracks.size)
                                    } else emptyList()
                                    onTrackClick(track, upcoming)
                                },
                                onLikeTrack = onLikeTrack,
                                onAddToPlaylistClick = onAddToPlaylistClick,
                                onReplaceTrackClick = onReplaceTrackClick
                            )
                            HorizontalDivider(color = Color(0xFF1E1E1E))
                        }
                    }
                }
            }
        } else {
            // ====================================================================
            // ВКЛАДКА 2: НОВЫЕ ТРЕКИ (Глобальный поиск через подключенные плагины)
            // ====================================================================
            // Фильтры по источнику / качеству (динамически на основе найденных кандидатов)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidateFilterOptions.forEach { (key, label) ->
                    val isSelected = selectedCandidateFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedCandidateFilter = key
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8A2BE2),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF23202A),
                            labelColor = Color.LightGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFF8A2BE2) else Color(0xFF383444),
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            if (searchQuery.isBlank()) {
                // Приглашение к глобальному поиску
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Surface(
                            color = Color(0xFF231F2E),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.TravelExplore,
                                    contentDescription = null,
                                    tint = Color(0xFFBB86FC),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Глобальный поиск треков",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Введите название трека или исполнителя в поле поиска выше для поиска и загрузки через подключенные плагины сервера.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else if (isSearchingNewCandidates && newCandidates.isEmpty()) {
                // Загрузка
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF8A2BE2), strokeWidth = 3.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Поиск по подключенным плагинам сервера...",
                            color = Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (filteredCandidates.isEmpty()) {
                // Пусто
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Новых треков не найдено",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Попробуйте изменить запрос. Если поиск ничего не находит, возможно, на сервере не подключены плагины скачивания.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                // Список кандидатов
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Найдено во внешних источниках: ${filteredCandidates.size}",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCandidates, key = { it["candidate_id"] as? String ?: it.hashCode().toString() }) { cand ->
                        val cid = cand["candidate_id"] as? String ?: ""
                        val cTitle = cand["title"] as? String ?: "Без названия"
                        val cArtist = cand["artist"] as? String ?: "Неизвестный исполнитель"
                        val cSource = cand["source_name"] as? String ?: (cand["source"] as? String ?: "Источник")
                        val cFormat = (cand["format"] as? String ?: "mp3").uppercase()
                        val cBitrate = (cand["bitrate"] as? Number)?.toInt()
                        val cBitrateStr = if (cBitrate != null && cBitrate > 0) "$cBitrate kbps" else ""

                        val vType = (cand["version_type"] as? String ?: "").lowercase()
                        val isExplicit = (cand["explicit"] as? Boolean == true) || vType.contains("explicit")
                        val vLabel = cand["version_label"] as? String ?: when {
                            isExplicit -> "Explicit (18+)"
                            cFormat == "FLAC" || vType.contains("flac") -> "FLAC Lossless"
                            vType.contains("slowed") -> "Slowed"
                            vType.contains("speed") -> "Speed Up"
                            vType.contains("remix") -> "Remix"
                            else -> "Оригинал"
                        }
                        val vBadgeColor = when {
                            isExplicit -> Color(0xFFFF5252)
                            cFormat == "FLAC" || vType.contains("flac") -> Color(0xFF1DB954)
                            vType.contains("remix") || vType.contains("slowed") -> Color(0xFFE040FB)
                            else -> Color(0xFF8A2BE2)
                        }

                        val canPreview = cand["can_preview"] as? Boolean ?: true
                        val isPlayingThis = previewingCandidateId == cid
                        val isDownloadingThis = downloadingCandidateIds.contains(cid)
                        val isDownloadedThis = downloadedCandidateIds.contains(cid)

                        // Карточка найденного нового трека
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF201C28),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Бейджи источника, типа и формата
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF332B40),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = cSource,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD8B4FE),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        color = vBadgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = vLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = vBadgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFF2E2A38),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "$cFormat $cBitrateStr".trim(),
                                            fontSize = 10.sp,
                                            color = Color.LightGray,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // Название и артист
                                Text(
                                    text = cTitle,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = cArtist,
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(Modifier.height(10.dp))

                                // Кнопки действий: Прослушать превью + Скачать в фонотеку
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (canPreview) {
                                        OutlinedButton(
                                            onClick = { togglePreview(cid) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = if (isPlayingThis) Color(0xFF1DB954) else Color.White
                                            )
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = if (isPlayingThis) Color(0xFF1DB954) else Color.White
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (isPlayingThis) "Стоп" else "Слушать",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (!isDownloadingThis && !isDownloadedThis) {
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
                                        },
                                        enabled = !isDownloadingThis && !isDownloadedThis,
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isDownloadedThis) Color(0xFF1DB954) else Color(0xFF8A2BE2),
                                            disabledContainerColor = if (isDownloadedThis) Color(0xFF1DB954) else Color(0xFF4A3B66),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        if (isDownloadingThis) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Загрузка...", fontSize = 12.sp)
                                        } else if (isDownloadedThis) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("В медиатеке", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(
                                                Icons.Filled.Download,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Скачать", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItemRow(
    track: TrackDto,
    isPlayingCurrent: Boolean,
    isDownloaded: Boolean,
    serverUrl: String,
    token: String,
    isDownloading: Boolean = false,
    onDownloadTrack: ((TrackDto) -> Unit)? = null,
    onDeleteDownloadedTrack: ((TrackDto) -> Unit)? = null,
    onTrackClick: () -> Unit,
    onLikeTrack: (TrackDto) -> Unit,
    onAddToPlaylistClick: (TrackDto) -> Unit,
    onReplaceTrackClick: (TrackDto) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlayingCurrent) Color(0x228A2BE2) else Color.Transparent)
            .clickable(onClick = onTrackClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackCover(
            trackId = track.id,
            serverUrl = serverUrl,
            token = token,
            size = 48.dp,
            shapeRadius = 8.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                color = if (isPlayingCurrent) Color(0xFFBB86FC) else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist,
                    modifier = Modifier.weight(1f, fill = false),
                    color = Color.Gray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.audioFormat.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = track.audioFormat.uppercase(),
                        fontSize = 10.sp,
                        color = if (track.audioFormat.equals("flac", ignoreCase = true)) Color(0xFF1DB954) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (isDownloaded || isDownloading) {
                    Spacer(Modifier.width(6.dp))
                    com.sentinel.music.presentation.components.DownloadStatusIndicator(
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        showText = true
                    )
                }
            }
        }

        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLikeTrack(track)
            }
        ) {
            Icon(
                imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Лайк",
                tint = if (track.isLiked) Color(0xFFFF3366) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }

        TrackActionDropdown(
            track = track,
            isDownloaded = isDownloaded,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onReplaceTrackClick = onReplaceTrackClick,
            onDownloadTrack = onDownloadTrack,
            onDeleteDownloadedTrack = onDeleteDownloadedTrack
        )
    }
}
