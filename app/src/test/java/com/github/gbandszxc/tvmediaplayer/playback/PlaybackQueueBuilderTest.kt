package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueBuilderTest {

    @Test
    fun `filters out directory and empty uri`() {
        val entries = listOf(
            SmbEntry(name = "dir", fullPath = "dir", isDirectory = true),
            SmbEntry(name = "a.mp3", fullPath = "a.mp3", isDirectory = false, streamUri = "smb://a.mp3"),
            SmbEntry(name = "b.flac", fullPath = "b.flac", isDirectory = false, streamUri = null)
        )

        val queue = PlaybackQueueBuilder.fromDirectory(entries)

        assertEquals(1, queue.size)
        assertEquals("a.mp3", queue.first().name)
    }

    @Test
    fun `returns matched start index`() {
        val queue = listOf(
            SmbEntry(name = "a.mp3", fullPath = "a.mp3", isDirectory = false, streamUri = "smb://a.mp3"),
            SmbEntry(name = "b.mp3", fullPath = "b.mp3", isDirectory = false, streamUri = "smb://b.mp3")
        )

        val index = PlaybackQueueBuilder.startIndex(queue, queue[1])

        assertEquals(1, index)
    }
}

