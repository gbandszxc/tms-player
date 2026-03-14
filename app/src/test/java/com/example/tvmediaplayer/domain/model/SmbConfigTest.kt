package com.example.tvmediaplayer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbConfigTest {

    @Test
    fun `normalized path trims slash and backslash`() {
        val config = SmbConfig(
            host = "192.168.1.2",
            share = "Music",
            path = "\\A/B//",
            username = "",
            password = "",
            guest = true
        )

        assertEquals("A/B", config.normalizedPath())
    }

    @Test
    fun `root url appends normalized path`() {
        val config = SmbConfig(
            host = "192.168.1.2",
            share = "Music",
            path = "/A/B/",
            username = "",
            password = "",
            guest = true
        )

        assertEquals("smb://192.168.1.2/Music/A/B", config.rootUrl())
    }

    @Test
    fun `root url supports empty share`() {
        val config = SmbConfig(
            host = "192.168.1.2",
            share = "",
            path = "",
            username = "",
            password = "",
            guest = true
        )

        assertEquals("smb://192.168.1.2", config.rootUrl())
    }
}
