package com.example.tvmediaplayer.data.repo

import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.domain.repo.SmbRepository
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JcifsSmbRepository : SmbRepository {

    override suspend fun list(config: SmbConfig, path: String): List<SmbEntry> = withContext(Dispatchers.IO) {
        require(config.host.isNotBlank() && config.share.isNotBlank()) {
            "SMB 主机地址和共享名不能为空"
        }

        val currentPath = path.trim('/').ifBlank { config.normalizedPath() }
        val url = buildDirectoryUrl(config, currentPath)
        val context = buildContext(config)
        val directory = SmbFile(url, context)

        try {
            directory.listFiles()
                .orEmpty()
                .sortedBy { it.name.lowercase() }
                .mapNotNull { file -> mapToEntry(file, currentPath) }
        } catch (ex: SmbException) {
            throw ex
        }
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

    private fun buildDirectoryUrl(config: SmbConfig, path: String): String {
        val base = "smb://${config.host.trim()}/${config.share.trim()}"
        val withPath = if (path.isBlank()) base else "$base/$path"
        return "${withPath.trimEnd('/')}/"
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
            base.withCredentials(
                NtlmPasswordAuthenticator(
                    "",
                    config.username.trim(),
                    config.password
                )
            )
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
}

