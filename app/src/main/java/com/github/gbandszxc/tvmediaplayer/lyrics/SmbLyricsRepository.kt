package com.github.gbandszxc.tvmediaplayer.lyrics

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.SmbPathResolver
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

        // 外置 .lrc 优先；异常不向上传播，确保失败后仍能降级到内嵌歌词
        val external = runCatching { loadExternalLrc(config, entry, context) }.getOrNull()
        if (external != null && external.lines.isNotEmpty()) return@withContext external

        val embedded = runCatching { loadEmbeddedLyrics(context, entry) }.getOrNull()
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

        // 策略1：直接把 streamUri 扩展名换成 .lrc（最快路径）
        if (stream.startsWith("smb://", ignoreCase = true)) {
            candidates.add(stream.substringBeforeLast('.', stream) + ".lrc")
        }
        // 策略2：通过 SmbPathResolver 根据 config + entry 构造路径（share 子目录场景）
        val resolvedPath = SmbPathResolver.buildExternalLrcPath(config, entry)
        if (resolvedPath.isNotBlank()) candidates.add(resolvedPath)

        for (lrcPath in candidates) {
            // 每个候选独立 catch，避免单个路径异常中断整个链路
            runCatching {
                val lrcFile = SmbFile(lrcPath, context)
                if (!lrcFile.exists() || lrcFile.isDirectory) return@runCatching
                val timeline = SmbFileInputStream(lrcFile).use { it.readBytes() }
                    .let { LrcParser.parseTimeline(decodeText(it)) }
                if (timeline.lines.isNotEmpty()) return timeline
            }
        }

        // 策略3：精确路径未命中时遍历父目录做模糊匹配。
        // 常见场景：语音识别/标注工具在相同文件名中混用了全角/半角字符
        // （如 MP3 用 ！ U+FF01，而 LRC 用 ! U+0021），导致精确路径 exists=false。
        // normalizeWidth 将全角 ASCII 统一转半角后再比较，同时忽略大小写。
        if (stream.startsWith("smb://", ignoreCase = true)) {
            val baseName = stream.substringAfterLast('/').substringBeforeLast('.')
            val parentUrl = stream.substringBeforeLast('/') + "/"
            runCatching {
                val normalizedBase = normalizeWidth(baseName)
                val lrcFile = SmbFile(parentUrl, context).listFiles()?.firstOrNull {
                    val name = it.name.trimEnd('/')
                    name.endsWith(".lrc", ignoreCase = true) &&
                        normalizeWidth(name.substringBeforeLast('.')).equals(normalizedBase, ignoreCase = true)
                }
                if (lrcFile != null) {
                    val timeline = SmbFileInputStream(lrcFile).use { it.readBytes() }
                        .let { LrcParser.parseTimeline(decodeText(it)) }
                    if (timeline.lines.isNotEmpty()) return timeline
                }
            }
        }

        return null
    }

    private fun loadEmbeddedLyrics(context: CIFSContext, entry: SmbEntry): String? {
        val smbFile = SmbFile(requireNotNull(entry.streamUri), context)
        // jaudiotagger 根据文件扩展名选解析器，必须使用真实扩展名，.tmp 会导致 CannotReadException
        val ext = entry.streamUri!!.substringAfterLast('.', "").lowercase()
            .let { if (it.isBlank() || it.length > 8) "mp3" else it }
        val tempFile = File.createTempFile("lyrics-", ".$ext")
        return try {
            SmbFileInputStream(smbFile).use { it.copyTo(tempFile.outputStream()) }
            val tag = AudioFileIO.read(tempFile).tag ?: return null
            tag.getFirst(FieldKey.LYRICS).takeIf { it.isNotBlank() }
        } finally {
            tempFile.delete()
        }
    }

    /** 全角 ASCII（U+FF01‥U+FF5E）→ 半角（U+0021‥U+007E），用于文件名模糊匹配 */
    private fun normalizeWidth(s: String) = s.map { c ->
        if (c.code in 0xFF01..0xFF5E) (c.code - 0xFF01 + 0x0021).toChar() else c
    }.joinToString("")

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return bytes.toString(Charsets.UTF_16LE)
            if (b0 == 0xFE && b1 == 0xFF) return bytes.toString(Charsets.UTF_16BE)
        }
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) bytes.toString(Charset.forName("GB18030")) else utf8
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
