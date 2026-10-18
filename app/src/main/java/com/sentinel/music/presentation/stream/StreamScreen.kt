package com.sentinel.music.presentation.stream

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.stream.components.StreamControls
import com.sentinel.music.presentation.stream.components.StreamHeader
import com.sentinel.music.presentation.stream.components.StreamSettingsCard
import com.sentinel.music.presentation.stream.models.STREAM_MOODS

@Composable
fun StreamScreen(
    currentTrack: TrackDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    selectedMood: String,
    selectedDiscoveryRatio: Float,
    serverUrl: String,
    token: String,
    onMoodSelected: (String) -> Unit,
    onDiscoveryRatioSelected: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onNextStreamTrack: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenLyricsClick: () -> Unit,
    isStreamActive: Boolean = false,
    onStartStream: (() -> Unit)? = null
) {
    val activeMoodObj = remember(selectedMood) {
        STREAM_MOODS.find { it.id == selectedMood } ?: STREAM_MOODS.first()
    }

    val animatedTopGlow by animateColorAsState(
        targetValue = activeMoodObj.accentColor.copy(alpha = 0.28f),
        animationSpec = tween(durationMillis = 600),
        label = "GlowAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedTopGlow,
                        Color(0xFF101016),
                        Color(0xFF09090C)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (isStreamActive) {
                // 1. Верхняя секция: статус активного потока, обложка, инфо о треке
                StreamHeader(
                    currentTrack = currentTrack,
                    accentColor = activeMoodObj.accentColor,
                    serverUrl = serverUrl,
                    token = token,
                    onNextStreamTrack = onNextStreamTrack
                )

                Spacer(Modifier.height(14.dp))

                // 2. Кнопки управления воспроизведением и вызов караоке
                StreamControls(
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    isDisliked = isDisliked,
                    onPlayPause = onPlayPause,
                    onNextStreamTrack = onNextStreamTrack,
                    onLike = onLike,
                    onDislike = onDislike,
                    onOpenLyricsClick = onOpenLyricsClick
                )

                Spacer(Modifier.height(14.dp))

                // 3. Панель настроек (Настроение + Новая музыка)
                StreamSettingsCard(
                    selectedMood = selectedMood,
                    selectedDiscoveryRatio = selectedDiscoveryRatio,
                    onMoodSelected = onMoodSelected,
                    onDiscoveryRatioSelected = onDiscoveryRatioSelected
                )
            } else {
                // Поток не активен - показываем стильный лаунчпад Потока с кнопкой запуска
                StreamInactiveView(
                    accentColor = activeMoodObj.accentColor,
                    selectedMood = selectedMood,
                    selectedDiscoveryRatio = selectedDiscoveryRatio,
                    onMoodSelected = onMoodSelected,
                    onDiscoveryRatioSelected = onDiscoveryRatioSelected,
                    onStartStream = { onStartStream?.invoke() }
                )
            }
        }
    }
}

@Composable
private fun StreamInactiveView(
    accentColor: Color,
    selectedMood: String,
    selectedDiscoveryRatio: Float,
    onMoodSelected: (String) -> Unit,
    onDiscoveryRatioSelected: (Float) -> Unit,
    onStartStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "StreamPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Spacer(Modifier.height(24.dp))

        // Пульсирующая сфера Потока
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )

            Surface(
                modifier = Modifier.size(125.dp),
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.18f),
                border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.6f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Умный Поток",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Умный бесконечный поток музыки,\nподобранный под ваш персональный вкус",
            fontSize = 13.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onStartStream,
            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
            shape = RoundedCornerShape(26.dp),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Запустить Поток",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Настройки настроения и соотношения перед стартом
        StreamSettingsCard(
            selectedMood = selectedMood,
            selectedDiscoveryRatio = selectedDiscoveryRatio,
            onMoodSelected = onMoodSelected,
            onDiscoveryRatioSelected = onDiscoveryRatioSelected
        )
    }
}
