package com.sentinel.music.presentation.stream.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class StreamMood(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val gradientColors: List<Color>,
    val accentColor: Color
)

data class StreamDiscoveryOption(
    val ratio: Float,
    val title: String,
    val badge: String
)

val STREAM_MOODS = listOf(
    StreamMood(
        id = "default",
        title = "Все подряд",
        icon = Icons.Filled.AutoAwesome,
        gradientColors = listOf(Color(0xFF8A2BE2), Color(0xFF4A00E0)),
        accentColor = Color(0xFF8A2BE2)
    ),
    StreamMood(
        id = "energy",
        title = "Бодрое",
        icon = Icons.Filled.LocalFireDepartment,
        gradientColors = listOf(Color(0xFFFF512F), Color(0xFFDD2476)),
        accentColor = Color(0xFFFF512F)
    ),
    StreamMood(
        id = "chill",
        title = "Спокойное",
        icon = Icons.Filled.NightsStay,
        gradientColors = listOf(Color(0xFF2E3192), Color(0xFF1BFFFF)),
        accentColor = Color(0xFF2E3192)
    ),
    StreamMood(
        id = "happy",
        title = "Весёлое",
        icon = Icons.Filled.WbSunny,
        gradientColors = listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        accentColor = Color(0xFFF7971E)
    ),
    StreamMood(
        id = "sad",
        title = "Грустное",
        icon = Icons.Filled.WaterDrop,
        gradientColors = listOf(Color(0xFF4B6CB7), Color(0xFF182848)),
        accentColor = Color(0xFF4B6CB7)
    ),
    StreamMood(
        id = "focus",
        title = "Концентрация",
        icon = Icons.Filled.Psychology,
        gradientColors = listOf(Color(0xFF00B4DB), Color(0xFF0083B0)),
        accentColor = Color(0xFF00B4DB)
    )
)

val STREAM_DISCOVERY_OPTIONS = listOf(
    StreamDiscoveryOption(0.0f, "Только знакомое", "0%"),
    StreamDiscoveryOption(0.1f, "Редко", "10%"),
    StreamDiscoveryOption(0.2f, "Иногда", "20%"),
    StreamDiscoveryOption(0.5f, "Часто", "50%"),
    StreamDiscoveryOption(1.0f, "Сплошные новинки", "100%")
)
