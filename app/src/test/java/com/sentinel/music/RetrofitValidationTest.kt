package com.sentinel.music

import com.sentinel.music.data.api.MusicApiService
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitValidationTest {

    @Test
    fun testMusicApiServiceValidation() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .validateEagerly(true)
            .build()

        retrofit.create(MusicApiService::class.java)
    }
}
