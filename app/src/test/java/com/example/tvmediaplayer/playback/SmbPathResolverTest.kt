package com.example.tvmediaplayer.playback

import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import org.junit.Assert.assertEquals
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
}
