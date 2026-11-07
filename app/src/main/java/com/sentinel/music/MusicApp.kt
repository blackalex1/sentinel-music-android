package com.sentinel.music

import android.app.Application
import javax.net.ssl.HttpsURLConnection
import com.sentinel.music.data.api.NetworkClient
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        // Разрешаем подключение HttpsURLConnection (ExoPlayer и MediaSession/Notification)
        // к серверам в локальной сети/LAN, даже если SSL-сертификат выдан на публичный домен
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        // nosemgrep: android-kotlin-insecure-ssl, improper-certificate-validation
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
            if (defaultVerifier.verify(hostname, session)) {
                true
            } else {
                NetworkClient.isLocalOrPrivateHost(hostname)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NetworkClient.sharedOkHttpClient }
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
