package com.sentinel.music.presentation.components.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sentinel.music.presentation.main.PlaybackRepeatMode

@Composable
fun MediaControls(
    isPlaying: Boolean,
    repeatMode: PlaybackRepeatMode,
    isShuffleEnabled: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // 1. Перемешивание (Shuffle / Remix)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(44.dp)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleShuffle()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = if (isShuffleEnabled) "Перемешивание включено" else "Перемешивание выключено",
                    tint = if (isShuffleEnabled) Color(0xFFD8B4FE) else Color(0xFF71717A),
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isShuffleEnabled) Color(0xFFD8B4FE) else Color.Transparent)
            )
        }

        // 2. Предыдущий трек
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Предыдущий трек",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // 3. Воспроизведение / Пауза
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF8A2BE2),
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
                modifier = Modifier.size(38.dp)
            )
        }

        // 4. Следующий трек
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Следующий трек",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // 5. Режимы повтора (Выкл -> Повторять все -> Повторять один)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.width(44.dp)
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleRepeat()
                }
            ) {
                Icon(
                    imageVector = if (repeatMode == PlaybackRepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = repeatMode.title,
                    tint = if (repeatMode != PlaybackRepeatMode.OFF) Color(0xFFD8B4FE) else Color(0xFF71717A),
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (repeatMode != PlaybackRepeatMode.OFF) Color(0xFFD8B4FE) else Color.Transparent)
            )
        }
    }
}
