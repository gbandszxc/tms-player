package com.example.tvmediaplayer.lyrics

import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.playback.SmbPathResolver
import java.io.File
import java.nio.charset.Charset
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class SmbLyricsRepository {

    suspend fun load(config: SmbConfig, entry: SmbEntry): LrcTimeline? = withContext(Dispatchers.IO) {
        if (entry.isDirectory || entry.streamUri.isNullOrBlank()) return@withContext null

        val context = buildContext(config)
        val external = loadExternalLrc(config, entry, context)
        if (external != null && external.lines.isNotEmpty()) return@withContext external

        val embedded = loadEmbeddedLyrics(context, entry)
        if (embedded.isNullOrBlank()) return@withContext null

        val maybeTimeline = LrcParser.parseTimeline(embedded)
        if (maybeTimeline.lines.isNotEmpty()) maybeTimeline else LrcTimeline(
            lines = listOf(LyricLine(0, embedded.trim())),
            offsetMs = 0
        )
    }

    private fun loadExternalLrc(config: SmbConfig, entry: SmbEntry, context: CIFSContext): LrcTimeline? {
        val candidates = linkedSetOf<String>()
        val stream = entry.streamUri.orEmpty()
        if (stream.startsWith("smb://", ignoreCase = true)) {
            val lrcByUri = stream.substringBeforeLast('.', stream) + ".lrc"
            candidates.add(lrcByUri)
        }
        candidates.add(SmbPathResolver.buildExternalLrcPath(config, entry))

        for (lrcPath in candidates) {
            val lrcFile = SmbFile(lrcPath, context)
            if (!lrcFile.exists() || lrcFile.isDirectory) continue
            val bytes = SmbFileInputStream(lrcFile).use { it.readBytes() }
            val content = decodeText(bytes)
            val timeline = LrcParser.parseTimeline(content)
            if (timeline.lines.isNotEmpty()) return timeline
        }
        return null
    }

    private fun loadEmbeddedLyrics(context: CIFSContext, entry: SmbEntry): String? {
        val smbFile = SmbFile(requireNotNull(entry.streamUri), context)
        val tempFile = File.createTempFile("lyrics-", ".tmp")
        return try {
            SmbFileInputStream(smbFile).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tag ?: return null
            tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotBlank() }
        } finally {
            tempFile.delete()
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return bytes.toString(Charsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return bytes.toString(Charsets.UTF_16BE)
            }
        }
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            bytes.toString(Charset.forName("GB18030"))
        } else {
            utf8
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
