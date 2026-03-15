package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry

object SmbPathResolver {

    fun resolveFileName(entry: SmbEntry): String {
        if (entry.name.isNotBlank()) return entry.name
        val stream = entry.streamUri.orEmpty()
        return stream.substringAfterLast('/', stream)
    }

    fun resolveParentPath(config: SmbConfig, entry: SmbEntry): String {
        if (entry.fullPath.isNotBlank()) {
            return entry.fullPath.substringBeforeLast('/', "")
        }
        val stream = entry.streamUri.orEmpty()
        if (!stream.startsWith("smb://", ignoreCase = true)) return ""
        val afterHost = stream.removePrefix("smb://").substringAfter('/', "")
        val noFile = afterHost.substringBeforeLast('/', "")
        if (config.share.isBlank()) return noFile
        return noFile.removePrefix("${config.share.trim()}/").removePrefix(config.share.trim())
    }

    fun buildExternalLrcPath(config: SmbConfig, entry: SmbEntry): String {
        val parentPath = resolveParentPath(config, entry)
        val fileName = resolveFileName(entry)
        val fileNameWithoutExt = fileName.substringBeforeLast('.', fileName)
        val hostBase = "smb://${config.host.trim()}".trimEnd('/')
        val share = config.share.trim()
        val base = if (share.isBlank()) hostBase else "$hostBase/$share"
        return listOf(base, parentPath, "$fileNameWithoutExt.lrc")
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
}
