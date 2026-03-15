package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SmbMediaItemFactoryTest {

    private val factory = SmbMediaItemFactory()

    @Test
    fun `parse title removes extension`() {
        assertEquals("01 - intro", factory.parseTitle("01 - intro.flac"))
    }

    @Test
    fun `parse album uses parent folder`() {
        val config = SmbConfig(
            host = "192.168.1.2",
            share = "Music",
            path = "",
            username = "",
            password = "",
            guest = true
        )
        assertEquals("ACG", factory.parseAlbum(config, "Anime/ACG/01.mp3"))
    }

    @Test
    fun `parse album falls back to share`() {
        val config = SmbConfig(
            host = "192.168.1.2",
            share = "Music",
            path = "",
            username = "",
            password = "",
            guest = true
        )
        assertEquals("Music", factory.parseAlbum(config, "01.mp3"))
    }
}

