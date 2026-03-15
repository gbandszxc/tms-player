package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig

object PlaybackConfigStore {
    @Volatile
    private var activeConfig: SmbConfig = SmbConfig.Empty

    fun update(config: SmbConfig) {
        activeConfig = config
    }

    fun current(): SmbConfig = activeConfig
}
