package com.sentinel.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.sentinel.music.data.model.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.sentinel.music.data.api.NetworkClient
import java.io.File
import java.io.FileOutputStream

class OfflineManager(
    private val context: Context,
    private val serverUrl: String,
    private val okHttpClient: OkHttpClient = NetworkClient.sharedOkHttpClient
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)

    /**
     * Режим скачивания на телефон:
     * - false (по умолчанию): чистый стриминг с сервера, на телефон ничего не сохраняется.
     * - true: треки сохраняются на телефон для офлайн прослушивания.
     */
    var isDownloadModeEnabled: Boolean
        get() = prefs.getBoolean("download_mode_enabled", false)
        set(value) = prefs.edit().putBoolean("download_mode_enabled", value).apply()

    var downloadOnlyOnWifi: Boolean
        get() = prefs.getBoolean("download_only_on_wifi", false)
        set(value) = prefs.edit().putBoolean("download_only_on_wifi", value).apply()

    var preloadNextTrack: Boolean
        get() = prefs.getBoolean("preload_next_track", true)
        set(value) = prefs.edit().putBoolean("preload_next_track", value).apply()

    private val offlineAudioDir: File by lazy {
        File(context.filesDir, "offline_tracks").apply { if (!exists()) mkdirs() }
    }

    private val offlineLyricsDir: File by lazy {
        File(context.filesDir, "offline_lyrics").apply { if (!exists()) mkdirs() }
    }

    private val downloadedCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val _downloadedTrackIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<Int>> = _downloadedTrackIds.asStateFlow()

    private val _downloadingTrackIds = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<Int>> = _downloadingTrackIds.asStateFlow()

    init {
        refreshDownloadedCache()
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (_: Exception) {
            true
        }
    }

    fun refreshDownloadedCache() {
        try {
            downloadedCache.clear()
            val ids = mutableSetOf<Int>()
            offlineAudioDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    downloadedCache[file.name] = true
                    val idPart = file.name.substringBefore('.')
                    idPart.toIntOrNull()?.let { ids.add(it) }
                }
            }
            _downloadedTrackIds.value = ids
        } catch (_: Exception) {}
    }

    fun getLocalAudioFile(trackId: Int, format: String? = null): File {
        val safeFmt = format?.takeIf { it.isNotBlank() } ?: "mp3"
        return File(offlineAudioDir, "$trackId.$safeFmt")
    }

    fun isTrackDownloaded(trackId: Int, format: String? = null): Boolean {
        val safeFmt = format?.takeIf { it.isNotBlank() }
        if (!safeFmt.isNullOrEmpty()) {
            val fileName = "$trackId.$safeFmt"
            if (downloadedCache[fileName] == true) return true
        }
        return _downloadedTrackIds.value.contains(trackId)
    }

    /**
     * Возвращает источник воспроизведения:
     * - Если трек уже сохранен на телефоне: локальный файл (без интернета).
     * - Иначе: защищенный стриминг с сервера с токеном авторизации.
     */
    fun getPlaybackUri(trackId: Int, format: String? = null, token: String = ""): Uri {
        val safeFmt = format?.takeIf { it.isNotBlank() } ?: "mp3"
        val localFile = getLocalAudioFile(trackId, safeFmt)
        if (localFile.exists() && localFile.length() > 0) {
            return Uri.fromFile(localFile)
        }
        // Проверяем, может файл сохранен с другим расширением
        val anyExisting = offlineAudioDir.listFiles()?.firstOrNull {
            it.name.substringBefore('.') == trackId.toString() && it.length() > 0
        }
        if (anyExisting != null) {
            return Uri.fromFile(anyExisting)
        }

        val query = if (token.isNotEmpty()) "?token=${Uri.encode(token)}" else ""
        return Uri.parse("$serverUrl/api/stream/$trackId$query")
    }

    /**
     * Скачивает трек на телефон.
     * @param force если true, скачивает даже если автоматический режим выключен (например, при ручном скачивании)
     */
    suspend fun downloadTrack(track: TrackDto, token: String = "", force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && !isDownloadModeEnabled) return@withContext false
        if (downloadOnlyOnWifi && !isWifiConnected()) return@withContext false

        val safeFmt = track.safeAudioFormat
        val targetFile = getLocalAudioFile(track.id, safeFmt)
        if (targetFile.exists() && targetFile.length() > 0) {
            downloadedCache[targetFile.name] = true
            _downloadedTrackIds.update { it + track.id }
            return@withContext true
        }

        val tempFile = File(offlineAudioDir, "${track.id}.$safeFmt.tmp")
        _downloadingTrackIds.update { it + track.id }

        try {
            val query = if (token.isNotEmpty()) "?token=${Uri.encode(token)}" else ""
            val requestBuilder = Request.Builder()
                .url("$serverUrl/api/stream/${track.id}$query")
            if (token.isNotEmpty()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadingTrackIds.update { it - track.id }
                    return@withContext false
                }
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                downloadedCache[targetFile.name] = true
                _downloadedTrackIds.update { it + track.id }
                _downloadingTrackIds.update { it - track.id }
                return@withContext true
            } else {
                tempFile.delete()
                _downloadingTrackIds.update { it - track.id }
                return@withContext false
            }
        } catch (e: Exception) {
            tempFile.delete()
            targetFile.delete()
            downloadedCache.remove(targetFile.name)
            _downloadedTrackIds.update { it - track.id }
            _downloadingTrackIds.update { it - track.id }
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Удаляет конкретный трек из офлайн-кеша.
     */
    fun deleteTrack(trackId: Int, format: String? = null): Boolean {
        var deletedAny = false
        val safeFmt = format?.takeIf { it.isNotBlank() } ?: "mp3"
        val targetFile = getLocalAudioFile(trackId, safeFmt)
        if (targetFile.exists()) {
            if (targetFile.delete()) deletedAny = true
        }
        // Также удаляем любые файлы с этим trackId
        offlineAudioDir.listFiles()?.filter { it.name.substringBefore('.') == trackId.toString() }?.forEach { f ->
            if (f.delete()) deletedAny = true
        }
        val lyricsFile = File(offlineLyricsDir, "$trackId.lrc")
        if (lyricsFile.exists()) {
            lyricsFile.delete()
        }
        downloadedCache.remove(targetFile.name)
        _downloadedTrackIds.update { it - trackId }
        _downloadingTrackIds.update { it - trackId }
        return deletedAny
    }

    /**
     * Размер всех сохраненных офлайн треков в байтах.
     */
    fun getOfflineTracksSizeBytes(): Long {
        return getFolderSize(offlineAudioDir) + getFolderSize(offlineLyricsDir)
    }

    fun getDownloadedCount(): Int = _downloadedTrackIds.value.size

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return total
    }

    /**
     * Очищает сохраненные треки с телефона.
     */
    fun clearOfflineCache() {
        offlineAudioDir.listFiles()?.forEach { it.delete() }
        offlineLyricsDir.listFiles()?.forEach { it.delete() }
        downloadedCache.clear()
        _downloadedTrackIds.value = emptySet()
    }
}
