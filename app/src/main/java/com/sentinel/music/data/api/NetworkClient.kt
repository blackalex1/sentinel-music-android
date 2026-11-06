package com.sentinel.music.data.api

import com.sentinel.music.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val sessionManager: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val token = sessionManager.token
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    val sharedConnectionPool = okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES)

    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createApiService(sessionManager: SessionManager): MusicApiService {
        val okHttpClient = sharedOkHttpClient.newBuilder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .build()

        val rawUrl = sessionManager.serverUrl
        val baseUrl = if (rawUrl.endsWith("/")) rawUrl else "$rawUrl/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApiService::class.java)
    }

    /**
     * Создает временный клиент для проверки подключения и авторизации к произвольному URL
     */
    fun createProbeService(targetUrl: String): MusicApiService {
        val okHttpClient = sharedOkHttpClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val baseUrl = if (targetUrl.endsWith("/")) targetUrl else "$targetUrl/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApiService::class.java)
    }
}
