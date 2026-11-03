package com.sentinel.music

import com.google.gson.Gson
import com.sentinel.music.data.model.PlaybackUrlResolver
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.data.model.candidateToTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUrlResolverTest {

    @Test
    fun testCandidatePlaybackUrlResolution() {
        val candidate = TrackDto(
            id = -105372,
            title = "Test Song",
            artist = "Test Artist",
            isPreview = true,
            candidateId = "cand_stream_18274",
            coverUrl = "https://cdn.example.org/cover18274.jpg"
        )

        val serverUrl = "https://sentinel.example.com"
        val token = "test_auth_token_xyz"

        val playUrl = PlaybackUrlResolver.resolvePlaybackUrl(candidate, serverUrl, token)

        // Должен указывать на эндпоинт превью кандидатов, а НЕ на /api/stream/{id}
        assertEquals(
            "https://sentinel.example.com/api/tracks/candidates/preview?candidate_id=cand_stream_18274&token=test_auth_token_xyz",
            playUrl
        )
    }

    @Test
    fun testCandidatePlaybackUrlResolutionTrailingSlashAndSpecialChars() {
        val candidate = TrackDto(
            id = -999,
            title = "Special Track",
            artist = "Special Artist",
            isPreview = true,
            candidateId = "provider_x:audio:12345 6789"
        )

        val serverUrl = "https://music.example.com:8443/"
        val token = "my token+test"

        val playUrl = PlaybackUrlResolver.resolvePlaybackUrl(candidate, serverUrl, token)

        // Проверяем: нет двойного слеша //api, пробелы и спецсимволы корректно закодированы
        assertTrue("URL must not contain double slashes after domain", !playUrl.contains(".example.com:8443//"))
        assertTrue("Candidate ID must be URL-encoded", playUrl.contains("candidate_id=provider_x%3Aaudio%3A12345+6789"))
        assertTrue("Token must be URL-encoded", playUrl.contains("token=my+token%2Btest"))
    }

    @Test
    fun testLibraryTrackPlaybackUrlResolution() {
        val libraryTrack = TrackDto(
            id = 42,
            title = "Local Library Song",
            artist = "Local Artist",
            isPreview = false,
            candidateId = null
        )

        val serverUrl = "https://sentinel.example.com"
        val token = "valid_token"

        val playUrl = PlaybackUrlResolver.resolvePlaybackUrl(libraryTrack, serverUrl, token)

        // Обычный трек из фонотеки должен использовать /api/stream/{id}
        assertEquals("https://sentinel.example.com/api/stream/42?token=valid_token", playUrl)
    }

    @Test
    fun testCandidateCoverUrlResolutionWithExternalCover() {
        val candidateWithCover = TrackDto(
            id = -555,
            title = "Preview Track",
            artist = "Artist",
            isPreview = true,
            candidateId = "cand_555",
            coverUrl = "https://cdn.example.org/artworks/555.jpg"
        )

        val coverUrl = PlaybackUrlResolver.resolveCoverUrl(candidateWithCover, "https://sentinel.example.com", "token")

        // Должен вернуть внешний URL обложки напрямую
        assertEquals("https://cdn.example.org/artworks/555.jpg", coverUrl)
    }

    @Test
    fun testCandidateCoverUrlResolutionWithoutCoverReturnsNull() {
        val candidateWithoutCover = TrackDto(
            id = -555,
            title = "Preview Track",
            artist = "Artist",
            isPreview = true,
            candidateId = "cand_555",
            coverUrl = null
        )

        val coverUrl = PlaybackUrlResolver.resolveCoverUrl(candidateWithoutCover, "https://sentinel.example.com", "token")

        // Для кандидатов без обложки НЕЛЬЗЯ генерировать /api/covers/-555 (вызовет HTTP 404)
        assertNull("Candidate without cover must return null to prevent 404 requests", coverUrl)
    }

    @Test
    fun testLibraryTrackCoverUrlResolution() {
        val libraryTrack = TrackDto(
            id = 100,
            title = "Library Song",
            artist = "Artist",
            isPreview = false,
            candidateId = null,
            coverUrl = null
        )

        val coverUrl = PlaybackUrlResolver.resolveCoverUrl(libraryTrack, "https://sentinel.example.com", "tok123")
        assertEquals("https://sentinel.example.com/api/covers/100?token=tok123", coverUrl)

        val thumbUrl = PlaybackUrlResolver.resolveCoverUrl(libraryTrack, "https://sentinel.example.com", "tok123", size = 160)
        assertEquals("https://sentinel.example.com/api/covers/100?token=tok123&size=160", thumbUrl)
    }

    @Test
    fun testCandidateEndToEndFlow() {
        val candidateRaw = mapOf<String, Any?>(
            "candidate_id" to "cand_source_998811",
            "title" to "Sample Song",
            "artist" to "Sample Artist",
            "album" to "Sample Album",
            "source" to "external_plugin",
            "source_name" to "External Source",
            "duration" to 258,
            "format" to "mp3",
            "bitrate" to 320,
            "cover_url" to "https://cdn.example.org/covers/sample.jpg",
            "can_preview" to true
        )

        val trackDto = candidateToTrackDto(candidateRaw)

        assertTrue("Preview track ID must be negative", trackDto.id < 0)
        assertTrue("Must be marked as preview", trackDto.isPreview)
        assertEquals("cand_source_998811", trackDto.candidateId)
        assertEquals("https://cdn.example.org/covers/sample.jpg", trackDto.coverUrl)

        val serverUrl = "https://sentinel.example.com"
        val token = "user_jwt_token"

        val playUrl = PlaybackUrlResolver.resolvePlaybackUrl(trackDto, serverUrl, token)
        assertEquals(
            "https://sentinel.example.com/api/tracks/candidates/preview?candidate_id=cand_source_998811&token=user_jwt_token",
            playUrl
        )

        val coverUrl = PlaybackUrlResolver.resolveCoverUrl(trackDto, serverUrl, token)
        assertEquals("https://cdn.example.org/covers/sample.jpg", coverUrl)
    }

    @Test
    fun testTrackDtoSerializationForRemoteDevices() {
        val gson = Gson()
        val candidate = TrackDto(
            id = -105372,
            title = "Cast Song",
            artist = "Cast Artist",
            isPreview = true,
            candidateId = "cand_cast_9999",
            coverUrl = "https://cdn.example.org/cast.jpg"
        )

        val json = gson.toJson(candidate)

        // Проверяем, что сериализация производит ожидаемые поля snake_case для клиентов Desktop/Station/Web
        assertTrue("JSON must contain is_preview", json.contains("\"is_preview\":true"))
        assertTrue("JSON must contain candidate_id", json.contains("\"candidate_id\":\"cand_cast_9999\""))
        assertTrue("JSON must contain cover_url", json.contains("\"cover_url\":\"https://cdn.example.org/cast.jpg\""))
    }

    @Test
    fun testTrackDtoDeserializationFromRemoteDevices() {
        val gson = Gson()

        // Сценарий 1: JSON со snake_case от Desktop / Web / Server
        val snakeCaseJson = """
            {
                "id": -105372,
                "title": "Remote Song",
                "artist": "Remote Artist",
                "duration": 210.0,
                "is_preview": true,
                "candidate_id": "cand_remote_snake",
                "cover_url": "https://cdn.example.org/snake.jpg"
            }
        """.trimIndent()

        val parsedSnake = gson.fromJson(snakeCaseJson, TrackDto::class.java)
        assertTrue(parsedSnake.isPreview)
        assertEquals("cand_remote_snake", parsedSnake.candidateId)
        assertEquals("https://cdn.example.org/snake.jpg", parsedSnake.coverUrl)

        // Сценарий 2: JSON с camelCase (alternate)
        val camelCaseJson = """
            {
                "id": -105372,
                "title": "Remote Song",
                "artist": "Remote Artist",
                "duration": 210.0,
                "isPreview": true,
                "candidateId": "cand_remote_camel",
                "coverUrl": "https://cdn.example.org/camel.jpg"
            }
        """.trimIndent()

        val parsedCamel = gson.fromJson(camelCaseJson, TrackDto::class.java)
        assertTrue(parsedCamel.isPreview)
        assertEquals("cand_remote_camel", parsedCamel.candidateId)
        assertEquals("https://cdn.example.org/camel.jpg", parsedCamel.coverUrl)
    }
}
