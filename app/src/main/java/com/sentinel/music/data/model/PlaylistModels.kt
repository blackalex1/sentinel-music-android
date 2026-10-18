package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class PlaylistDto(
    val id: Int,
    val title: String,
    val description: String?,
    @SerializedName("track_count") val trackCount: Int = 0
)

data class PlaylistDetailsDto(
    val id: Int,
    val title: String,
    val description: String?,
    val tracks: List<TrackDto> = emptyList()
)

data class CreatePlaylistRequest(
    val title: String,
    val description: String? = null
)

data class AddTrackToPlaylistRequest(
    @SerializedName("track_id") val trackId: Int
)

data class StreamOptionDto(
    val id: String,
    val name: String,
    val description: String? = null
)
