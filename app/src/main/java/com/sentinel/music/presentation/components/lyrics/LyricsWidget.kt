package com.sentinel.music.presentation.components.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.domain.models.LyricLine
import com.sentinel.music.domain.usecase.SyncLyricsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsWidget(
    lyrics: List<LyricLine>,
    currentProgressMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onLineClick: (Long) -> Unit = {}
) {
    val syncUseCase = remember { SyncLyricsUseCase() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Плавное локальное время
    var smoothTimeMs by remember { mutableLongStateOf(currentProgressMs) }

    LaunchedEffect(currentProgressMs, isPlaying) {
        if (Math.abs(smoothTimeMs - currentProgressMs) > 400 || !isPlaying) {
            smoothTimeMs = currentProgressMs
        }
        if (isPlaying) {
            val startReal = System.currentTimeMillis()
            val initialSmooth = smoothTimeMs
            while (true) {
                val elapsed = System.currentTimeMillis() - startReal
                smoothTimeMs = initialSmooth + elapsed
                delay(80)
            }
        }
    }

    // O(log N) поиск текущей строки
    val activeIndex by remember(lyrics) {
        derivedStateOf { syncUseCase(lyrics, smoothTimeMs) }
    }

    // Автоматическая плавная прокрутка к активной строке по центру экрана
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = -250 // Центрируем строку
                )
            }
        }
    }

    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Текст песни недоступен",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyrics, key = { _, item -> item.id }) { index, line ->
            val isActive = index == activeIndex
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1.08f else 0.95f,
                animationSpec = tween(durationMillis = 300),
                label = "scale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1.0f else 0.45f,
                animationSpec = tween(durationMillis = 300),
                label = "alpha"
            )

            Text(
                text = line.text,
                fontSize = if (isActive) 24.sp else 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color.LightGray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clickable { onLineClick(line.timeMs) }
            )
        }
    }
}
