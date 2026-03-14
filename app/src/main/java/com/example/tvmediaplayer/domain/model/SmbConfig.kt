package com.example.tvmediaplayer.domain.model

data class SmbConfig(
    val host: String,
    val share: String,
    val path: String,
    val username: String,
    val password: String,
    val guest: Boolean,
    val smb1Enabled: Boolean = false
) {
    fun normalizedPath(): String = path.trim().replace("\\", "/").trim('/')

    fun rootUrl(): String {
        val base = "smb://${host.trim()}/${share.trim()}"
        val sub = normalizedPath()
        return if (sub.isBlank()) base else "$base/$sub"
    }

    companion object {
        val Empty = SmbConfig(
            host = "",
            share = "",
            path = "",
            username = "",
            password = "",
            guest = true,
            smb1Enabled = false
        )
    }
}
