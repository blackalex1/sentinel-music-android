package com.sentinel.music.presentation.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.main.components.MainBottomBar
import com.sentinel.music.presentation.main.components.MainDialogsHost
import com.sentinel.music.presentation.main.components.MainNotificationReceiver
import com.sentinel.music.presentation.main.components.MainTabContent
import com.sentinel.music.presentation.main.components.MainTopAppBar
import com.sentinel.music.presentation.navigation.MainTab

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

    // Слушатель системных уведомлений плеера
    MainNotificationReceiver(viewModel = viewModel)

    val state by viewModel.uiState.collectAsState()

    var isExpandedPlayerOpen by remember { mutableStateOf(false) }
    var initialShowLyrics by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<TrackDto?>(null) }
    var trackForReplace by remember { mutableStateOf<TrackDto?>(null) }
    var trackForDelete by remember { mutableStateOf<TrackDto?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }
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
                MainTopAppBar(
                    selectedTab = state.selectedTab,
                    isSearchVisible = isSearchVisible,
                    isSearchActive = state.trackSearchQuery.isNotBlank(),
                    isQueueAvailable = state.queue.isNotEmpty(),
                    activeDevice = state.activeDevice,
                    isPcOnline = state.isPcOnline,
                    onSearchClick = {
                        if (state.selectedTab != MainTab.TRACKS) {
                            viewModel.setTab(MainTab.TRACKS)
                            isSearchVisible = true
                        } else {
                            if (!isSearchVisible) {
                                isSearchVisible = true
                            } else {
                                isSearchVisible = false
                                viewModel.setSearchQuery("")
                            }
                        }
                    },
                    onQueueClick = { isQueueSheetOpen = true },
                    onCastClick = { viewModel.openDevicesDialog() },
                    onSettingsClick = { viewModel.setTab(MainTab.SETTINGS) }
                )
            },
            bottomBar = {
                MainBottomBar(
                    selectedTab = state.selectedTab,
                    isStreamActive = state.isStreamActive,
                    currentTrack = state.currentTrack,
                    isPlaying = state.isPlaying,
                    isLiked = state.isLiked,
                    currentProgressMs = state.currentProgressMs,
                    durationMs = state.durationMs,
                    serverUrl = serverUrl,
                    token = token,
                    activeDeviceName = state.activeDevice?.name,
                    onPlayPause = { viewModel.playPause() },
                    onLike = { viewModel.toggleLike() },
                    onPlayerClick = {
                        initialShowLyrics = false
                        isExpandedPlayerOpen = true
                    },
                    onNext = { viewModel.playNext() },
                    onPrev = { viewModel.playPrev() },
                    onTabSelected = { viewModel.setTab(it) }
                )
            }
        ) { paddingValues ->
            MainTabContent(
                state = state,
                viewModel = viewModel,
                sessionManager = sessionManager,
                serverUrl = serverUrl,
                token = token,
                api = api,
                offlineManager = offlineManager,
                isSearchVisible = isSearchVisible,
                onCloseSearch = {
                    isSearchVisible = false
                    viewModel.setSearchQuery("")
                },
                onHideSearch = {
                    isSearchVisible = false
                },
                onOpenLyrics = {
                    initialShowLyrics = true
                    isExpandedPlayerOpen = true
                },
                onSelectTrackForPlaylist = { trackForPlaylist = it },
                onSelectTrackForReplace = { trackForReplace = it },
                onSelectTrackForDelete = { trackForDelete = it },
                onLogout = onLogout,
                modifier = Modifier.padding(paddingValues)
            )
        }

        MainDialogsHost(
            state = state,
            viewModel = viewModel,
            serverUrl = serverUrl,
            token = token,
            api = api,
            isExpandedPlayerOpen = isExpandedPlayerOpen,
            initialShowLyrics = initialShowLyrics,
            onDismissExpandedPlayer = { isExpandedPlayerOpen = false },
            isQueueSheetOpen = isQueueSheetOpen,
            onDismissQueueSheet = { isQueueSheetOpen = false },
            trackForPlaylist = trackForPlaylist,
            onDismissPlaylistDialog = { trackForPlaylist = null },
            trackForReplace = trackForReplace,
            onDismissReplaceDialog = { trackForReplace = null },
            trackForDelete = trackForDelete,
            onDismissDeleteDialog = { trackForDelete = null },
            onOpenQueue = { isQueueSheetOpen = true },
            onAddToPlaylistClick = { trackForPlaylist = state.currentTrack },
            onReplaceTrack = { trackForReplace = state.currentTrack }
        )
    }
}
