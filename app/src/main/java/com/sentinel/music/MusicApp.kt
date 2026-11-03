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
        val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        HttpsURLConnection.setDefaultHostnameVerifier { hostname, session ->
            if (defaultVerifier.verify(hostname, session)) {
                true
            } else {
                val clean = hostname.trim().lowercase()
                clean == "localhost" ||
                        clean == "127.0.0.1" ||
                        clean.startsWith("192.168.") ||
                        clean.startsWith("10.") ||
                        clean.matches(Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))
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
