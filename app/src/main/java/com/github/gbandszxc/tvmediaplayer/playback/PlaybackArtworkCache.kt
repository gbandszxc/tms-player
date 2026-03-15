package com.github.gbandszxc.tvmediaplayer.playback

import android.graphics.Bitmap
import android.util.LruCache

object PlaybackArtworkCache {
    private val cache = object : LruCache<String, Bitmap>(24) {}

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}
