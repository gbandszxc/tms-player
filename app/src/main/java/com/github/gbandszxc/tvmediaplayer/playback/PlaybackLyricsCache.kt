package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import com.github.gbandszxc.tvmediaplayer.lyrics.LyricLine
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object PlaybackLyricsCache {

    private const val DEFAULT_NEGATIVE_TTL_MS = 10 * 60 * 1000L
    private val memory = ConcurrentHashMap<String, LrcTimeline>()
    private val negativeMemory = ConcurrentHashMap<String, Long>()
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 查内存缓存 */
    fun get(key: String): LrcTimeline? = memory[key]

    /** 仅写内存缓存 */
    fun put(key: String, timeline: LrcTimeline) {
        memory[key] = timeline
        negativeMemory.remove(key)
    }

    /** 异步将 timeline 写入磁盘（在 ioScope 中执行，调用方无需切线程） */
    fun saveAsync(context: Context, key: String, timeline: LrcTimeline) {
        ioScope.launch {
            runCatching {
                cacheFile(context, key).writeText(serialize(timeline))
                missFile(context, key).delete()
            }
        }
    }

    /**
     * 从磁盘读取缓存（同步，调用方负责在 IO 线程调用）。
     * 命中时不会自动放入内存，调用方应自行调用 put()。
     */
    fun loadFromDisk(context: Context, key: String): LrcTimeline? = runCatching {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        deserialize(file.readText())
    }.getOrNull()

    fun markMissAsync(context: Context, key: String, ttlMs: Long = DEFAULT_NEGATIVE_TTL_MS) {
        val expiresAt = System.currentTimeMillis() + ttlMs
        negativeMemory[key] = expiresAt
        ioScope.launch {
            runCatching {
                missFile(context, key).writeText(expiresAt.toString())
            }
        }
    }

    fun clearMiss(context: Context, key: String) {
        negativeMemory.remove(key)
        runCatching { missFile(context, key).delete() }
    }

    fun isMissCached(context: Context, key: String): Boolean {
        val now = System.currentTimeMillis()
        val memoryExpiresAt = negativeMemory[key]
        if (memoryExpiresAt != null) {
            if (memoryExpiresAt > now) return true
            negativeMemory.remove(key)
        }

        val file = missFile(context, key)
        if (!file.exists()) return false
        val expiresAt = file.readText().trim().toLongOrNull() ?: run {
            file.delete()
            return false
        }
        return if (expiresAt > now) {
            negativeMemory[key] = expiresAt
            true
        } else {
            file.delete()
            false
        }
    }

    /** 清空内存和磁盘缓存 */
    fun clearDisk(context: Context) {
        memory.clear()
        negativeMemory.clear()
        lyricsCacheDir(context).deleteRecursively()
    }

    /** 返回磁盘缓存占用字节数 */
    fun diskCacheSize(context: Context): Long =
        lyricsCacheDir(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    // ---- 内部工具 ----

    private fun lyricsCacheDir(context: Context): File =
        File(context.cacheDir, "lyrics").also { it.mkdirs() }

    private fun cacheFile(context: Context, key: String): File {
        val hash = key.hashCode()
        val name = if (hash >= 0) "p$hash.json" else "n${-hash}.json"
        return File(lyricsCacheDir(context), name)
    }

    private fun missFile(context: Context, key: String): File {
        val hash = key.hashCode()
        val name = if (hash >= 0) "p$hash.miss" else "n${-hash}.miss"
        return File(lyricsCacheDir(context), name)
    }

    private fun serialize(timeline: LrcTimeline): String {
        val arr = JSONArray().also { a ->
            timeline.lines.forEach { line ->
                a.put(JSONObject().apply {
                    put("t", line.timestampMs)
                    put("s", line.text)
                })
            }
        }
        return JSONObject().apply {
            put("offset", timeline.offsetMs)
            put("lines", arr)
        }.toString()
    }

    private fun deserialize(json: String): LrcTimeline {
        val obj = JSONObject(json)
        val offsetMs = obj.getLong("offset")
        val arr = obj.getJSONArray("lines")
        val lines = (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            LyricLine(timestampMs = item.getLong("t"), text = item.getString("s"))
        }
        return LrcTimeline(lines = lines, offsetMs = offsetMs)
    }
}
