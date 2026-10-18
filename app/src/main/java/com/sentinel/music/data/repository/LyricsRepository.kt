package com.sentinel.music.data.repository

import android.content.Context
import com.google.gson.Gson
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.api.LyricsResponse
import com.sentinel.music.domain.models.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LyricsRepository(
    private val context: Context,
    private val api: MusicApiService,
    private val offlineManager: OfflineManager,
    private val gson: Gson = Gson()
) {
    // Оперативная память (для текущей сессии без записи на диск)
    private val memoryCache = ConcurrentHashMap<Int, List<LyricLine>>()

    private val lyricsOfflineDir: File by lazy {
        File(context.filesDir, "offline_lyrics").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Получает текст песни:
     * - Если режим скачивания ВЫКЛЮЧЕН: прямой стриминг текста с сервера (хранится только в RAM, на диск не пишется).
     * - Если режим скачивания ВКЛЮЧЕН: проверяется и сохраняется на постоянный диск телефона.
     */
    suspend fun getLyrics(trackId: Int): List<LyricLine> = withContext(Dispatchers.IO) {
        // 1. Проверяем оперативную память сессии
        memoryCache[trackId]?.let { return@withContext it }

        val cacheFile = File(lyricsOfflineDir, "$trackId.json")

        // 2. Если включен режим скачивания, проверяем локальный диск телефона
        if (offlineManager.isDownloadModeEnabled && cacheFile.exists()) {
            try {
                val json = cacheFile.readText()
                val cachedResponse = gson.fromJson(json, LyricsResponse::class.java)
                if (cachedResponse != null && cachedResponse.lines.isNotEmpty()) {
                    val result = cachedResponse.lines.map { LyricLine(timeMs = it.timeMs, text = it.text) }
                    memoryCache[trackId] = result
                    return@withContext result
                }
            } catch (e: Exception) {
                cacheFile.delete()
            }
        }

        // 3. Стриминг текста напрямую с сервера
        try {
            val response = api.getLyrics(trackId)
            val result = if (response.lines.isNotEmpty()) {
                response.lines.map { LyricLine(timeMs = it.timeMs, text = it.text) }
            } else if (!response.raw.isNullOrBlank()) {
                response.raw.lines()
                    .filter { it.isNotBlank() }
                    .mapIndexed { idx, line ->
                        LyricLine(timeMs = idx * 4000L, text = line.trim())
                    }
            } else {
                emptyList()
            }

            if (result.isNotEmpty()) {
                memoryCache[trackId] = result

                // Сохраняем на диск ТОЛЬКО если пользователь включил режим скачивания!
                if (offlineManager.isDownloadModeEnabled) {
                    cacheFile.writeText(gson.toJson(response))
                }

                return@withContext result
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emptyList()
    }
}
