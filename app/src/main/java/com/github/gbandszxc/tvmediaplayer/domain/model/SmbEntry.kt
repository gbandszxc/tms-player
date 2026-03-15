package com.github.gbandszxc.tvmediaplayer.domain.model

data class SmbEntry(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val streamUri: String? = null
)
