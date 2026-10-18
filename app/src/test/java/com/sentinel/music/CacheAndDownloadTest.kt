package com.sentinel.music

import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.TrackFilterMode
import com.sentinel.music.presentation.navigation.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheAndDownloadTest {

    @Test
    fun testSettingsTabExists() {
        val tab = MainTab.SETTINGS
        assertEquals("Настройки", tab.title)
        assertEquals(5, MainTab.values().size)
    }

    @Test
    fun testMainUiStateCacheDefaults() {
        val state = MainUiState()
        assertFalse(state.isFullDownloading)
        assertEquals(0f, state.fullDownloadProgress, 0.001f)
        assertEquals(0, state.fullDownloadCurrentIndex)
        assertEquals(0, state.fullDownloadTotal)
        assertTrue(state.downloadedTrackIds.isEmpty())
        assertTrue(state.downloadingTrackIds.isEmpty())
        assertEquals(500, state.maxStreamCacheSizeMb)
        assertFalse(state.downloadOnlyOnWifi)
        assertTrue(state.preloadNextTrack)
    }

    @Test
    fun testOfflineFilterUsesDownloadedTrackIds() {
        val tracks = listOf(
            TrackDto(id = 10, title = "Track 1", artist = "Artist 1", audioFormat = "flac"),
            TrackDto(id = 20, title = "Track 2", artist = "Artist 2", audioFormat = "mp3"),
            TrackDto(id = 30, title = "Track 3", artist = "Artist 3", audioFormat = "flac")
        )

        val downloadedIds = setOf(10, 30)
        val offlineTracks = tracks.filter { downloadedIds.contains(it.id) }

        assertEquals(2, offlineTracks.size)
        assertTrue(offlineTracks.any { it.id == 10 })
        assertTrue(offlineTracks.any { it.id == 30 })
        assertFalse(offlineTracks.any { it.id == 20 })
    }

    @Test
    fun testFullDownloadMissingCalculation() {
        val allLibrary = listOf(
            TrackDto(id = 1, title = "Song A", artist = "Singer", audioFormat = "flac", isLiked = true),
            TrackDto(id = 2, title = "Song B", artist = "Singer", audioFormat = "mp3", isLiked = false),
            TrackDto(id = 3, title = "Song C", artist = "Singer", audioFormat = "flac", isLiked = true),
            TrackDto(id = 4, title = "Song D", artist = "Singer", audioFormat = "mp3", isLiked = false)
        )

        val alreadyDownloaded = setOf(1, 2)

        // 1. All missing
        val missingAll = allLibrary.filter { !alreadyDownloaded.contains(it.id) }
        assertEquals(2, missingAll.size)
        assertEquals(listOf(3, 4), missingAll.map { it.id })

        // 2. Liked only missing
        val likedTracks = allLibrary.filter { it.isLiked }
        val missingLiked = likedTracks.filter { !alreadyDownloaded.contains(it.id) }
        assertEquals(1, missingLiked.size)
        assertEquals(3, missingLiked[0].id)
    }

    @Test
    fun testDownloadProgressCalculation() {
        val total = 50
        for (i in 0..total) {
            val progress = if (total > 0) (i.toFloat() / total.toFloat()) else 0f
            assertTrue(progress in 0f..1f)
        }
    }
}
