package com.github.gbandszxc.tvmediaplayer.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import java.io.IOException
import java.util.Properties
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlin.math.min

class SmbDataSource(
    private val configProvider: () -> SmbConfig
) : BaseDataSource(false) {

    private var dataSpec: DataSpec? = null
    private var randomAccessFile: SmbRandomAccessFile? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        if (!uri.scheme.equals("smb", ignoreCase = true)) {
            throw PlaybackException(
                "Unsupported URI scheme: ${uri.scheme}",
                null,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            )
        }
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec

        try {
            val context = buildContext(configProvider())
            val file = SmbFile(uri.toString(), context)
            val raf = SmbRandomAccessFile(file, "r")
            randomAccessFile = raf

            if (dataSpec.position > 0) {
                raf.seek(dataSpec.position)
            }

            val fileLength = raf.length()
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - dataSpec.position
            }

            if (bytesRemaining < 0) {
                throw IOException("Invalid read range: position=${dataSpec.position}, length=$fileLength")
            }
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            throw IOException("Failed to open SMB stream: $uri", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val raf = randomAccessFile ?: return C.RESULT_END_OF_INPUT
        val bytesToRead = min(bytesRemaining, length.toLong()).toInt()
        val bytesRead = raf.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT

        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri() = dataSpec?.uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } finally {
            randomAccessFile = null
            dataSpec = null
            bytesRemaining = 0
            if (opened) {
                opened = false
                transferEnded()
            }
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

    class Factory(
        private val configProvider: () -> SmbConfig
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(configProvider)
    }
}
