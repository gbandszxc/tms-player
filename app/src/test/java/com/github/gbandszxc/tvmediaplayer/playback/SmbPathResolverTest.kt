package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbPathResolverTest {

    @Test
    fun buildExternalLrcPath_withShareAndSubPath_shouldKeepShareSegment() {
        val config = SmbConfig(
            host = "192.168.31.233",
            share = "Banana",
            path = "",
            username = "u",
            password = "p",
            guest = false
        )
        val entry = SmbEntry(
            name = "Track9.mp3",
            fullPath = "RJ01113327/Track9.mp3",
            isDirectory = false,
            streamUri = "smb://192.168.31.233/Banana/RJ01113327/Track9.mp3"
        )

        val path = SmbPathResolver.buildExternalLrcPath(config, entry)
        assertEquals("smb://192.168.31.233/Banana/RJ01113327/Track9.lrc", path)
    }

    @Test
    fun buildExternalLrcPath_shouldFallbackToStreamUriWhenMediaIdMissing() {
        val config = SmbConfig(
            host = "192.168.31.233",
            share = "Banana",
            path = "",
            username = "",
            password = "",
            guest = true
        )
        val entry = SmbEntry(
            name = "Track9.mp3",
            fullPath = "",
            isDirectory = false,
            streamUri = "smb://192.168.31.233/Banana/RJ01113327/Track9.mp3"
        )

        val path = SmbPathResolver.buildExternalLrcPath(config, entry)
        assertEquals("smb://192.168.31.233/Banana/RJ01113327/Track9.lrc", path)
    }

    @Test
    fun track9SampleShouldParseTimestamps() {
        val file = java.io.File("sample/Track9.lrc").takeIf { it.exists() }
            ?: java.io.File("../sample/Track9.lrc")
        val content = file.readText(Charsets.UTF_8)
        val timeline = LrcParser.parseTimeline(content)
        assertTrue(timeline.lines.isNotEmpty())
        assertTrue(timeline.lines.first().timestampMs >= 0L)
    }
}
