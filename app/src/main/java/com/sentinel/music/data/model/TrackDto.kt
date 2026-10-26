package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class TrackDto(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double = 0.0,
    @SerializedName("audio_format") val audioFormat: String? = "mp3",
    val bitrate: Int? = null,
    @SerializedName("file_size") val fileSize: Long? = null,
    val bpm: Float? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    @SerializedName("loudness_lufs") val loudnessLufs: Float? = null,
    @SerializedName("gain_db") val gainDb: Double? = null,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("is_disliked") val isDisliked: Boolean = false,
    val isPreview: Boolean = false,
    val candidateId: String? = null,
    val coverUrl: String? = null
) {
    val safeAudioFormat: String get() = audioFormat?.takeIf { it.isNotBlank() } ?: "mp3"
    val safeTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "Без названия"
    val safeArtist: String get() = artist?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель"

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
        if (!coverUrl.isNullOrBlank()) return coverUrl
        val clean = baseUrl.trim().removeSuffix("/")
        val base = "$clean/api/covers/$id"
        val params = mutableListOf<String>()
        if (token.isNotBlank()) params.add("token=$token")
        if (size != null && size > 0) params.add("size=$size")
        return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
    }
}

/**
 * Создает TrackDto из произвольного словаря кандидата поисковых плагинов без жестких зависимостей
 */
fun candidateToTrackDto(candidate: Map<String, Any?>): TrackDto {
    val cid = (candidate["candidate_id"] ?: candidate["id"] ?: candidate["url"] ?: "").toString()
    val hash = cid.hashCode().let { if (it == 0) 1 else it }
    val previewId = -kotlin.math.abs(hash)
    val title = (candidate["title"] as? String)?.takeIf { it.isNotBlank() } ?: "Без названия"
    val artist = (candidate["artist"] as? String)?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель"
    val album = candidate["album"] as? String
        ?: candidate["source_name"] as? String
        ?: candidate["source"] as? String
        ?: "Онлайн поиск"
    val dur = when (val d = candidate["duration"]) {
        is Number -> d.toDouble()
        is String -> d.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    val format = (candidate["format"] as? String ?: candidate["audio_format"] as? String)?.lowercase() ?: "mp3"
    val bitrate = (candidate["bitrate"] as? Number)?.toInt()
    val coverUrl = candidate["cover_url"] as? String
        ?: candidate["cover"] as? String
        ?: candidate["artwork"] as? String

    return TrackDto(
        id = previewId,
        title = title,
        artist = artist,
        album = album,
        duration = dur,
        audioFormat = format,
        bitrate = bitrate,
        isPreview = true,
        candidateId = cid,
        coverUrl = coverUrl
    )
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
