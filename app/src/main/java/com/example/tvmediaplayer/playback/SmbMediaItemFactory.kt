package com.example.tvmediaplayer.playback

import android.net.Uri
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

class SmbMediaItemFactory {

    fun create(config: SmbConfig, queue: List<SmbEntry>): List<MediaItem> {
        val context = buildContext(config)
        val artworkCache = mutableMapOf<String, String?>()
        return queue.map { entry ->
            val directoryPath = entry.fullPath.substringBeforeLast('/', "")
            val artworkUri = artworkCache.getOrPut(directoryPath) {
                resolveArtworkUri(config, directoryPath, context)
            }
            MediaItem.Builder()
                .setUri(requireNotNull(entry.streamUri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(parseTitle(entry.name))
                        .setArtist(config.share.ifBlank { config.host })
                        .setAlbumTitle(parseAlbum(config, entry.fullPath))
                        .setArtworkUri(artworkUri?.let(Uri::parse))
                        .build()
                )
                .build()
        }
    }

    internal fun parseTitle(fileName: String): String = fileName.substringBeforeLast('.', fileName)

    internal fun parseAlbum(config: SmbConfig, fullPath: String): String {
        val parent = fullPath.substringBeforeLast('/', "")
        val fallback = config.share.ifBlank { "SMB" }
        return if (parent.isBlank()) fallback else parent.substringAfterLast('/')
    }

    private fun resolveArtworkUri(config: SmbConfig, directoryPath: String, context: CIFSContext): String? {
        val directoryBase = buildDirectoryBase(config, directoryPath)
        val candidates = listOf("folder.jpg", "cover.jpg", "front.jpg")
        for (candidate in candidates) {
            val artworkUrl = "$directoryBase/$candidate"
            runCatching {
                val file = SmbFile(artworkUrl, context)
                if (file.exists() && !file.isDirectory) {
                    return file.canonicalPath
                }
            }
        }
        return null
    }

    private fun buildDirectoryBase(config: SmbConfig, path: String): String {
        val hostBase = "smb://${config.host.trim()}".trimEnd('/')
        val share = config.share.trim()
        return when {
            share.isNotBlank() && path.isBlank() -> "$hostBase/$share"
            share.isNotBlank() -> "$hostBase/$share/$path"
            path.isBlank() -> hostBase
            else -> "$hostBase/$path"
        }
    }

    private fun buildContext(config: SmbConfig): CIFSContext {
        val properties = Properties().apply {
            setProperty("jcifs.smb.client.responseTimeout", "10000")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "10000")
            if (config.smb1Enabled) {
                setProperty("jcifs.smb.client.minVersion", "SMB1")
            } else {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
            }
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
        }
        val base = BaseContext(PropertyConfiguration(properties))
        return if (config.guest) {
            base.withCredentials(NtlmPasswordAuthenticator("", ""))
        } else {
            base.withCredentials(NtlmPasswordAuthenticator("", config.username.trim(), config.password))
        }
    }
}
