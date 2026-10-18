package com.sentinel.music

import com.sentinel.music.domain.models.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParsingTest {

    @Test
    fun testActiveLyricIndexResolution() {
        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "Line 1"),
            LyricLine(timeMs = 5000L, text = "Line 2"),
            LyricLine(timeMs = 12000L, text = "Line 3"),
            LyricLine(timeMs = 20000L, text = "Line 4")
        )

        fun findActiveIndex(progressMs: Long): Int {
            var active = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= progressMs) {
                    active = i
                } else {
                    break
                }
            }
            return active
        }

        // Before any lyrics start
        assertEquals(-1, findActiveIndex(500L))

        // At exact start of Line 1
        assertEquals(0, findActiveIndex(1000L))

        // Between Line 1 and Line 2
        assertEquals(0, findActiveIndex(3500L))

        // At Line 2
        assertEquals(1, findActiveIndex(5000L))

        // Between Line 2 and Line 3
        assertEquals(1, findActiveIndex(9000L))

        // Beyond last line
        assertEquals(3, findActiveIndex(25000L))
    }

    @Test
    fun testUnsyncedLyricsFallback() {
        val rawLrc = """
            First verse line
            Second verse line
            Chorus line
        """.trimIndent()

        val parsed = rawLrc.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { idx, line ->
                LyricLine(timeMs = idx * 4000L, text = line.trim())
            }

        assertEquals(3, parsed.size)
        assertEquals(0L, parsed[0].timeMs)
        assertEquals("First verse line", parsed[0].text)
        assertEquals(4000L, parsed[1].timeMs)
        assertEquals("Second verse line", parsed[1].text)
        assertEquals(8000L, parsed[2].timeMs)
        assertEquals("Chorus line", parsed[2].text)
    }
}
