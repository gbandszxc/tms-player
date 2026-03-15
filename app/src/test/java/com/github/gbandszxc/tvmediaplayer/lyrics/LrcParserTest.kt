package com.github.gbandszxc.tvmediaplayer.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses offset tag`() {
        val content = """
            [offset:+500]
            [00:01.00]a
        """.trimIndent()

        val timeline = LrcParser.parseTimeline(content)

        assertEquals(500, timeline.offsetMs)
        assertEquals(1000, timeline.lines.first().timestampMs)
    }

    @Test
    fun `finds current line with binary search`() {
        val lines = listOf(
            LyricLine(1000, "a"),
            LyricLine(2000, "b"),
            LyricLine(3000, "c")
        )

        assertEquals(-1, LrcParser.findCurrentLineIndex(lines, 500))
        assertEquals(1, LrcParser.findCurrentLineIndex(lines, 2500))
        assertEquals(2, LrcParser.findCurrentLineIndex(lines, 3500))
    }
}

