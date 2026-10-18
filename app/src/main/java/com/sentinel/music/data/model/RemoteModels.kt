package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

/**
 * Модели данных Sentinel Connect (Удаленное управление воспроизведением).
 */
data class RemoteDeviceDto(
    val id: String,
    val name: String,
    val type: String = "player",
    @SerializedName("can_play") val canPlay: Boolean = true,
    @SerializedName("is_playing") val isPlaying: Boolean = false,
    @SerializedName("current_track") val currentTrack: TrackDto? = null,
    @SerializedName("position_ms") val positionMs: Long = 0L,
    @SerializedName("duration_ms") val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    @SerializedName("is_muted") val isMuted: Boolean = false,
    @SerializedName("repeat_mode") val repeatMode: String = "off",
    @SerializedName("is_shuffle") val isShuffle: Boolean = false,
    @SerializedName("last_seen") val lastSeen: Double = 0.0,
    val equalizer: RemoteEqualizerDto? = null,
    val limiter: RemoteLimiterDto? = null,
    val queue: List<TrackDto>? = null
) {
    val safeQueue: List<TrackDto> get() = queue ?: emptyList()
}

data class RemotePlaybackSessionDto(
    @SerializedName("current_track") val currentTrack: TrackDto? = null,
    @SerializedName("position_ms") val positionMs: Long = 0L,
    @SerializedName("duration_ms") val durationMs: Long = 0L,
    val queue: List<TrackDto>? = null,
    @SerializedName("device_name") val deviceName: String = "",
    @SerializedName("saved_at") val savedAt: Double = 0.0
) {
    val safeQueue: List<TrackDto> get() = queue ?: emptyList()
}

data class RemoteEqualizerDto(
    @SerializedName("isEnabled") val isEnabled: Boolean = false,
    @SerializedName("currentPreset") val currentPreset: String? = "Обычный (Flat)",
    @SerializedName("bandLevels") val bandLevels: List<Float>? = null,
    @SerializedName("bassBoostPercent") val bassBoostPercent: Int = 0,
    @SerializedName("currentLimiterMode") val currentLimiterMode: String? = "soft"
) {
    val safeBandLevels: List<Float> get() = bandLevels ?: emptyList()
    val safePreset: String get() = currentPreset ?: "Обычный (Flat)"
}

data class RemoteLimiterDto(
    val mode: String = "soft",
    @SerializedName("is_loudness") val isLoudness: Boolean = true
)

data class RemoteDevicesResponse(
    val devices: List<RemoteDeviceDto>? = null
) {
    val safeDevices: List<RemoteDeviceDto> get() = devices ?: emptyList()
}

data class RemoteCommandRequest(
    @SerializedName("target_device_id") val targetDeviceId: String,
    val command: String,
    val payload: Map<String, Any?>? = null
)

data class RemoteStatusResponse(
    @SerializedName("has_pc") val hasPc: Boolean = false,
    @SerializedName("is_pc_online") val isPcOnline: Boolean = false,
    @SerializedName("pc_device") val pcDevice: RemoteDeviceDto? = null,
    @SerializedName("online_devices") val onlineDevices: List<RemoteDeviceDto>? = null,
    @SerializedName("last_playback_session") val lastPlaybackSession: RemotePlaybackSessionDto? = null
) {
    val safeOnlineDevices: List<RemoteDeviceDto> get() = onlineDevices ?: emptyList()
}
