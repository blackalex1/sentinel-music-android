package com.sentinel.music.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ObserveMediaProgressUseCase {
    operator fun invoke(baseProgressMs: Long, durationMs: Long, isPlaying: Boolean): Flow<Float> = flow {
        if (durationMs <= 0) {
            emit(0f)
            return@flow
        }

        if (!isPlaying) {
            emit((baseProgressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
            return@flow
        }

        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val currentProgress = baseProgressMs + elapsed
            val ratio = (currentProgress.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            emit(ratio)
            delay(100) // 10Hz плавное обновление
        }
    }
}
