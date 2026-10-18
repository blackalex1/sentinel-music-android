package com.sentinel.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateParsingTest {

    @Test
    fun testCandidateSourceExtraction() {
        val cand1 = mapOf<String, Any?>("source_name" to "WebSource", "source" to "websource")
        val cand2 = mapOf<String, Any?>("source" to "peer_source")
        val cand3 = mapOf<String, Any?>()

        val source1 = cand1["source_name"] as? String ?: (cand1["source"] as? String ?: "Источник")
        val source2 = cand2["source_name"] as? String ?: (cand2["source"] as? String ?: "Источник")
        val source3 = cand3["source_name"] as? String ?: (cand3["source"] as? String ?: "Источник")

        assertEquals("WebSource", source1)
        assertEquals("peer_source", source2)
        assertEquals("Источник", source3)
    }

    @Test
    fun testCandidateVersionBadgeAssignment() {
        fun resolveVersionLabel(cand: Map<String, Any?>): String {
            val cFormat = (cand["format"] as? String ?: "mp3").uppercase()
            val vType = (cand["version_type"] as? String ?: "").lowercase()
            val isExplicit = (cand["explicit"] as? Boolean == true) || vType.contains("explicit")

            return cand["version_label"] as? String ?: when {
                isExplicit -> "Explicit (18+)"
                cFormat == "FLAC" || vType.contains("flac") -> "FLAC Lossless"
                vType.contains("slowed") -> "Slowed"
                vType.contains("speed") -> "Speed Up"
                vType.contains("remix") -> "Remix"
                else -> "Оригинал"
            }
        }

        assertEquals("Explicit (18+)", resolveVersionLabel(mapOf("explicit" to true)))
        assertEquals("Explicit (18+)", resolveVersionLabel(mapOf("version_type" to "explicit_version")))
        assertEquals("FLAC Lossless", resolveVersionLabel(mapOf("format" to "flac", "version_type" to "flac")))
        assertEquals("Remix", resolveVersionLabel(mapOf("version_type" to "club_remix")))
        assertEquals("Slowed", resolveVersionLabel(mapOf("version_type" to "slowed_reverb")))
        assertEquals("Speed Up", resolveVersionLabel(mapOf("version_type" to "speed_up")))
        assertEquals("Оригинал", resolveVersionLabel(mapOf("version_type" to "original", "format" to "mp3")))
    }

    @Test
    fun testCandidatePreviewEligibility() {
        fun canPreview(cand: Map<String, Any?>): Boolean {
            return cand["can_preview"] as? Boolean ?: true
        }

        // По умолчанию превью доступно, если не указано обратное
        assertTrue(canPreview(mapOf("source" to "web_source")))

        // Явный флаг can_preview от сервера
        assertFalse(canPreview(mapOf("source" to "p2p_source", "can_preview" to false)))
        assertTrue(canPreview(mapOf("source" to "p2p_source", "can_preview" to true)))
    }

    @Test
    fun testCandidateFilteringByDynamicSourceAndQuality() {
        val candidates = listOf(
            mapOf("candidate_id" to "1", "source" to "source_a", "format" to "mp3", "bitrate" to 320),
            mapOf("candidate_id" to "2", "source" to "source_b", "format" to "mp3", "bitrate" to 320),
            mapOf("candidate_id" to "3", "source" to "source_c", "format" to "flac", "bitrate" to 950),
            mapOf("candidate_id" to "4", "source" to "source_a", "format" to "mp3", "bitrate" to 192),
            mapOf("candidate_id" to "5", "source" to "source_c", "format" to "mp3", "bitrate" to 320)
        )

        fun filterCandidates(list: List<Map<String, Any?>>, filter: String): List<Map<String, Any?>> {
            if (filter == "all") return list
            return list.filter { cand ->
                val source = (cand["source_name"] as? String ?: cand["source"] as? String ?: "").lowercase()
                val format = (cand["format"] as? String ?: "").lowercase()
                val vType = (cand["version_type"] as? String ?: "").lowercase()
                when (filter) {
                    "flac" -> format == "flac" || vType.contains("flac")
                    else -> source.equals(filter, ignoreCase = true)
                }
            }
        }

        assertEquals(5, filterCandidates(candidates, "all").size)
        assertEquals(1, filterCandidates(candidates, "flac").size)
        assertEquals("3", filterCandidates(candidates, "flac").first()["candidate_id"])
        assertEquals(2, filterCandidates(candidates, "source_a").size)
        assertEquals(1, filterCandidates(candidates, "source_b").size)
        assertEquals(2, filterCandidates(candidates, "source_c").size)
    }
}
