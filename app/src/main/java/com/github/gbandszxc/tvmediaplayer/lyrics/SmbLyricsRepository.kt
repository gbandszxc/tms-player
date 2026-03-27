package com.github.gbandszxc.tvmediaplayer.lyrics

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.SmbAudioMetadataProbe
import com.github.gbandszxc.tvmediaplayer.playback.SmbPathResolver
import java.nio.charset.Charset
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class SmbLyricsRepository {

    enum class Status {
        FOUND,
        MISS,
        ERROR
    }

    data class LoadResult(
        val status: Status,
        val timeline: LrcTimeline? = null,
    )

    suspend fun load(config: SmbConfig, entry: SmbEntry): LrcTimeline? {
        return loadDetailed(config, entry).timeline
    }

    suspend fun loadDetailed(config: SmbConfig, entry: SmbEntry): LoadResult = withContext(Dispatchers.IO) {
        if (entry.isDirectory || entry.streamUri.isNullOrBlank()) {
            return@withContext LoadResult(Status.MISS)
        }

        val context = buildContext(config)
        coroutineScope {
            val externalDeferred = async { runCatching { loadExternalLrc(config, entry, context) } }
            // 外置歌词先发起；若未快速命中，再并发内嵌探测。
            val embeddedDeferred = async {
                delay(150L)
                runCatching { loadEmbeddedTimeline(config, entry) }
            }

            var externalDone = false
            var embeddedDone = false
            var hasError = false

            while (!externalDone || !embeddedDone) {
                val (which, result) = select<Pair<Int, Result<LrcTimeline?>>>() {
                    if (!externalDone) {
                        externalDeferred.onAwait { 1 to it }
                    }
                    if (!embeddedDone) {
                        embeddedDeferred.onAwait { 2 to it }
                    }
                }

                when (which) {
                    1 -> externalDone = true
                    2 -> embeddedDone = true
                }

                val timeline = result.getOrNull()
                if (timeline != null && timeline.lines.isNotEmpty()) {
                    if (!externalDone) externalDeferred.cancel()
                    if (!embeddedDone) embeddedDeferred.cancel()
                    return@coroutineScope LoadResult(Status.FOUND, timeline)
                }
                if (result.isFailure) hasError = true
            }

            if (hasError) LoadResult(Status.ERROR) else LoadResult(Status.MISS)
        }
    }

    private fun loadExternalLrc(config: SmbConfig, entry: SmbEntry, context: CIFSContext): LrcTimeline? {
        val candidates = linkedSetOf<String>()
        val stream = entry.streamUri.orEmpty()

        // 策略1：直接把 streamUri 扩展名替换为 .lrc（最快路径）
        if (stream.startsWith("smb://", ignoreCase = true)) {
            candidates.add(stream.substringBeforeLast('.', stream) + ".lrc")
        }
        // 策略2：通过 SmbPathResolver 根据 config + entry 构造路径（share 子目录场景）
        val resolvedPath = SmbPathResolver.buildExternalLrcPath(config, entry)
        if (resolvedPath.isNotBlank()) candidates.add(resolvedPath)

        for (lrcPath in candidates) {
            runCatching {
                val lrcFile = SmbFile(lrcPath, context)
                if (!lrcFile.exists() || lrcFile.isDirectory) return@runCatching
                val timeline = SmbFileInputStream(lrcFile).use { it.readBytes() }
                    .let { LrcParser.parseTimeline(decodeText(it)) }
                if (timeline.lines.isNotEmpty()) return timeline
            }
        }

        // 策略3：精确路径未命中时遍历父目录做模糊匹配（全半角归一化 + 忽略大小写）
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

    private suspend fun loadEmbeddedTimeline(config: SmbConfig, entry: SmbEntry): LrcTimeline? {
        val embedded = SmbAudioMetadataProbe.probe(config, entry.streamUri.orEmpty())?.lyrics
        if (embedded.isNullOrBlank()) return null

        val maybeTimeline = LrcParser.parseTimeline(embedded)
        return if (maybeTimeline.lines.isNotEmpty()) {
            maybeTimeline
        } else {
            LrcTimeline(
                lines = listOf(LyricLine(0, embedded.trim())),
                offsetMs = 0
            )
        }
    }

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
