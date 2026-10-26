package com.sentinel.music.presentation.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.components.media.MiniPlayer
import com.sentinel.music.presentation.navigation.AppBottomNavigation
import com.sentinel.music.presentation.navigation.MainTab

@Composable
fun MainBottomBar(
    selectedTab: MainTab,
    isStreamActive: Boolean,
    currentTrack: TrackDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    currentProgressMs: Long,
    durationMs: Long,
    serverUrl: String,
    token: String,
    activeDeviceName: String?,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onPlayerClick: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(Color(0xFF141218))) {
        // Закрепленный мини-плеер над табами (показываем, если не на Потоке, либо если поток не активен)
        if (selectedTab != MainTab.STREAM || !isStreamActive) {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                isLiked = isLiked,
                currentProgressMs = currentProgressMs,
                durationMs = durationMs,
                serverUrl = serverUrl,
                token = token,
                onPlayPause = onPlayPause,
                onLike = onLike,
                onClick = onPlayerClick,
                onNext = onNext,
                onPrev = onPrev,
                activeDeviceName = activeDeviceName,
                onOpenDevices = null
            )
        }

        // 4 Навигационные вкладки
        AppBottomNavigation(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )
    }
}
