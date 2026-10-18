package com.sentinel.music.presentation.components.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.domain.models.LyricLine
import com.sentinel.music.player.EqualizerState
import com.sentinel.music.presentation.components.lyrics.LyricsWidget
import com.sentinel.music.presentation.main.PlaybackRepeatMode

@Composable
fun ExpandedPlayerModal(
    track: TrackDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    currentProgressMs: Long,
    durationMs: Long,
    lyrics: List<LyricLine>,
    serverUrl: String,
    token: String,
    initialShowLyrics: Boolean = false,
    repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    isShuffleEnabled: Boolean = false,
    isLoudnessNormalizationEnabled: Boolean = true,
    loudnessTargetLufs: Float = -14.0f,
    currentLimiterMode: String = "soft",
    equalizerState: EqualizerState = EqualizerState(),
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onToggleRepeat: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleLoudnessNormalization: (Boolean) -> Unit = {},
    onSelectLoudnessTargetLufs: (Float) -> Unit = {},
    onSelectLimiterMode: (String) -> Unit = {},
    onToggleEqualizer: (Boolean) -> Unit = {},
    onSetBandLevel: (Int, Float) -> Unit = { _, _ -> },
    onSelectEqualizerPreset: (String) -> Unit = {},
    onResetEqualizer: () -> Unit = {},
    onSetBassBoost: (Int) -> Unit = {},
    onSetVirtualizer: (Int) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    onSeek: (Long) -> Unit,
    onAddToPlaylistClick: () -> Unit = {},
    onOpenQueue: (() -> Unit)? = null,
    onReplaceTrack: (() -> Unit)? = null,
    activeDeviceName: String? = null,
    onOpenDevices: (() -> Unit)? = null
) {
    if (track == null) return

    val haptic = LocalHapticFeedback.current
    var showLyrics by remember(track.id, initialShowLyrics) { mutableStateOf(initialShowLyrics) }
    var showLoudnessDialog by remember { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121016)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF2B183E),
                            Color(0xFF16121D),
                            Color(0xFF0E0C12)
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 28.dp)
        ) {
            // 1. Верхняя панель (Свернуть + Формат + Очередь + Заменить + Текст/Обложка)
            PlayerHeader(
                audioFormat = track.audioFormat,
                showLyrics = showLyrics,
                onDismiss = onDismiss,
                onToggleLyrics = { showLyrics = !showLyrics },
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onToggleDownload = onToggleDownload,
                onOpenQueue = onOpenQueue,
                activeDeviceName = activeDeviceName,
                onOpenDevices = onOpenDevices
            )

            Spacer(Modifier.height(8.dp))

            // 2. Центральная зона: Караоке-текст или Кликабельная обложка
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(targetState = showLyrics, label = "PlayerViewMode") { isLyricsMode ->
                    if (isLyricsMode) {
                        LyricsWidget(
                            lyrics = lyrics,
                            currentProgressMs = currentProgressMs,
                            isPlaying = isPlaying,
                            onLineClick = onSeek
                        )
                    } else {
                        PlayerCoverView(
                            track = track,
                            serverUrl = serverUrl,
                            token = token,
                            onOpenLyrics = { showLyrics = true },
                            onNext = onNext,
                            onPrev = onPrev
                        )
                    }
                }
            }

            // 3. Панель действий: Дизлайк, Добавить в плейлист, Заменить трек, Настройки звука, Лайк
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDislike()
                    }
                ) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Не нравится",
                        tint = if (isDisliked) Color(0xFFFF5252) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onAddToPlaylistClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Добавить в плейлист",
                        tint = Color.LightGray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Кнопка замены трека (Не тот трек / Заменить версию)
                if (onReplaceTrack != null) {
                    IconButton(onClick = onReplaceTrack) {
                        Icon(
                            imageVector = Icons.Filled.FindReplace,
                            contentDescription = "Не тот трек / Заменить версию",
                            tint = Color.LightGray,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Кнопка подстройки звука (LUFS нормализация и эквалайзер)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showLoudnessDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Настройки звука: громкость LUFS и эквалайзер",
                        tint = if (isLoudnessNormalizationEnabled || equalizerState.isEnabled) Color(0xFF8A2BE2) else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLike()
                    }
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Нравится",
                        tint = if (isLiked) Color(0xFFFF3366) else Color.Gray,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 4. Плавный прогресс-бар с промоткой
            SmoothProgressBar(
                baseProgressMs = currentProgressMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onSeek = onSeek
            )

            Spacer(Modifier.height(10.dp))

            // 5. Кнопки управления воспроизведением, перемешиванием и повтором
            MediaControls(
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                onPlayPause = onPlayPause,
                onPrev = onPrev,
                onNext = onNext,
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLoudnessDialog) {
        LoudnessDialog(
            isEnabled = isLoudnessNormalizationEnabled,
            currentTargetLufs = loudnessTargetLufs,
            currentTrack = track,
            onToggleEnabled = onToggleLoudnessNormalization,
            onSelectTargetLufs = onSelectLoudnessTargetLufs,
            currentLimiterMode = currentLimiterMode,
            onSelectLimiterMode = onSelectLimiterMode,
            equalizerState = equalizerState,
            onToggleEqualizer = onToggleEqualizer,
            onSetBandLevel = onSetBandLevel,
            onSelectPreset = onSelectEqualizerPreset,
            onResetEqualizer = onResetEqualizer,
            onSetBassBoost = onSetBassBoost,
            onSetVirtualizer = onSetVirtualizer,
            onDismiss = { showLoudnessDialog = false }
        )
    }
}
