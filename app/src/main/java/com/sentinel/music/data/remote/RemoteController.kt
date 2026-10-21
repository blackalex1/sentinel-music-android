package com.sentinel.music.data.remote

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.RemoteCommandRequest
import com.sentinel.music.data.model.RemoteDeviceDto
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class RemoteController(
    private val sessionManager: SessionManager,
    private val api: MusicApiService
) {
    private val tag = "SentinelRemote"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = com.sentinel.music.data.api.NetworkClient.sharedOkHttpClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 для непрерывного WebSocket стрима
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val _devices = MutableStateFlow<List<RemoteDeviceDto>>(emptyList())
    val devices: StateFlow<List<RemoteDeviceDto>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<RemoteDeviceDto?>(null)
    val selectedDevice: StateFlow<RemoteDeviceDto?> = _selectedDevice.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _remoteStatus = MutableStateFlow<com.sentinel.music.data.model.RemoteStatusResponse?>(null)
    val remoteStatus: StateFlow<com.sentinel.music.data.model.RemoteStatusResponse?> = _remoteStatus.asStateFlow()

    fun start() {
        if (!_isConnected.value && webSocket == null) {
            connectWebSocket()
        }
        refreshDevicesHttp()
    }

    suspend fun checkRemoteStatus(): com.sentinel.music.data.model.RemoteStatusResponse? {
        return try {
            if (sessionManager.isLoggedIn) {
                val myId = sessionManager.deviceId
                val status = api.getRemoteStatus(myId)
                _remoteStatus.value = status
                _devices.value = status.safeOnlineDevices.filter { it.id != myId }
                status
            } else null
        } catch (e: Exception) {
            Log.w(tag, "Ошибка проверки статуса Sentinel Connect: ${e.message}")
            null
        }
    }

    fun stop() {
        reconnectJob?.cancel()
        reconnectJob = null
        val curWs = webSocket
        webSocket = null
        try {
            curWs?.close(1000, "App closed")
        } catch (_: Exception) {}
        _isConnected.value = false
        _devices.value = emptyList()
        _selectedDevice.value = null
    }

    fun selectDevice(device: RemoteDeviceDto?) {
        _selectedDevice.value = device
        Log.i(tag, "Выбрано устройство воспроизведения: ${device?.name ?: "Этот телефон (Локально)"}")
    }

    fun refreshDevicesHttp() {
        scope.launch {
            try {
                if (sessionManager.isLoggedIn) {
                    val resp = api.getRemoteDevices()
                    _devices.update { current ->
                        val myId = sessionManager.deviceId
                        resp.safeDevices.filter { it.id != myId }
                    }
                    // Обновляем выбранное устройство свежими данными
                    val curSelected = _selectedDevice.value
                    if (curSelected != null) {
                        val fresh = _devices.value.find { it.id == curSelected.id }
                        _selectedDevice.value = fresh
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Ошибка запроса устройств через HTTP: ${e.message}")
            }
        }
    }

    @Synchronized
    private fun connectWebSocket() {
        if (!sessionManager.isLoggedIn) return
        if (_isConnected.value && webSocket != null) return

        val serverUrl = sessionManager.serverUrl
        val token = sessionManager.token ?: return
        if (serverUrl.isBlank()) return

        reconnectJob?.cancel()
        reconnectJob = null

        val oldWs = webSocket
        webSocket = null
        try {
            oldWs?.close(1000, "Reconnecting")
        } catch (_: Exception) {}

        val myId = sessionManager.deviceId
        val modelName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val deviceName = "Смартфон ($modelName)"

        val wsUrl = serverUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .removeSuffix("/") +
                "/api/remote/ws?token=" + URLEncoder.encode(token, "UTF-8") +
                "&device_id=" + URLEncoder.encode(myId, "UTF-8") +
                "&device_name=" + URLEncoder.encode(deviceName, "UTF-8") +
                "&device_type=hybrid"

        val request = Request.Builder().url(wsUrl).build()

        val newWs = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (this@RemoteController.webSocket !== webSocket) {
                    try { webSocket.close(1000, "Stale") } catch (_: Exception) {}
                    return
                }
                Log.i(tag, "Sentinel Connect WebSocket успешно подключен!")
                _isConnected.value = true
                reconnectJob?.cancel()
                reconnectJob = null
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (this@RemoteController.webSocket !== webSocket) return
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (this@RemoteController.webSocket !== webSocket) return
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (this@RemoteController.webSocket !== webSocket) return
                _isConnected.value = false
                this@RemoteController.webSocket = null
                // Не переподключаемся при нормальном закрытии (код 1000)
                if (code != 1000) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (this@RemoteController.webSocket !== webSocket) return
                Log.w(tag, "Сбой WebSocket соединения: ${t.message}")
                _isConnected.value = false
                this@RemoteController.webSocket = null
                scheduleReconnect()
            }
        })

        webSocket = newWs
    }

    private fun scheduleReconnect() {
        if (_isConnected.value) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(5000)
            if (isActive && sessionManager.isLoggedIn && !_isConnected.value) {
                Log.d(tag, "Попытка переподключения WebSocket...")
                connectWebSocket()
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = gson.fromJson(text, JsonObject::class.java) ?: return
            val type = json.get("type")?.asString ?: return
            val myId = sessionManager.deviceId

            when (type) {
                "pong" -> {
                    // Сердечный ритм от сервера
                }
                "welcome", "devices_update" -> {
                    val devicesArray = json.getAsJsonArray("devices")
                    if (devicesArray != null) {
                        val list = mutableListOf<RemoteDeviceDto>()
                        for (el in devicesArray) {
                            try {
                                val dev = gson.fromJson(el, RemoteDeviceDto::class.java)
                                if (dev.id != myId) {
                                    list.add(dev)
                                }
                            } catch (e: Exception) {
                                Log.w(tag, "Ошибка десериализации устройства: ${e.message}")
                            }
                        }
                        _devices.value = list

                        // Синхронизируем выбранное устройство
                        val curSelected = _selectedDevice.value
                        if (curSelected != null) {
                            val fresh = list.find { it.id == curSelected.id }
                            _selectedDevice.value = fresh
                        }
                    }
                }

                "device_state" -> {
                    val devId = json.get("device_id")?.asString ?: return
                    val devObj = json.getAsJsonObject("device") ?: return
                    val updatedDev = gson.fromJson(devObj, RemoteDeviceDto::class.java)

                    _devices.update { current ->
                        current.map { if (it.id == devId) updatedDev else it }
                    }

                    if (_selectedDevice.value?.id == devId) {
                        _selectedDevice.value = updatedDev
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Ошибка разбора входящего сообщения: ${e.message}")
        }
    }

    fun sendCommand(command: String, payload: Map<String, Any?>? = null) {
        val target = _selectedDevice.value ?: return
        val myId = sessionManager.deviceId

        val msg = JsonObject().apply {
            addProperty("action", "command")
            addProperty("target_device_id", target.id)
            addProperty("command", command.uppercase())
            if (payload != null) {
                add("payload", gson.toJsonTree(payload))
            }
        }

        val jsonStr = gson.toJson(msg)
        val sentViaWs = webSocket?.send(jsonStr) ?: false

        if (!sentViaWs) {
            // Фолбэк через REST API если сокет временно недоступен
            scope.launch {
                try {
                    api.sendRemoteCommand(
                        RemoteCommandRequest(
                            targetDeviceId = target.id,
                            command = command.uppercase(),
                            payload = payload
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Ошибка отправки команды через HTTP: ${e.message}")
                }
            }
        }
    }

    fun togglePlay() = sendCommand("TOGGLE_PLAY")
    fun play() = sendCommand("PLAY")
    fun pause() = sendCommand("PAUSE")
    fun next() = sendCommand("NEXT")
    fun prev() = sendCommand("PREV")
    fun seekTo(positionMs: Long) = sendCommand("SEEK", mapOf(
        "position_seconds" to positionMs / 1000.0,
        "position_ms" to positionMs
    ))
    fun setVolume(volume: Float) = sendCommand("SET_VOLUME", mapOf("volume" to volume.coerceIn(0f, 1f)))
    fun setReaction(trackId: Int, isLiked: Boolean, isDisliked: Boolean) = sendCommand("SET_REACTION", mapOf(
        "track_id" to trackId,
        "is_liked" to isLiked,
        "is_disliked" to isDisliked
    ))
    fun playTrack(track: TrackDto, positionSeconds: Double? = null, queue: List<TrackDto>? = null, autoPlay: Boolean = true) = sendCommand(
        "PLAY_TRACK",
        mutableMapOf<String, Any?>("track" to track, "auto_play" to autoPlay).apply {
            if (positionSeconds != null) put("position_seconds", positionSeconds)
            if (queue != null) put("queue", queue)
        }
    )

    fun transferPlaybackToDevice(
        targetDeviceId: String,
        track: TrackDto,
        queue: List<TrackDto>,
        positionMs: Long,
        autoPlay: Boolean
    ) {
        val payload = mapOf(
            "track" to track,
            "queue" to queue,
            "position_ms" to positionMs,
            "position_seconds" to (positionMs / 1000.0),
            "auto_play" to autoPlay
        )
        val msg = JsonObject().apply {
            addProperty("action", "command")
            addProperty("target_device_id", targetDeviceId)
            addProperty("command", "PREPARE_TRACK")
            add("payload", gson.toJsonTree(payload))
        }

        val jsonStr = gson.toJson(msg)
        val sentViaWs = webSocket?.send(jsonStr) ?: false

        if (!sentViaWs) {
            scope.launch {
                try {
                    api.sendRemoteCommand(
                        RemoteCommandRequest(
                            targetDeviceId = targetDeviceId,
                            command = "PREPARE_TRACK",
                            payload = payload
                        )
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Ошибка передачи сессии на ПК: ${e.message}")
                }
            }
        }
    }
    fun toggleShuffle() = sendCommand("SHUFFLE")
    fun toggleRepeat() = sendCommand("REPEAT")

    fun setEqualizer(
        enabled: Boolean? = null,
        preset: String? = null,
        bandIndex: Int? = null,
        levelDb: Float? = null,
        bands: List<Float>? = null,
        bassBoost: Int? = null,
        reset: Boolean? = null
    ) {
        val payload = mutableMapOf<String, Any?>()
        if (enabled != null) payload["enabled"] = enabled
        if (preset != null) payload["preset"] = preset
        if (bandIndex != null && levelDb != null) {
            payload["band_index"] = bandIndex
            payload["level_db"] = levelDb
        }
        if (bands != null) payload["bands"] = bands
        if (bassBoost != null) payload["bass_boost"] = bassBoost
        if (reset != null) payload["reset"] = reset
        sendCommand("SET_EQUALIZER", payload)
    }

    fun setLimiter(
        mode: String? = null,
        loudnessEnabled: Boolean? = null,
        targetLufs: Float? = null
    ) {
        val payload = mutableMapOf<String, Any?>()
        if (mode != null) payload["mode"] = mode
        if (loudnessEnabled != null) payload["loudness_enabled"] = loudnessEnabled
        if (targetLufs != null) payload["target_lufs"] = targetLufs
        sendCommand("SET_LIMITER", payload)
    }

    private var lastBroadcastTime = 0L

    fun broadcastLocalState(
        isPlaying: Boolean,
        currentTrack: TrackDto?,
        positionMs: Long,
        durationMs: Long,
        queue: List<TrackDto>,
        isShuffle: Boolean = false,
        repeatMode: String = "off",
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBroadcastTime < 1000L) {
            return
        }
        lastBroadcastTime = now

        if (currentTrack == null) return

        val stateMap = mapOf(
            "is_playing" to isPlaying,
            "current_track" to currentTrack,
            "position_ms" to positionMs,
            "duration_ms" to durationMs,
            "queue" to queue.take(50),
            "volume" to 1.0,
            "is_muted" to false,
            "repeat_mode" to repeatMode,
            "is_shuffle" to isShuffle
        )

        val msg = JsonObject().apply {
            addProperty("action", "state_update")
            add("state", gson.toJsonTree(stateMap))
        }

        val jsonStr = gson.toJson(msg)
        webSocket?.send(jsonStr)
    }
}
