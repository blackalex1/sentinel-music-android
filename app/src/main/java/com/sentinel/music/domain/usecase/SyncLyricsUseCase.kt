package com.sentinel.music.domain.usecase

import com.sentinel.music.domain.models.LyricLine

class SyncLyricsUseCase {
    /**
     * Возвращает индекс текущей строки с помощью бинарного поиска O(log N).
     * Предотвращает микрофризы интерфейса при скролле караоке.
     */
    operator fun invoke(lines: List<LyricLine>, currentTimeMs: Long): Int {
        if (lines.isEmpty() || currentTimeMs < lines.first().timeMs) return -1
        
        var low = 0
        var high = lines.size - 1
        var bestMatch = -1

        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= currentTimeMs) {
                bestMatch = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        
        return bestMatch
    }
}
