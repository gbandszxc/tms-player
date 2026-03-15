package com.github.gbandszxc.tvmediaplayer.data.repo

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbFailure
import com.github.gbandszxc.tvmediaplayer.domain.repo.SmbRepository
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JcifsSmbRepository : SmbRepository {

    override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        require(config.host.isNotBlank()) {
            "SMB 主机地址不能为空"
        }

        val currentPath = path.trim('/').ifBlank { config.normalizedPath() }
        val target = resolveTarget(config, currentPath)
        val context = buildContext(config)
        val directory = SmbFile(target.url, context)

        listWithRetry(directory)
            .orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { file -> mapToEntry(file, target.pathPrefix) }
    }

    private fun resolveTarget(config: SmbConfig, currentPath: String): TargetLocation {
        val host = config.host.trim()
        val configuredShare = config.share.trim()

        if (configuredShare.isNotBlank()) {
            val base = "smb://$host/$configuredShare".trimEnd('/')
            val url = if (currentPath.isBlank()) "$base/" else "$base/$currentPath/"
            return TargetLocation(url = url, pathPrefix = currentPath)
        }

        if (currentPath.isBlank()) {
            return TargetLocation(url = "smb://$host/", pathPrefix = "")
        }

        val dynamicShare = currentPath.substringBefore('/')
        val subPath = currentPath.substringAfter('/', "")
        val base = "smb://$host/$dynamicShare".trimEnd('/')
        val url = if (subPath.isBlank()) "$base/" else "$base/$subPath/"
        return TargetLocation(url = url, pathPrefix = currentPath)
    }

    private fun listWithRetry(directory: SmbFile): Array<SmbFile>? {
        val first = runCatching { directory.listFiles() }
        if (first.isSuccess) return first.getOrThrow()

        val firstError = first.exceptionOrNull() ?: return first.getOrNull()
        val failure = SmbFailureMapper.map(firstError)
        val retryable = failure == SmbFailure.TIMEOUT || failure == SmbFailure.HOST_UNREACHABLE
        if (!retryable) throw firstError

        return runCatching { directory.listFiles() }.getOrElse { throw it }
    }

    private fun mapToEntry(file: SmbFile, currentPath: String): SmbEntry? {
        val name = file.name.trimEnd('/').trim()
        if (name.isBlank()) return null

        val fullPath = combinePath(currentPath, name)
        return if (file.isDirectory) {
            SmbEntry(name = name, fullPath = fullPath, isDirectory = true)
        } else {
            if (!isAudioFile(name)) return null
            SmbEntry(
                name = name,
                fullPath = fullPath,
                isDirectory = false,
                streamUri = file.canonicalPath
            )
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

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
            lower.endsWith(".flac") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".ogg")
    }

    private fun combinePath(base: String, child: String): String =
        if (base.isBlank()) child else "$base/$child"

    private data class TargetLocation(val url: String, val pathPrefix: String)
}

