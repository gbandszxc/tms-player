package com.github.gbandszxc.tvmediaplayer.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object PlaybackArtworkCache {

    private val memory = object : LruCache<String, Bitmap>(24) {}
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 查内存缓存 */
    fun get(key: String): Bitmap? = memory.get(key)

    /** 仅写内存缓存 */
    fun put(key: String, bitmap: Bitmap) {
        memory.put(key, bitmap)
    }

    /** 异步将 bitmap 以 JPEG 写入磁盘（在 ioScope 中执行） */
    fun saveAsync(context: Context, key: String, bitmap: Bitmap) {
        ioScope.launch {
            runCatching {
                cacheFile(context, key).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        }
    }

    /**
     * 从磁盘读取缓存（同步，调用方负责在 IO 线程调用）。
     * 命中时不会自动放入内存，调用方应自行调用 put()。
     */
    fun loadFromDisk(context: Context, key: String): Bitmap? = runCatching {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /** 清空内存和磁盘缓存 */
    fun clearDisk(context: Context) {
        memory.evictAll()
        artworkCacheDir(context).deleteRecursively()
    }

    /** 返回磁盘缓存占用字节数 */
    fun diskCacheSize(context: Context): Long =
        artworkCacheDir(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    // ---- 内部工具 ----

    private fun artworkCacheDir(context: Context): File =
        File(context.cacheDir, "artwork").also { it.mkdirs() }

    private fun cacheFile(context: Context, key: String): File {
        val hash = key.hashCode()
        val name = if (hash >= 0) "p$hash.jpg" else "n${-hash}.jpg"
        return File(artworkCacheDir(context), name)
    }
}
