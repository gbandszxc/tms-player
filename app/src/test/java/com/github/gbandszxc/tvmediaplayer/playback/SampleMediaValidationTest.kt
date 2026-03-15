package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleMediaValidationTest {

    private fun resolveSampleFile(name: String): File {
        val candidates = listOf(
            File("sample/$name"),
            File("../sample/$name"),
            File("../../sample/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Sample file not found: $name")
    }

    @Test
    fun lrcSampleShouldBeParsable() {
        val lrc = resolveSampleFile("Track9.lrc")
        val content = lrc.readText(Charsets.UTF_8)
        val timeline = LrcParser.parseTimeline(content)
        assertTrue("LRC should contain parsed lines", timeline.lines.isNotEmpty())
        assertTrue(
            "LRC should include valid timestamp ordering",
            timeline.lines.zipWithNext().all { (a, b) -> a.timestampMs <= b.timestampMs }
        )
    }

    @Test
    fun mp3SampleShouldContainEmbeddedArtwork() {
        val mp3 = resolveSampleFile("Track9.mp3")
        val audio = AudioFileIO.read(mp3)
        val artwork = audio.tag?.firstArtwork
        assertNotNull("Sample MP3 should contain embedded artwork", artwork)
        assertTrue("Embedded artwork bytes should not be empty", (artwork?.binaryData?.size ?: 0) > 0)
    }
}
