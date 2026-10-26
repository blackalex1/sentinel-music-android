package com.sentinel.music.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.RemoteDeviceDto
import com.sentinel.music.data.model.TrackDto
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sentinel.music.data.remote.RemoteController
import com.sentinel.music.data.repository.LyricsRepository
import com.sentinel.music.data.repository.OfflineManager
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.player.AudioCacheManager
import com.sentinel.music.player.EqualizerManager
import com.sentinel.music.presentation.main.modules.DownloadStorageModule
import com.sentinel.music.presentation.main.modules.DspModule
import com.sentinel.music.presentation.main.modules.LibraryLoaderModule
import com.sentinel.music.presentation.main.modules.PlaybackModule
import com.sentinel.music.presentation.main.modules.ReactionsModule
import com.sentinel.music.presentation.main.modules.RemoteModule
import com.sentinel.music.presentation.main.modules.SearchModule
import com.sentinel.music.presentation.main.modules.StreamModule
import com.sentinel.music.presentation.navigation.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Главная ViewModel экрана приложения.
 * Разделена на специализированные модули:
 * - [PlaybackModule] — воспроизведение, очереди, таймлайн, MediaController
 * - [ReactionsModule] — реакции, лайки/дизлайки
 * - [SearchModule] — локальный и глобальный поиск, загрузка кандидатов, замена трека
 * - [DownloadStorageModule] — скачивание треков, оффлайн режим, кеш
 * - [DspModule] — эквалайзер, нормализация громкости EBU R128, лимитер
 * - [RemoteModule] — Sentinel Connect, управление ПК и синхронизация системной шторки
 * - [StreamModule] — потоковое радио "Моя волна", настроения
 * - [LibraryLoaderModule] — загрузка библиотеки треков с сервера
 */
class MainViewModel(
    private val api: MusicApiService,
    private val sessionManager: SessionManager,
    private val offlineManager: OfflineManager,
    private val lyricsRepository: LyricsRepository,
    val remoteController: RemoteController = RemoteController(sessionManager, api)
) : ViewModel() {

    private val equalizerManager = EqualizerManager.getInstance(sessionManager.context)

    private val _uiState = MutableStateFlow(
        MainUiState(
            downloadOnlyOnWifi = offlineManager.downloadOnlyOnWifi,
            preloadNextTrack = offlineManager.preloadNextTrack,
            maxStreamCacheSizeMb = AudioCacheManager.getMaxCacheSizeMb(sessionManager.context),
            downloadedTrackIds = offlineManager.downloadedTrackIds.value,
            downloadingTrackIds = offlineManager.downloadingTrackIds.value,
            isLoudnessNormalizationEnabled = sessionManager.isLoudnessNormalizationEnabled,
            loudnessTargetLufs = sessionManager.loudnessTargetLufs,
            isPcPlaybackHandoffEnabled = sessionManager.isPcPlaybackHandoffEnabled,
            currentUsername = sessionManager.username ?: "",
            isCurrentUserAdmin = sessionManager.isAdmin
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 1. DSP модуль (нормализация, эквалайзер, лимитер)
    val dspModule: DspModule = DspModule(
        sessionManager = sessionManager,
        equalizerManager = equalizerManager,
        remoteController = remoteController,
        uiStateFlow = _uiState,
        getMediaController = { playbackModule.mediaController }
    )

    // 2. Модуль загрузки и управления хранилищем
    val downloadStorageModule: DownloadStorageModule = DownloadStorageModule(
        scope = viewModelScope,
        sessionManager = sessionManager,
        offlineManager = offlineManager,
        lyricsRepository = lyricsRepository,
        uiStateFlow = _uiState
    )

    // 3. Модуль удаленного воспроизведения (Sentinel Connect)
    val remoteModule: RemoteModule = RemoteModule(
        scope = viewModelScope,
        sessionManager = sessionManager,
        remoteController = remoteController,
        equalizerManager = equalizerManager,
        lyricsRepository = lyricsRepository,
        uiStateFlow = _uiState,
        getMediaController = { playbackModule.mediaController },
        onPrepareLocalPlayback = { track, queue, pos, autoPlay ->
            playbackModule.prepareLocalPlayback(track, queue, pos, autoPlay)
        }
    )

    // 4. Модуль воспроизведения и очередей
    val playbackModule: PlaybackModule = PlaybackModule(
        scope = viewModelScope,
        api = api,
        sessionManager = sessionManager,
        offlineManager = offlineManager,
        lyricsRepository = lyricsRepository,
        remoteController = remoteController,
        dspModule = dspModule,
        uiStateFlow = _uiState,
        onSyncRemoteState = { remoteModule.syncRemoteStateToService(it) },
        onStreamNext = { mood, ratio -> streamModule.startStream(mood, ratio) }
    )

    // 5. Модуль реакций (лайки / дизлайки)
    val reactionsModule: ReactionsModule = ReactionsModule(
        scope = viewModelScope,
        api = api,
        uiStateFlow = _uiState,
        getMediaController = { playbackModule.mediaController },
        onPlayNext = { playbackModule.playNext() },
        onSyncRemoteState = { remoteModule.syncRemoteStateToService() }
    )

    // 6. Модуль поиска
    val searchModule: SearchModule = SearchModule(
        scope = viewModelScope,
        api = api,
        sessionManager = sessionManager,
        offlineManager = offlineManager,
        uiStateFlow = _uiState,
        onPlayTrack = { tr, upcoming, fromStream -> playbackModule.playTrack(tr, upcoming, fromStream) },
        onRefreshStorageStats = { downloadStorageModule.refreshStorageStats() }
    )

    // 7. Модуль "Моя волна" и настроений
    val streamModule: StreamModule = StreamModule(
        scope = viewModelScope,
        api = api,
        uiStateFlow = _uiState,
        onPlayTrack = { tr, upcoming, fromStream -> playbackModule.playTrack(tr, upcoming, fromStream) }
    )

    // 8. Загрузчик библиотеки треков
    val libraryLoaderModule: LibraryLoaderModule = LibraryLoaderModule(
        scope = viewModelScope,
        api = api,
        sessionManager = sessionManager,
        uiStateFlow = _uiState
    )

    init {
        libraryLoaderModule.loadInitialData()
        playbackModule.startProgressPolling()
        remoteController.start()
        remoteController.onCommandReceived = { cmd, payload ->
            handleInboundRemoteCommand(cmd, payload)
        }

        viewModelScope.launch {
            remoteController.devices.collect { devs ->
                _uiState.update { it.copy(availableDevices = devs) }
                val currentActive = _uiState.value.activeDevice
                if (currentActive != null) {
                    val stillOnline = devs.any { it.id == currentActive.id }
                    if (!stillOnline) {
                        // ПК отключился — плавно переключаем воспроизведение на телефон с сохранением трека, позиции и очереди
                        val lastTrack = currentActive.currentTrack
                        val lastPos = currentActive.positionMs
                        val lastQueue = currentActive.safeQueue.ifEmpty { _uiState.value.queue }
                        _uiState.update { it.copy(activeDevice = null, isPlaying = false) }
                        remoteController.selectDevice(null)
                        remoteModule.syncRemoteStateToService(null)
                        if (_uiState.value.isPcPlaybackHandoffEnabled && lastTrack != null) {
                            playbackModule.prepareLocalPlayback(
                                track = lastTrack,
                                upcomingList = lastQueue,
                                startPositionMs = lastPos,
                                autoPlay = false // На паузе, ждет нажатия кнопки Play пользователем
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            val status = remoteController.checkRemoteStatus()
            val gson = com.google.gson.Gson()
            if (status != null) {
                val pc = status.pcDevice
                val isPcPlaying = status.isPcOnline && pc != null && pc.isPlaying && pc.currentTrack != null
                _uiState.update {
                    it.copy(
                        hasPc = status.hasPc,
                        isPcOnline = status.isPcOnline,
                        discoveredPc = pc,
                        showStartupDeviceChooser = false
                    )
                }
                if (isPcPlaying) {
                    val isLocalPlaying = playbackModule.mediaController?.isPlaying == true
                    if (!isLocalPlaying) {
                        remoteModule.selectPlaybackDevice(pc)
                    }
                } else if (_uiState.value.isPcPlaybackHandoffEnabled && !status.isPcOnline && _uiState.value.currentTrack == null) {
                    // ПК выключен, и на телефоне плеер пуст — восстанавливаем последнюю сессию ПК (трек, позицию, очередь)
                    val session = status.lastPlaybackSession
                    val savedTrack = sessionManager.lastTrackJson?.let {
                        try { gson.fromJson(it, TrackDto::class.java) } catch (_: Exception) { null }
                    }
                    val trackToRestore = session?.currentTrack ?: savedTrack
                    val posToRestore = maxOf(session?.positionMs ?: 0L, sessionManager.lastPositionMs)
                    val queueToRestore = session?.safeQueue?.takeIf { it.isNotEmpty() } ?: run {
                        val qType = object : com.google.gson.reflect.TypeToken<List<TrackDto>>() {}.type
                        sessionManager.lastQueueJson?.let {
                            try { gson.fromJson<List<TrackDto>>(it, qType) } catch (_: Exception) { null }
                        }
                    }
                    if (trackToRestore != null) {
                        playbackModule.prepareLocalPlayback(
                            track = trackToRestore,
                            upcomingList = queueToRestore,
                            startPositionMs = posToRestore,
                            autoPlay = false
                        )
                    }
                }
            } else if (_uiState.value.isPcPlaybackHandoffEnabled && _uiState.value.currentTrack == null) {
                val savedTrack = sessionManager.lastTrackJson?.let {
                    try { gson.fromJson(it, TrackDto::class.java) } catch (_: Exception) { null }
                }
                val qType = object : com.google.gson.reflect.TypeToken<List<TrackDto>>() {}.type
                val savedQueue: List<TrackDto>? = sessionManager.lastQueueJson?.let {
                    try { gson.fromJson(it, qType) } catch (_: Exception) { null }
                }
                if (savedTrack != null) {
                    playbackModule.prepareLocalPlayback(
                        track = savedTrack,
                        upcomingList = savedQueue,
                        startPositionMs = sessionManager.lastPositionMs,
                        autoPlay = false
                    )
                }
            }
        }

        viewModelScope.launch {
            val gson = com.google.gson.Gson()
            remoteController.selectedDevice.collect { dev ->
                _uiState.update { it.copy(activeDevice = dev) }
                if (dev != null) {
                    dev.equalizer?.let { eq ->
                        equalizerManager.applyRemoteEqualizer(eq)
                    }
                    if (dev.limiter != null) {
                        _uiState.update {
                            it.copy(
                                isLoudnessNormalizationEnabled = dev.limiter.isLoudness,
                                currentLimiterMode = dev.limiter.mode
                            )
                        }
                    } else if (dev.equalizer?.currentLimiterMode != null) {
                        _uiState.update {
                            it.copy(currentLimiterMode = dev.equalizer.currentLimiterMode)
                        }
                    }

                    if (dev.currentTrack != null) {
                        val trackChanged = _uiState.value.currentTrack?.id != dev.currentTrack.id
                        val dur = if (dev.durationMs > 0) dev.durationMs else (dev.currentTrack.duration * 1000).toLong().coerceAtLeast(1L)
                        val devQueue = dev.safeQueue
                        val finalPos = if (trackChanged || dev.isPlaying || dev.positionMs > 0L) dev.positionMs else _uiState.value.currentProgressMs
                        _uiState.update {
                            it.copy(
                                currentTrack = dev.currentTrack,
                                isPlaying = dev.isPlaying,
                                currentProgressMs = finalPos,
                                durationMs = dur,
                                queue = if (devQueue.isNotEmpty()) devQueue else it.queue,
                                isLiked = dev.currentTrack.isLiked,
                                isDisliked = dev.currentTrack.isDisliked,
                                isShuffleEnabled = dev.safeIsShuffle,
                                repeatMode = when (dev.safeRepeatMode) {
                                    "one" -> PlaybackRepeatMode.ONE
                                    "all" -> PlaybackRepeatMode.ALL
                                    else -> PlaybackRepeatMode.OFF
                                },
                                lyrics = if (trackChanged) emptyList() else it.lyrics
                            )
                        }
                        sessionManager.saveLastPlaybackSession(
                            trackJson = gson.toJson(dev.currentTrack),
                            positionMs = finalPos,
                            queueJson = if (devQueue.isNotEmpty()) gson.toJson(devQueue) else sessionManager.lastQueueJson
                        )
                        if (trackChanged) {
                            viewModelScope.launch {
                                val lyrics = lyricsRepository.getLyrics(dev.currentTrack.id)
                                if (_uiState.value.currentTrack?.id == dev.currentTrack.id) {
                                    _uiState.update { it.copy(lyrics = lyrics) }
                                }
                            }
                        }
                    }
                    remoteModule.syncRemoteStateToService(dev)
                } else {
                    remoteModule.syncRemoteStateToService(null)
                }
            }
        }

        viewModelScope.launch {
            equalizerManager.state.collect { eqState ->
                _uiState.update { it.copy(equalizerState = eqState) }
            }
        }

        viewModelScope.launch {
            offlineManager.downloadedTrackIds.collect { ids ->
                _uiState.update { it.copy(downloadedTrackIds = ids) }
            }
        }

        viewModelScope.launch {
            offlineManager.downloadingTrackIds.collect { ids ->
                _uiState.update { it.copy(downloadingTrackIds = ids) }
            }
        }

        downloadStorageModule.refreshStorageStats()
    }

    // --- Навигация и фильтры ---
    fun setTab(tab: MainTab) = _uiState.update { it.copy(selectedTab = tab) }
    fun setFilter(filter: TrackFilterMode) = _uiState.update { it.copy(trackFilter = filter) }

    // --- Воспроизведение ---
    fun bindController(controller: MediaController?) = playbackModule.bindController(controller)
    fun playTrack(track: TrackDto, upcomingList: List<TrackDto>? = null, isFromStream: Boolean = false) =
        playbackModule.playTrack(track, upcomingList, isFromStream)
    fun playTrackNext(track: TrackDto) = playbackModule.playTrackNext(track)
    fun playCandidatePreview(candidate: Map<String, Any?>) =
        playbackModule.playCandidatePreview(candidate)
    fun play() = playbackModule.play()
    fun pause() = playbackModule.pause()
    fun playPause() = playbackModule.playPause()
    fun playNext(customMood: String? = null, customRatio: Float? = null) =
        playbackModule.playNext(customMood, customRatio)
    fun playPrev() = playbackModule.playPrev()
    fun seekTo(positionMs: Long) = playbackModule.seekTo(positionMs)
    fun toggleRepeatMode() = playbackModule.toggleRepeatMode()
    fun toggleShuffleMode() = playbackModule.toggleShuffleMode()
    fun removeFromQueue(index: Int) = playbackModule.removeFromQueue(index)
    fun clearQueue() = playbackModule.clearQueue()

    // --- Реакции ---
    fun updateTrackReaction(trackId: Int, isLiked: Boolean, isDisliked: Boolean) =
        reactionsModule.updateTrackReaction(trackId, isLiked, isDisliked)
    fun toggleLike(targetTrack: TrackDto? = null) = reactionsModule.toggleLike(targetTrack)
    fun toggleDislike(targetTrack: TrackDto? = null) = reactionsModule.toggleDislike(targetTrack)

    // --- Поиск ---
    fun setSearchTab(tab: SearchTab) = searchModule.setSearchTab(tab)
    fun setSearchQuery(query: String) = searchModule.setSearchQuery(query)
    fun clearSearch() = searchModule.clearSearch()
    fun downloadNewCandidate(candidate: Map<String, Any?>, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) =
        searchModule.downloadNewCandidate(candidate, onComplete)
    fun updateReplacedTrack(updatedTrack: TrackDto) = searchModule.updateReplacedTrack(updatedTrack)

    // --- Стриминг и настроения ---
    fun startStream(customMood: String? = null, customRatio: Float? = null) =
        streamModule.startStream(customMood, customRatio)
    fun stopStream() = streamModule.stopStream()
    fun setMood(mood: String) = streamModule.setMood(mood)
    fun playMoodMix(moodKey: String) = streamModule.playMoodMix(moodKey)
    fun selectMoodInLibrary(moodKey: String) = streamModule.selectMoodInLibrary(moodKey)
    fun setDiscoveryRatio(ratio: Float) = streamModule.setDiscoveryRatio(ratio)

    // --- Оффлайн и загрузки ---
    fun toggleDownloadMode() = downloadStorageModule.toggleDownloadMode()
    fun downloadTrack(track: TrackDto, onComplete: ((Boolean) -> Unit)? = null) =
        downloadStorageModule.downloadTrack(track, onComplete)
    fun deleteDownloadedTrack(track: TrackDto) = downloadStorageModule.deleteDownloadedTrack(track)
    fun deleteTrackFromServer(
        track: TrackDto,
        deleteFile: Boolean = true,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.deleteTrack(track.id, deleteFile = deleteFile)
                }
                _uiState.update { current ->
                    current.copy(
                        tracks = current.tracks.filterNot { it.id == track.id },
                        recentTracks = current.recentTracks.filterNot { it.id == track.id },
                        searchResults = current.searchResults.filterNot { it.id == track.id },
                        queue = current.queue.filterNot { it.id == track.id }
                    )
                }
                if (offlineManager.isTrackDownloaded(track.id, track.safeAudioFormat)) {
                    deleteDownloadedTrack(track)
                }
                if (_uiState.value.currentTrack?.id == track.id) {
                    if (_uiState.value.queue.isNotEmpty()) {
                        playNext()
                    } else {
                        pause()
                        _uiState.update { it.copy(currentTrack = null) }
                    }
                }
                onComplete(true, "Трек «${track.title}» удален с сервера")
            } catch (e: retrofit2.HttpException) {
                val msg = if (e.code() == 403) {
                    "Ошибка 403: требуются права администратора для удаления с сервера"
                } else {
                    "Ошибка ${e.code()}: не удалось удалить трек"
                }
                onComplete(false, msg)
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Сбой при удалении трека")
            }
        }
    }
    fun startFullDownload(onlyLiked: Boolean = false) = downloadStorageModule.startFullDownload(onlyLiked)
    fun cancelFullDownload() = downloadStorageModule.cancelFullDownload()
    fun setDownloadOnlyOnWifi(enabled: Boolean) = downloadStorageModule.setDownloadOnlyOnWifi(enabled)
    fun setPreloadNextTrack(enabled: Boolean) = downloadStorageModule.setPreloadNextTrack(enabled)
    fun setStreamCacheSizeMb(sizeMb: Int) = downloadStorageModule.setStreamCacheSizeMb(sizeMb)
    fun clearOfflineCache() = downloadStorageModule.clearOfflineCache()
    fun clearStreamCache() = downloadStorageModule.clearStreamCache()
    fun refreshStorageStats() = downloadStorageModule.refreshStorageStats()

    // --- Обработка звука (DSP / EQ / Limiter) ---
    fun setLoudnessNormalization(enabled: Boolean) = dspModule.setLoudnessNormalization(enabled)
    fun setLoudnessTarget(targetLufs: Float) = dspModule.setLoudnessTarget(targetLufs)
    fun setLimiterMode(mode: String) = dspModule.setLimiterMode(mode)
    fun setEqualizerEnabled(enabled: Boolean) = dspModule.setEqualizerEnabled(enabled)
    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Float) = dspModule.setEqualizerBandLevel(bandIndex, levelDb)
    fun setEqualizerPreset(presetName: String) = dspModule.setEqualizerPreset(presetName)
    fun resetEqualizer() = dspModule.resetEqualizer()
    fun setBassBoost(strengthPercent: Int) = dspModule.setBassBoost(strengthPercent)
    fun setVirtualizer(strengthPercent: Int) = dspModule.setVirtualizer(strengthPercent)

    // --- Удаленное воспроизведение и выбор устройств ---
    fun openDevicesDialog() = remoteModule.openDevicesDialog()
    fun closeDevicesDialog() = remoteModule.closeDevicesDialog()
    fun selectPlaybackDevice(device: RemoteDeviceDto?) = remoteModule.selectPlaybackDevice(device)
    fun dismissStartupDeviceChooser() = remoteModule.dismissStartupDeviceChooser()
    fun chooseStartupPlaybackOnPc() = remoteModule.chooseStartupPlaybackOnPc()
    fun chooseStartupPlaybackOnPhone() = remoteModule.chooseStartupPlaybackOnPhone()
    fun setPcPlaybackHandoffEnabled(enabled: Boolean) {
        sessionManager.isPcPlaybackHandoffEnabled = enabled
        _uiState.update { it.copy(isPcPlaybackHandoffEnabled = enabled) }
    }

    // --- Загрузка данных медиатеки ---
    fun loadInitialData() = libraryLoaderModule.loadInitialData()

    companion object {
        fun calculateNormalizedVolume(
            gainDb: Double?,
            isEnabled: Boolean = true,
            targetLufs: Float = -14.0f
        ): Float = DspModule.calculateNormalizedVolume(gainDb, isEnabled, targetLufs)
    }

    private fun handleInboundRemoteCommand(cmd: String, payload: JsonObject?) {
        val upperCmd = cmd.uppercase()
        android.util.Log.i("MainViewModel", "Выполнение входящей команды управления: $upperCmd")
        when (upperCmd) {
            "TOGGLE_PLAY" -> {
                playbackModule.playPause()
            }
            "PLAY" -> {
                playbackModule.play()
            }
            "PAUSE" -> {
                playbackModule.pause()
            }
            "NEXT" -> {
                playbackModule.playNext()
            }
            "PREV" -> {
                playbackModule.playPrev()
            }
            "SEEK" -> {
                val posMs = payload?.get("position_ms")?.asLong
                    ?: (payload?.get("position_seconds")?.asDouble?.times(1000))?.toLong()
                if (posMs != null) {
                    playbackModule.seekTo(posMs)
                }
            }
            "SET_VOLUME" -> {
                val vol = payload?.get("volume")?.asFloat
                if (vol != null) {
                    playbackModule.mediaController?.volume = vol.coerceIn(0f, 1f)
                }
            }
            "SHUFFLE" -> {
                playbackModule.toggleShuffleMode()
            }
            "REPEAT" -> {
                playbackModule.toggleRepeatMode()
            }
            "PLAY_TRACK", "PREPARE_TRACK", "TRANSFER_PLAYBACK" -> {
                val trackJson = payload?.get("track")
                val autoPlay = if (upperCmd == "PLAY_TRACK") {
                    payload?.get("auto_play")?.asBoolean ?: true
                } else {
                    payload?.get("auto_play")?.asBoolean ?: false
                }
                val posMs = payload?.get("position_ms")?.asLong
                    ?: (payload?.get("position_seconds")?.asDouble?.times(1000))?.toLong()
                    ?: 0L
                val queueJson = payload?.getAsJsonArray("queue")
                val gson = Gson()
                val queue = if (queueJson != null) {
                    val qList = mutableListOf<TrackDto>()
                    for (el in queueJson) {
                        try {
                            val t = gson.fromJson(el, TrackDto::class.java)
                            if (t != null) qList.add(t)
                        } catch (_: Exception) {}
                    }
                    qList
                } else null

                if (trackJson != null) {
                    try {
                        val track = gson.fromJson(trackJson, TrackDto::class.java)
                        if (track != null) {
                            if (_uiState.value.activeDevice != null) {
                                _uiState.update { it.copy(activeDevice = null) }
                                remoteController.selectDevice(null)
                            }
                            playbackModule.prepareLocalPlayback(
                                track = track,
                                upcomingList = queue,
                                startPositionMs = posMs,
                                autoPlay = autoPlay
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Ошибка разбора трека для удаленного воспроизведения: ${e.message}")
                    }
                }
            }
            "SET_REACTION" -> {
                val trackId = payload?.get("track_id")?.asInt
                val isLiked = payload?.get("is_liked")?.asBoolean ?: false
                val isDisliked = payload?.get("is_disliked")?.asBoolean ?: false
                if (trackId != null) {
                    reactionsModule.updateTrackReaction(trackId, isLiked, isDisliked)
                }
            }
        }
    }

    override fun onCleared() {
        remoteController.stop()
        playbackModule.release()
        searchModule.cancelAll()
        downloadStorageModule.cancelAll()
        super.onCleared()
    }
}
