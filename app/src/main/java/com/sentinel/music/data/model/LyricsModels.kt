package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class LyricItemDto(
    @SerializedName("time_ms") val timeMs: Long,
    val text: String
)

data class LyricsResponse(
    val synced: Boolean,
    val lines: List<LyricItemDto>,
    val raw: String
)
