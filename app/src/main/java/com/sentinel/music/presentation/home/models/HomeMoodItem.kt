package com.sentinel.music.presentation.home.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class HomeMoodItem(
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val accentColor: Color
)

val DEFAULT_HOME_MOODS = listOf(
    HomeMoodItem(
        key = "energy",
        title = "Бодрое",
        description = "Высокий темп и мощная динамика",
        icon = Icons.Filled.LocalFireDepartment,
        gradient = listOf(Color(0xFF2E1515), Color(0xFF1F1316)),
        accentColor = Color(0xFFFF512F)
    ),
    HomeMoodItem(
        key = "chill",
        title = "Спокойное",
        description = "Плавный ритм для отдыха и фокуса",
        icon = Icons.Filled.NightsStay,
        gradient = listOf(Color(0xFF14242E), Color(0xFF131A24)),
        accentColor = Color(0xFF00B4DB)
    ),
    HomeMoodItem(
        key = "happy",
        title = "Радостное",
        description = "Мажорный вайб и светлая атмосфера",
        icon = Icons.Filled.WbSunny,
        gradient = listOf(Color(0xFF2E2414), Color(0xFF241D12)),
        accentColor = Color(0xFFFFB300)
    ),
    HomeMoodItem(
        key = "sad",
        title = "Грустное",
        description = "Глубокое и задумчивое звучание",
        icon = Icons.Filled.WaterDrop,
        gradient = listOf(Color(0xFF1B1E2E), Color(0xFF141724)),
        accentColor = Color(0xFF5C6BC0)
    )
)
