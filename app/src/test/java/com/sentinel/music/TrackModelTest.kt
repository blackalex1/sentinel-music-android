package com.sentinel.music

import com.google.gson.Gson
import com.sentinel.music.data.model.TrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackModelTest {

    private val gson = Gson()

    @Test
    fun testTrackDtoSerializationAndDeserialization() {
        val json = """
            {
                "id": 42,
                "title": "Starboy",
                "artist": "The Weeknd",
                "album": "Starboy",
                "duration": 230.5,
                "audio_format": "flac",
                "bitrate": 1024,
                "is_liked": true,
                "is_disliked": false
            }
        """.trimIndent()

        val track = gson.fromJson(json, TrackDto::class.java)

        assertNotNull(track)
        assertEquals(42, track.id)
        assertEquals("Starboy", track.title)
        assertEquals("The Weeknd", track.artist)
        assertEquals("Starboy", track.album)
        assertEquals(230.5, track.duration, 0.001)
        assertEquals("flac", track.audioFormat)
        assertEquals(1024, track.bitrate)
        assertTrue(track.isLiked)
    }

    @Test
    fun testTrackListReplacementLogic() {
        val originalTracks = listOf(
            TrackDto(id = 1, title = "Original 1", artist = "Artist 1", audioFormat = "mp3"),
            TrackDto(id = 2, title = "Original 2 (Wrong)", artist = "Artist 2", audioFormat = "mp3"),
            TrackDto(id = 3, title = "Original 3", artist = "Artist 3", audioFormat = "flac")
        )

        val replacedTrack = TrackDto(
            id = 2,
            title = "Original 2 (Replaced FLAC)",
            artist = "Artist 2",
            audioFormat = "flac",
            bitrate = 960
        )

        // Replacement in list preserving index order
        val updatedTracks = originalTracks.map {
            if (it.id == replacedTrack.id) replacedTrack else it
        }

        assertEquals(3, updatedTracks.size)
        assertEquals("Original 1", updatedTracks[0].title)
        assertEquals("Original 2 (Replaced FLAC)", updatedTracks[1].title)
        assertEquals("flac", updatedTracks[1].audioFormat)
        assertEquals(960, updatedTracks[1].bitrate)
        assertEquals("Original 3", updatedTracks[2].title)
    }

    @Test
    fun testCurrentTrackReplacementUpdate() {
        val currentTrack = TrackDto(id = 10, title = "Song Old", artist = "Band", audioFormat = "mp3")
        val updatedTrack = TrackDto(id = 10, title = "Song New", artist = "Band", audioFormat = "flac")

        val newCurrent = if (currentTrack.id == updatedTrack.id) updatedTrack else currentTrack

        assertEquals("Song New", newCurrent.title)
        assertEquals("flac", newCurrent.audioFormat)
    }
}
