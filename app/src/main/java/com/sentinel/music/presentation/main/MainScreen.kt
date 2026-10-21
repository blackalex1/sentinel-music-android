package com.sentinel.music.presentation.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import com.sentinel.music.player.MusicService
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.components.media.DevicesDialog
import com.sentinel.music.presentation.components.media.StartupDeviceDialog
import com.sentinel.music.presentation.components.media.ExpandedPlayerModal
import com.sentinel.music.presentation.components.media.MiniPlayer
import com.sentinel.music.presentation.components.media.QueueBottomSheet
import com.sentinel.music.presentation.home.HomeScreen
import com.sentinel.music.presentation.navigation.AppBottomNavigation
import com.sentinel.music.presentation.navigation.MainTab
import com.sentinel.music.presentation.playlists.AddToPlaylistDialog
import com.sentinel.music.presentation.playlists.PlaylistsScreen
import com.sentinel.music.presentation.settings.SettingsScreen
import com.sentinel.music.presentation.stream.StreamScreen
import com.sentinel.music.presentation.tracks.ReplaceTrackDialog
import com.sentinel.music.presentation.tracks.TracksScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    api: MusicApiService,
    sessionManager: SessionManager,
    controllerProvider: () -> MediaController?,
    onLogout: () -> Unit
) {
    val serverUrl = sessionManager.serverUrl
    val token = sessionManager.token ?: ""
    val context = LocalContext.current
    val offlineManager = remember(serverUrl) { OfflineManager(context, serverUrl) }
    val lyricsRepo = remember(api, offlineManager) { LyricsRepository(context, api, offlineManager) }

    val viewModel: MainViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(api, sessionManager, offlineManager, lyricsRepo) as T
            }
        }
    )

    // Привязываем контроллер плеера к вьюмодели
    val controller = controllerProvider()
    LaunchedEffect(controller) {
        viewModel.bindController(controller)
    }

    DisposableEffect(context, viewModel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    MusicService.ACTION_NOTIFICATION_PLAY -> viewModel.play()
                    MusicService.ACTION_NOTIFICATION_PAUSE -> viewModel.pause()
                    MusicService.ACTION_NOTIFICATION_NEXT -> viewModel.playNext()
                    MusicService.ACTION_NOTIFICATION_PREV -> viewModel.playPrev()
                    MusicService.ACTION_NOTIFICATION_PLAY_PAUSE -> viewModel.playPause()
                    MusicService.ACTION_NOTIFICATION_SEEK -> {
                        val pos = intent.getLongExtra("POSITION_MS", 0L)
                        viewModel.seekTo(pos)
                    }
                    MusicService.ACTION_REACTIONS_CHANGED -> {
                        val trackId = intent.getIntExtra("TRACK_ID", -1)
                        if (trackId != -1) {
                            val isLiked = intent.getBooleanExtra("IS_LIKED", false)
                            val isDisliked = intent.getBooleanExtra("IS_DISLIKED", false)
                            viewModel.updateTrackReaction(trackId, isLiked, isDisliked)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(MusicService.ACTION_NOTIFICATION_PLAY)
            addAction(MusicService.ACTION_NOTIFICATION_PAUSE)
            addAction(MusicService.ACTION_NOTIFICATION_NEXT)
            addAction(MusicService.ACTION_NOTIFICATION_PREV)
            addAction(MusicService.ACTION_NOTIFICATION_PLAY_PAUSE)
            addAction(MusicService.ACTION_NOTIFICATION_SEEK)
            addAction(MusicService.ACTION_REACTIONS_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val state by viewModel.uiState.collectAsState()

    var isExpandedPlayerOpen by remember { mutableStateOf(false) }
    var initialShowLyrics by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<TrackDto?>(null) }
    var trackForReplace by remember { mutableStateOf<TrackDto?>(null) }
    var isQueueSheetOpen by remember { mutableStateOf(false) }

    // Проверка 401 на случай протухания токена
    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage?.contains("401") == true) {
            onLogout()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedTab.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                },
                actions = {
                    // Кнопка открытия текущей очереди треков
                    if (state.queue.isNotEmpty()) {
                        IconButton(onClick = { isQueueSheetOpen = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Очередь воспроизведения",
                                tint = Color(0xFFBB86FC)
                            )
                        }
                    }

                    // Трансляция на ПК (Sentinel Connect)
                    IconButton(onClick = { viewModel.openDevicesDialog() }) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Filled.Cast,
                                contentDescription = "Трансляция на ПК",
                                tint = if (state.activeDevice != null) {
                                    Color(0xFF1DB954)
                                } else if (state.isPcOnline) {
                                    Color(0xFFBB86FC)
                                } else {
                                    Color.Gray
                                }
                            )
                            if (state.isPcOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1DB954))
                                )
                            }
                        }
                    }

                    // Настройки приложения
                    IconButton(onClick = { viewModel.setTab(MainTab.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Настройки",
                            tint = if (state.selectedTab == MainTab.SETTINGS) Color(0xFFBB86FC) else Color.LightGray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16141C))
            )
        },
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF141218))) {
                // Закрепленный мини-плеер над табами (показываем, если не на Потоке, либо если поток не активен)
                if (state.selectedTab != MainTab.STREAM || !state.isStreamActive) {
                    MiniPlayer(
                        track = state.currentTrack,
                        isPlaying = state.isPlaying,
                        isLiked = state.isLiked,
                        currentProgressMs = state.currentProgressMs,
                        durationMs = state.durationMs,
                        serverUrl = serverUrl,
                        token = token,
                        onPlayPause = { viewModel.playPause() },
                        onLike = { viewModel.toggleLike() },
                        onClick = {
                            initialShowLyrics = false
                            isExpandedPlayerOpen = true
                        },
                        onNext = { viewModel.playNext() },
                        onPrev = { viewModel.playPrev() },
                        activeDeviceName = state.activeDevice?.name,
                        onOpenDevices = null
                    )
                }

                // 4 Навигационные вкладки
                AppBottomNavigation(
                    selectedTab = state.selectedTab,
                    onTabSelected = { tab -> viewModel.setTab(tab) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                        onAddToPlaylistClick = { trackForPlaylist = it },
                        onReplaceTrackClick = { trackForReplace = it },
                        onDownloadTrack = { viewModel.downloadTrack(it) },
                        onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) }
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
                        onOpenLyricsClick = {
                            initialShowLyrics = true
                            isExpandedPlayerOpen = true
                        }
                    )
                }
                MainTab.TRACKS -> {
                    TracksScreen(
                        tracks = state.tracks,
                        currentTrack = state.currentTrack,
                        serverUrl = serverUrl,
                        token = token,
                        offlineManager = offlineManager,
                        downloadedTrackIds = state.downloadedTrackIds,
                        downloadingTrackIds = state.downloadingTrackIds,
                        initialFilter = state.trackFilter,
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
                        onAddToPlaylistClick = { trackForPlaylist = it },
                        onReplaceTrackClick = { trackForReplace = it },
                        onDownloadTrack = { viewModel.downloadTrack(it) },
                        onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) },
                        onDownloadCandidate = { candidate, onComplete ->
                            viewModel.downloadNewCandidate(candidate) { success, message ->
                                onComplete(success, message)
                            }
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
                        onAddToPlaylistClick = { trackForPlaylist = it },
                        onReplaceTrackClick = { trackForReplace = it },
                        onDownloadTrack = { viewModel.downloadTrack(it) },
                        onDeleteDownloadedTrack = { viewModel.deleteDownloadedTrack(it) }
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

    // Полноэкранный плеер с караоке (плавно выезжает поверх всего экрана)
    AnimatedVisibility(
        visible = isExpandedPlayerOpen && state.currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.zIndex(10f)
    ) {
            ExpandedPlayerModal(
                track = state.currentTrack,
                isPlaying = state.isPlaying,
                isLiked = state.isLiked,
                isDisliked = state.isDisliked,
                currentProgressMs = state.currentProgressMs,
                durationMs = state.durationMs,
                lyrics = state.lyrics,
                serverUrl = serverUrl,
                token = token,
                initialShowLyrics = initialShowLyrics,
                repeatMode = state.repeatMode,
                isShuffleEnabled = state.isShuffleEnabled,
                isLoudnessNormalizationEnabled = state.isLoudnessNormalizationEnabled,
                loudnessTargetLufs = state.loudnessTargetLufs,
                currentLimiterMode = state.currentLimiterMode,
                equalizerState = state.equalizerState,
                isDownloaded = state.currentTrack?.let { state.downloadedTrackIds.contains(it.id) } ?: false,
                isDownloading = state.currentTrack?.let { state.downloadingTrackIds.contains(it.id) } ?: false,
                onToggleDownload = {
                    state.currentTrack?.let { track ->
                        if (state.downloadedTrackIds.contains(track.id)) {
                            viewModel.deleteDownloadedTrack(track)
                        } else {
                            viewModel.downloadTrack(track)
                        }
                    }
                },
                onDismiss = { isExpandedPlayerOpen = false },
                onPlayPause = { viewModel.playPause() },
                onPrev = { viewModel.playPrev() },
                onNext = { viewModel.playNext() },
                onLike = { viewModel.toggleLike() },
                onDislike = { viewModel.toggleDislike() },
                onToggleRepeat = { viewModel.toggleRepeatMode() },
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleLoudnessNormalization = { viewModel.setLoudnessNormalization(it) },
                onSelectLoudnessTargetLufs = { viewModel.setLoudnessTarget(it) },
                onSelectLimiterMode = { viewModel.setLimiterMode(it) },
                onToggleEqualizer = { viewModel.setEqualizerEnabled(it) },
                onSetBandLevel = { band, level -> viewModel.setEqualizerBandLevel(band, level) },
                onSelectEqualizerPreset = { viewModel.setEqualizerPreset(it) },
                onResetEqualizer = { viewModel.resetEqualizer() },
                onSetBassBoost = { viewModel.setBassBoost(it) },
                onSetVirtualizer = { viewModel.setVirtualizer(it) },
                onSeek = { seekPosMs -> viewModel.seekTo(seekPosMs) },
                onAddToPlaylistClick = {
                    trackForPlaylist = state.currentTrack
                },
                onOpenQueue = { isQueueSheetOpen = true },
                onReplaceTrack = {
                    trackForReplace = state.currentTrack
                },
                activeDeviceName = state.activeDevice?.name,
                onOpenDevices = { viewModel.openDevicesDialog() }
            )
        }

        // Диалог выбора устройства воспроизведения (Sentinel Connect)
        if (state.isDevicesDialogOpen) {
            DevicesDialog(
                availableDevices = state.availableDevices,
                activeDevice = state.activeDevice,
                onSelectDevice = { viewModel.selectPlaybackDevice(it) },
                onDismiss = { viewModel.closeDevicesDialog() }
            )
        }


        // Шторка очереди воспроизведения (Up Next)
        if (isQueueSheetOpen) {
            QueueBottomSheet(
                queue = state.queue,
                currentTrack = state.currentTrack,
                serverUrl = serverUrl,
                token = token,
                downloadedTrackIds = state.downloadedTrackIds,
                onTrackClick = { clickedTrack ->
                    val idx = state.queue.indexOfFirst { it.id == clickedTrack.id }
                    val remaining = if (idx != -1 && idx < state.queue.size - 1) {
                        state.queue.subList(idx + 1, state.queue.size)
                    } else emptyList()
                    viewModel.playTrack(clickedTrack, remaining)
                },
                onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                onClearQueue = { viewModel.clearQueue() },
                onDismiss = { isQueueSheetOpen = false }
            )
        }

        // Диалог добавления трека в плейлист
        if (trackForPlaylist != null) {
            AddToPlaylistDialog(
                track = trackForPlaylist!!,
                api = api,
                onDismiss = { trackForPlaylist = null },
                onSuccess = { trackForPlaylist = null }
            )
        }

        // Диалог "Не тот трек / Заменить"
        if (trackForReplace != null) {
            ReplaceTrackDialog(
                track = trackForReplace!!,
                api = api,
                serverUrl = serverUrl,
                token = token,
                onDismiss = { trackForReplace = null },
                onSuccess = { updatedTrack ->
                    viewModel.updateReplacedTrack(updatedTrack)
                    trackForReplace = null
                }
            )
        }
    }
}
