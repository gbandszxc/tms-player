package com.github.gbandszxc.tvmediaplayer.domain.repo

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry

interface SmbRepository {
    suspend fun list(config: SmbConfig, path: String): List<SmbEntry>
}
