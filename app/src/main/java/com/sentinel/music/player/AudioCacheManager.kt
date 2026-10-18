package com.sentinel.music.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import java.io.File

object AudioCacheManager {
    private const val DEFAULT_CACHE_SIZE_MB = 500
    private const val PREFS_KEY_CACHE_SIZE_MB = "stream_cache_size_mb"

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getMaxCacheSizeMb(context: Context): Int {
        val prefs = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        return prefs.getInt(PREFS_KEY_CACHE_SIZE_MB, DEFAULT_CACHE_SIZE_MB)
    }

    fun setMaxCacheSizeMb(context: Context, sizeMb: Int) {
        val prefs = context.getSharedPreferences("music_service_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(PREFS_KEY_CACHE_SIZE_MB, sizeMb).apply()
        synchronized(this) {
            try {
                simpleCache?.release()
                simpleCache = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getMaxCacheSizeBytes(context: Context): Long {
        val mb = getMaxCacheSizeMb(context)
        return mb.toLong() * 1024 * 1024
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: run {
            val cacheDir = File(context.cacheDir, "sentinel_audio_cache")
            val evictor = LeastRecentlyUsedCacheEvictor(getMaxCacheSizeBytes(context))
            val databaseProvider = StandaloneDatabaseProvider(context)
            SimpleCache(cacheDir, evictor, databaseProvider).also {
                simpleCache = it
            }
        }
    }

    fun createMediaSourceFactory(context: Context): MediaSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DefaultMediaSourceFactory(context)
            .setDataSourceFactory(cacheDataSourceFactory)
    }

    fun getStreamCacheSizeBytes(context: Context): Long {
        val cacheDir = File(context.cacheDir, "sentinel_audio_cache")
        return getFolderSize(cacheDir)
    }

    private fun getFolderSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return total
    }

    fun clearCache(context: Context) {
        synchronized(this) {
            try {
                simpleCache?.release()
                simpleCache = null
                val cacheDir = File(context.cacheDir, "sentinel_audio_cache")
                cacheDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
