package com.sentinel.music

import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.player.EqualizerManager
import com.sentinel.music.presentation.main.MainViewModel
import com.sentinel.music.presentation.main.PlaybackRepeatMode
import com.sentinel.music.presentation.main.SearchTab
import com.sentinel.music.presentation.main.TrackFilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndFiltersTest {

    @Test
    fun testSearchTabTitles() {
        assertEquals("В медиатеке", SearchTab.LIBRARY.title)
        assertEquals("Новые треки", SearchTab.NEW_TRACKS.title)
    }

    @Test
    fun testTrackFilterModes() {
        val tracks = listOf(
            TrackDto(id = 1, title = "Song 1", artist = "Artist A", audioFormat = "flac", isLiked = true),
            TrackDto(id = 2, title = "Song 2", artist = "Artist B", audioFormat = "mp3", isLiked = false),
            TrackDto(id = 3, title = "Song 3", artist = "Artist C", audioFormat = "FLAC", isLiked = false),
            TrackDto(id = 4, title = "Song 4", artist = "Artist D", audioFormat = "m4a", isLiked = true)
        )

        // ALL
        assertEquals(4, tracks.size)

        // FLAC only
        val flacOnly = tracks.filter { it.audioFormat.equals("flac", ignoreCase = true) }
        assertEquals(2, flacOnly.size)
        assertTrue(flacOnly.all { it.audioFormat.equals("flac", ignoreCase = true) })

        // Liked only
        val likedOnly = tracks.filter { it.isLiked }
        assertEquals(2, likedOnly.size)
        assertTrue(likedOnly.all { it.isLiked })
    }

    @Test
    fun testPlaybackRepeatModeCycle() {
        var mode = PlaybackRepeatMode.OFF
        assertEquals("Повтор выключен", mode.title)

        // Cycle to ALL
        mode = when (mode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        assertEquals(PlaybackRepeatMode.ALL, mode)
        assertEquals("Повторять все", mode.title)

        // Cycle to ONE
        mode = when (mode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        assertEquals(PlaybackRepeatMode.ONE, mode)
        assertEquals("Повторять один трек", mode.title)

        // Cycle back to OFF
        mode = when (mode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.OFF
        }
        assertEquals(PlaybackRepeatMode.OFF, mode)
    }

    @Test
    fun testMoodFiltering() {
        val tracks = listOf(
            TrackDto(id = 1, title = "Energetic Song", artist = "Rock Band", bpm = 125f, loudnessLufs = -6.5f),
            TrackDto(id = 2, title = "Chill Song", artist = "Ambient", bpm = 78f, danceability = 0.4f, valence = 0.65f, loudnessLufs = -11.0f, energy = 0.4f),
            TrackDto(id = 3, title = "Happy Song", artist = "Pop Star", bpm = 95f, danceability = 0.5f, valence = 0.8f),
            TrackDto(id = 4, title = "Sad Song", artist = "Emo Rock", bpm = 105f, danceability = 0.5f, valence = 0.3f, loudnessLufs = -7.3f, energy = 0.8f)
        )

        val energyTracks = tracks.filter { it.matchesMood("energy") }
        assertEquals(1, energyTracks.size)
        assertEquals("Energetic Song", energyTracks[0].title)

        val chillTracks = tracks.filter { it.matchesMood("chill") }
        assertEquals(1, chillTracks.size)
        assertEquals("Chill Song", chillTracks[0].title)

        val happyTracks = tracks.filter { it.matchesMood("happy") }
        assertEquals(1, happyTracks.size)
        assertEquals("Happy Song", happyTracks[0].title)

        val sadTracks = tracks.filter { it.matchesMood("sad") }
        assertEquals(1, sadTracks.size)
        assertEquals("Sad Song", sadTracks[0].title)
    }

    @Test
    fun testVolumeNormalizationNeverExceedsOne() {
        // Positive gain (e.g. +3.5 dB, +10.0 dB) must be strictly clamped to <= 1.0f (never > 1.0f for MediaController)
        val posGainVol = MainViewModel.calculateNormalizedVolume(3.5)
        assertEquals(1.0f, posGainVol, 0.0001f)

        val largePosGainVol = MainViewModel.calculateNormalizedVolume(15.0)
        assertEquals(1.0f, largePosGainVol, 0.0001f)

        // Null and 0.0 gain
        assertEquals(1.0f, MainViewModel.calculateNormalizedVolume(null), 0.0001f)
        assertEquals(1.0f, MainViewModel.calculateNormalizedVolume(0.0), 0.0001f)

        // Negative gain (attenuation)
        val negGainVol = MainViewModel.calculateNormalizedVolume(-6.0)
        assertTrue(negGainVol < 1.0f)
        assertTrue(negGainVol >= 0.05f)
        assertEquals(0.501187f, negGainVol, 0.005f)

        // Extreme negative gain clamped to min 0.05f
        val extremeNegGain = MainViewModel.calculateNormalizedVolume(-100.0)
        assertEquals(0.05f, extremeNegGain, 0.0001f)
    }

    @Test
    fun testFlacFilterWithPositiveGainSafety() {
        val tracks = listOf(
            TrackDto(id = 1, title = "FLAC Track Loud", artist = "Artist 1", audioFormat = "flac", gainDb = 2.5),
            TrackDto(id = 2, title = "MP3 Track", artist = "Artist 2", audioFormat = "mp3", gainDb = -1.0),
            TrackDto(id = 3, title = "FLAC Track Quiet", artist = "Artist 3", audioFormat = "FLAC", gainDb = 5.0)
        )

        val flacTracks = tracks.filter { it.audioFormat.equals("flac", ignoreCase = true) }
        assertEquals(2, flacTracks.size)

        // All FLAC tracks must have safe volume <= 1.0f and >= 0.0f
        flacTracks.forEach { track ->
            val vol = MainViewModel.calculateNormalizedVolume(track.gainDb)
            assertTrue("Volume $vol must be <= 1.0f to avoid Media3 crash", vol in 0.0f..1.0f)
        }
    }

    @Test
    fun testLoudnessToggleAndTargetPresets() {
        // When disabled, volume should always be 1.0f (raw uncorrected audio)
        val disabledVol = MainViewModel.calculateNormalizedVolume(gainDb = -6.0, isEnabled = false)
        assertEquals(1.0f, disabledVol, 0.0001f)

        // When enabled with default -14 LUFS
        val defaultLufsVol = MainViewModel.calculateNormalizedVolume(gainDb = -6.0, isEnabled = true, targetLufs = -14.0f)
        assertEquals(0.501187f, defaultLufsVol, 0.005f)

        // When enabled with -18 LUFS (additional -4 dB attenuation)
        val hifiLufsVol = MainViewModel.calculateNormalizedVolume(gainDb = -6.0, isEnabled = true, targetLufs = -18.0f)
        assertTrue(hifiLufsVol < defaultLufsVol)
        assertEquals(0.316227f, hifiLufsVol, 0.005f)

        // When enabled with -23 LUFS (additional -9 dB attenuation)
        val broadcastLufsVol = MainViewModel.calculateNormalizedVolume(gainDb = -6.0, isEnabled = true, targetLufs = -23.0f)
        assertTrue(broadcastLufsVol < hifiLufsVol)
        assertEquals(0.177827f, broadcastLufsVol, 0.005f)
    }

    @Test
    fun testEqualizerPresetDefinitions() {
        val presets = EqualizerManager.PRESET_VALUES
        assertTrue(presets.containsKey("Обычный (Flat)"))
        assertTrue(presets.containsKey("Рок"))
        assertTrue(presets.containsKey("Глубокий бас"))

        // All presets must have 5 frequency bands in range [-12, +12] dB
        presets.forEach { (name, bands) ->
            assertEquals("Preset $name must have 5 bands", 5, bands.size)
            assertTrue("Preset $name bands must be in range [-12, +12] dB", bands.all { it in -12f..12f })
        }

        // Flat preset must be all zeros
        val flat = presets["Обычный (Flat)"]!!
        assertTrue(flat.all { it == 0f })
    }
}
