package com.github.gbandszxc.tvmediaplayer.playback

import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import java.util.concurrent.ConcurrentHashMap

object PlaybackLyricsCache {
    private val cache = ConcurrentHashMap<String, LrcTimeline>()

    fun get(key: String): LrcTimeline? = cache[key]

    fun put(key: String, timeline: LrcTimeline) {
        cache[key] = timeline
    }
}
