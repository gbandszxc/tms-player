package com.example.tvmediaplayer.playback

import com.example.tvmediaplayer.lyrics.LrcTimeline
import java.util.concurrent.ConcurrentHashMap

object PlaybackLyricsCache {
    private val cache = ConcurrentHashMap<String, LrcTimeline>()

    fun get(key: String): LrcTimeline? = cache[key]

    fun put(key: String, timeline: LrcTimeline) {
        cache[key] = timeline
    }
}
