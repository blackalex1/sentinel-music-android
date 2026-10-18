package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class TrackDto(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double = 0.0,
    @SerializedName("audio_format") val audioFormat: String = "mp3",
    val bitrate: Int? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    val bpm: Float? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    @SerializedName("loudness_lufs") val loudnessLufs: Float? = null,
    @SerializedName("gain_db") val gainDb: Double? = null,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("is_disliked") val isDisliked: Boolean = false
) {
    fun matchesMood(mood: String): Boolean {
        if (mood.isBlank() || mood == "default" || mood == "all") return true
        val b = bpm ?: 120f
        val v = valence ?: 0.5f
        val d = danceability ?: 0.5f
        val l = loudnessLufs ?: -9f
        val e = energy ?: 0.8f
        return when (mood) {
            "energy" -> b >= 118f || l >= -7.2f || (b >= 100f && d >= 0.75f)
            "happy" -> (d >= 0.66f && b >= 100f) || (v >= 0.52f && b >= 80f)
            "sad" -> {
                val isRave = b >= 125f && d >= 0.70f
                !isRave && ((v <= 0.45f && b <= 118f && l <= -7.0f) || (b <= 95f && d <= 0.65f && v <= 0.50f))
            }
            "chill" -> l <= -7.5f && ((l <= -10.2f && b <= 115f) || (b <= 92f && d <= 0.60f) || e <= 0.65f)
            else -> true
        }
    }
    fun getCoverUrl(baseUrl: String, token: String = "", size: Int? = null): String {
        val clean = baseUrl.trim().removeSuffix("/")
        val base = "$clean/api/covers/$id"
        val params = mutableListOf<String>()
        if (token.isNotBlank()) params.add("token=$token")
        if (size != null && size > 0) params.add("size=$size")
        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }
}

data class ArtistDto(
    val id: Int,
    val name: String,
    val trackCount: Int = 0
)

data class AlbumDto(
    val id: Int,
    val title: String,
    val artist: String,
    val year: Int?
)
