package com.sentinel.music.domain.models

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val id: String = java.util.UUID.randomUUID().toString()
)
