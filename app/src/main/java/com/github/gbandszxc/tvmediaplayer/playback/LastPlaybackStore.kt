package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import org.json.JSONArray

object LastPlaybackStore {

    private const val PREF_NAME = "last_playback"
    private const val KEY_QUEUE_URIS = "queue_uris"
    private const val KEY_QUEUE_IDS = "queue_media_ids"
    private const val KEY_INDEX = "current_index"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_TITLE = "current_title"

    data class Snapshot(
        val queueUris: List<String>,
        val queueMediaIds: List<String>,
        val currentIndex: Int,
        val positionMs: Long,
        val title: String
    )

    fun save(context: Context, snapshot: Snapshot) {
        prefs(context).edit()
            .putString(KEY_QUEUE_URIS, JSONArray(snapshot.queueUris).toString())
            .putString(KEY_QUEUE_IDS, JSONArray(snapshot.queueMediaIds).toString())
            .putInt(KEY_INDEX, snapshot.currentIndex)
            .putLong(KEY_POSITION_MS, snapshot.positionMs)
            .putString(KEY_TITLE, snapshot.title)
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val prefs = prefs(context)
        val urisJson = prefs.getString(KEY_QUEUE_URIS, null) ?: return null
        val idsJson = prefs.getString(KEY_QUEUE_IDS, null) ?: return null
        return runCatching {
            val uris = JSONArray(urisJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            val ids = JSONArray(idsJson).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
            if (uris.isEmpty()) return null
            Snapshot(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = prefs.getInt(KEY_INDEX, 0),
                positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                title = prefs.getString(KEY_TITLE, "").orEmpty()
            )
        }.getOrNull()
    }

    fun hasSnapshot(context: Context): Boolean {
        val json = prefs(context).getString(KEY_QUEUE_URIS, null) ?: return false
        return runCatching { JSONArray(json).length() > 0 }.getOrDefault(false)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
