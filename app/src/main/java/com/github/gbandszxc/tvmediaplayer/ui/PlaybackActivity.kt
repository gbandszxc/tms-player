package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.data.repo.SmbConfigStore
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import com.github.gbandszxc.tvmediaplayer.lyrics.SmbLyricsRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.SmbContextFactory
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class PlaybackActivity : BaseActivity() {

    private companion object {
        private const val MP4_TAG_PROBE_BYTES = 8L * 1024 * 1024
        private const val OGG_TAG_PROBE_BYTES = 2L * 1024 * 1024
        private const val FLAC_MAX_METADATA_BYTES = 32L * 1024 * 1024
        private const val FLAC_POST_METADATA_AUDIO_BYTES = 16L * 1024
    }

    private data class AudioTagInfo(
        val title: String?,
        val artist: String?,
        val albumTitle: String?
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private val lyricsRepository = SmbLyricsRepository()
    private lateinit var configStore: SmbConfigStore

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null
    private var currentArtworkKey: String? = null
    private var currentTagKey: String? = null
    private val tagInfoCache = mutableMapOf<String, AudioTagInfo>()
    private var fallbackConfig: SmbConfig = SmbConfig.Empty

    private lateinit var ivArtwork: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var tvTime: TextView
    private lateinit var pbProgress: SeekBar
    private lateinit var scrollLyrics: ScrollView
    private lateinit var tvLyricContent: TextView
    private lateinit var btnPrevious: Button
    private lateinit var btnPlayPause: Button
    private lateinit var btnNext: Button
    private lateinit var btnLyricsFullscreen: Button
    private lateinit var btnBack: Button

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)
            ) {
                renderPlayerState(player)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configStore = SmbConfigStore(applicationContext)
        setContentView(R.layout.activity_playback)
        bindViews()
        applyUiSettings()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        applyUiSettings()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        savePlaybackSnapshot()
        releaseController()
        super.onStop()
    }

    private fun bindViews() {
        ivArtwork = findViewById(R.id.iv_artwork)
        tvTitle = findViewById(R.id.tv_playback_title)
        tvArtist = findViewById(R.id.tv_playback_artist)
        tvAlbum = findViewById(R.id.tv_playback_album)
        tvTime = findViewById(R.id.tv_playback_time)
        pbProgress = findViewById(R.id.pb_playback)
        scrollLyrics = findViewById(R.id.scroll_lyrics)
        tvLyricContent = findViewById(R.id.tv_lyric_content)
        btnPrevious = findViewById(R.id.btn_prev)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnLyricsFullscreen = findViewById(R.id.btn_lyrics_fullscreen)
        btnBack = findViewById(R.id.btn_back_to_browser)
    }

    private fun bindActions() {
        btnPrevious.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
            renderPlayerState(controller)
        }
        btnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }
        btnLyricsFullscreen.setOnClickListener {
            startActivity(Intent(this, LyricsFullscreenActivity::class.java))
        }
        btnBack.setOnClickListener { finish() }

        pbProgress.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                val controller = mediaController ?: return@setOnKeyListener false
                if (controller.isPlaying) controller.pause() else controller.play()
                renderPlayerState(controller)
                return@setOnKeyListener true
            }
            if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) {
                return@setOnKeyListener false
            }
            val controller = mediaController ?: return@setOnKeyListener false
            val duration = controller.duration
            if (duration <= 0 || duration == C.TIME_UNSET) return@setOnKeyListener true

            val step = seekStepMs(event.repeatCount)
            val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) step else -step
            val target = (controller.currentPosition + delta).coerceIn(0L, duration)
            controller.seekTo(target)
            renderProgress(target, duration)
            renderLyrics(target)
            true
        }

        pbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // 拖动开始时暂停进度自动刷新，避免进度条被播放器覆盖
                progressJob?.cancel()
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val controller = mediaController ?: return
                val duration = controller.duration
                if (duration <= 0 || duration == C.TIME_UNSET) return
                val targetMs = (progress.toLong() * duration) / 1000L
                renderProgress(targetMs, duration)
                renderLyrics(targetMs)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val controller = mediaController ?: run {
                    startProgressTicker(); return
                }
                val duration = controller.duration
                if (duration > 0 && duration != C.TIME_UNSET) {
                    val targetMs = (seekBar.progress.toLong() * duration) / 1000L
                    controller.seekTo(targetMs)
                }
                startProgressTicker()
            }
        })
    }

    private fun applyUiSettings() {
        UiSettingsApplier.applyAll(this)
        tvLyricContent.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            UiSettingsStore.playbackLyricsFontSp(this).toFloat()
        )
        val spacing = UiSettingsStore.playbackLyricsLineSpacing(this).coerceAtLeast(1.0f)
        tvLyricContent.setLineSpacing(0f, spacing)
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(playerListener)
                        renderPlayerState(controller)
                        startProgressTicker()
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun renderPlayerState(player: Player) {
        val title = player.mediaMetadata.title?.toString().orEmpty()
        tvTitle.text = "歌曲：" + if (title.isBlank()) "暂无播放内容" else title

        // 先用 mediaMetadata 回退值填充，等 tag 异步读完后再覆盖
        val artist = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "-" }
        val album = player.mediaMetadata.albumTitle?.toString().orEmpty().ifBlank { "-" }
        tvArtist.text = "艺术家：$artist"
        tvAlbum.text = "专辑：$album"
        if (player.isPlaying) {
            btnPlayPause.text = "暂停"
            btnPlayPause.setBackgroundResource(R.drawable.bg_button_amber)
        } else {
            btnPlayPause.text = "播放"
            btnPlayPause.setBackgroundResource(R.drawable.bg_button_green)
        }

        renderProgress(player.currentPosition, player.duration)
        maybeLoadLyrics(player)
        maybeLoadArtwork(player)
        maybeLoadTagInfo(player)
        renderLyrics(player.currentPosition)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                val player = mediaController
                if (player != null) {
                    renderProgress(player.currentPosition, player.duration)
                    renderLyrics(player.currentPosition)
                }
                delay(300)
            }
        }
    }

    private fun renderProgress(positionMs: Long, durationMs: Long) {
        val safeDuration = if (durationMs <= 0 || durationMs == C.TIME_UNSET) 0L else durationMs
        tvTime.text = "${formatMs(positionMs)} / ${formatMs(safeDuration)}"
        pbProgress.progress = if (safeDuration <= 0L) {
            0
        } else {
            ((positionMs.coerceAtLeast(0L) * 1000L) / safeDuration).toInt().coerceIn(0, 1000)
        }
    }

    private fun maybeLoadLyrics(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null
        PlaybackLyricsCache.get(key)?.let {
            currentTimeline = it
            renderLyrics(player.currentPosition)
            return
        }
        tvLyricContent.text = "歌词加载中..."

        val config = PlaybackConfigStore.current()
        if (config.host.isBlank()) {
            lifecycleScope.launch {
                refreshFallbackConfigIfNeeded()
                if (currentLyricKey == key) {
                    // Trigger a fresh attempt once fallback config is restored.
                    currentLyricKey = null
                }
            }
        }
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val fullPath = mediaItem.mediaId
        val fileName = fullPath.substringAfterLast('/').ifBlank {
            mediaItem.mediaMetadata.title?.toString().orEmpty()
        }
        val entry = SmbEntry(
            name = fileName,
            fullPath = fullPath,
            isDirectory = false,
            streamUri = uri
        )

        lifecycleScope.launch {
            // 先查磁盘缓存
            val diskHit = withContext(Dispatchers.IO) {
                PlaybackLyricsCache.loadFromDisk(applicationContext, key)
            }
            if (diskHit != null) {
                if (currentLyricKey != key) return@launch
                PlaybackLyricsCache.put(key, diskHit)
                currentTimeline = diskHit
                renderLyrics(player.currentPosition)
                return@launch
            }

            // 磁盘未命中，从 SMB 加载
            val timeline = withContext(Dispatchers.IO) {
                loadLyricsWithRetry(entry)
            }
            if (currentLyricKey != key) return@launch
            currentTimeline = timeline
            if (timeline == null || timeline.lines.isEmpty()) {
                tvLyricContent.text = "暂无歌词"
                return@launch
            }
            PlaybackLyricsCache.put(key, timeline)
            PlaybackLyricsCache.saveAsync(applicationContext, key, timeline)
            renderLyrics(player.currentPosition)
        }
    }

    private fun renderLyrics(positionMs: Long) {
        val timeline = currentTimeline ?: return
        if (timeline.lines.isEmpty()) return

        val currentIndex = LrcParser.findCurrentLineIndex(
            lines = timeline.lines,
            playbackPositionMs = positionMs,
            offsetMs = timeline.offsetMs
        )

        val normalColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val highlightColor = ContextCompat.getColor(this, android.R.color.white)
        val builder = SpannableStringBuilder()
        var highlightStart = -1

        timeline.lines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text.ifBlank { "..." })
            val end = builder.length
            if (index == currentIndex) {
                builder.setSpan(ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                highlightStart = start
            } else {
                builder.setSpan(ForegroundColorSpan(normalColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index != timeline.lines.lastIndex) builder.append('\n')
        }
        tvLyricContent.text = builder

        if (highlightStart >= 0) {
            scrollLyrics.post {
                val layout = tvLyricContent.layout ?: return@post
                val lineIndex = layout.getLineForOffset(highlightStart)
                val lineTop = layout.getLineTop(lineIndex)
                val targetY = (lineTop - scrollLyrics.height / 3).coerceAtLeast(0)
                scrollLyrics.smoothScrollTo(0, targetY)
            }
        }
    }

    private fun maybeLoadArtwork(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val artworkKey = mediaCacheKey(mediaItem)
        if (artworkKey == currentArtworkKey) return
        currentArtworkKey = artworkKey
        PlaybackArtworkCache.get(artworkKey)?.let {
            ivArtwork.setImageBitmap(it)
            return
        }
        ivArtwork.setImageResource(R.drawable.default_cover)

        lifecycleScope.launch {
            // 先查磁盘缓存
            val diskHit = withContext(Dispatchers.IO) {
                PlaybackArtworkCache.loadFromDisk(applicationContext, artworkKey)
            }
            if (diskHit != null) {
                if (currentArtworkKey != artworkKey) return@launch
                ivArtwork.setImageBitmap(diskHit)
                PlaybackArtworkCache.put(artworkKey, diskHit)
                return@launch
            }

            // 磁盘未命中，从 SMB 加载
            val bitmap = withContext(Dispatchers.IO) {
                loadArtworkBitmap(resolvePlaybackConfig(), mediaItem)
            }
            if (currentArtworkKey != artworkKey) return@launch
            if (bitmap != null) {
                ivArtwork.setImageBitmap(bitmap)
                PlaybackArtworkCache.put(artworkKey, bitmap)
                PlaybackArtworkCache.saveAsync(applicationContext, artworkKey, bitmap)
            } else {
                ivArtwork.setImageResource(R.drawable.default_cover)
            }
        }
    }

    private fun maybeLoadTagInfo(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val key = mediaCacheKey(mediaItem)
        if (key == currentTagKey) return
        currentTagKey = key

        tagInfoCache[key]?.let { info ->
            applyTagInfo(info)
            return
        }

        lifecycleScope.launch {
            val config = resolvePlaybackConfig()
            val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
            val info = withContext(Dispatchers.IO) { loadAudioTagInfo(mediaUri, config) }
            if (currentTagKey != key) return@launch
            if (info != null) {
                tagInfoCache[key] = info
                applyTagInfo(info)
            }
        }
    }

    private fun applyTagInfo(info: AudioTagInfo) {
        if (!info.title.isNullOrBlank()) {
            tvTitle.text = "歌曲：${info.title}"
        }
        if (!info.artist.isNullOrBlank()) {
            tvArtist.text = "艺术家：${info.artist}"
        }
        if (!info.albumTitle.isNullOrBlank()) {
            tvAlbum.text = "专辑：${info.albumTitle}"
        }
    }

    private fun loadAudioTagInfo(mediaUri: String, config: SmbConfig): AudioTagInfo? = runCatching {
        if (!mediaUri.startsWith("smb://", ignoreCase = true)) return@runCatching null
        val smbFile = SmbFile(mediaUri, SmbContextFactory.build(config))
        val ext = mediaUri.substringAfterLast('.', "").lowercase()
        val suffix = if (ext.isBlank() || ext.length > 8) "tmp" else ext
        val temp = File.createTempFile("tags-", ".$suffix")
        try {
            val fastUsed = copySmbForMetadataProbe(smbFile, temp, suffix, fastPath = true)
            var info = extractAudioTagInfo(temp)
            if (info == null && fastUsed) {
                copySmbForMetadataProbe(smbFile, temp, suffix, fastPath = false)
                info = extractAudioTagInfo(temp)
            }
            info
        } finally {
            temp.delete()
        }
    }.getOrNull()

    private fun loadArtworkBitmap(config: SmbConfig, mediaItem: MediaItem) = runCatching {
        val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty()
        if (artworkUri.isNotBlank()) {
            loadSmbBitmap(artworkUri, config)?.let { return@runCatching it }
        }

        val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        if (mediaUri.startsWith("smb://", ignoreCase = true)) {
            loadEmbeddedArtwork(mediaUri, config)?.let { return@runCatching it }
            loadSiblingArtwork(mediaUri, config)?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun loadSiblingArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val context = SmbContextFactory.build(config)
        val parentPath = mediaSmbUrl.substringBeforeLast('/', "").trimEnd('/') + "/"
        val parentDir = SmbFile(parentPath, context)
        val candidates = listOf(
            "folder.jpg", "folder.png",
            "cover.jpg", "cover.png",
            "front.jpg", "front.png",
        )
        for (name in candidates) {
            val candidate = SmbFile(parentDir, name)
            if (!candidate.exists() || candidate.isDirectory) continue
            SmbFileInputStream(candidate).use { stream ->
                BitmapFactory.decodeStream(stream)?.let { return@runCatching it }
            }
        }
        null
    }.getOrNull()

    private fun loadSmbBitmap(smbUrl: String, config: SmbConfig) = runCatching {
        val smbFile = SmbFile(smbUrl, SmbContextFactory.build(config))
        if (!smbFile.exists() || smbFile.isDirectory) return@runCatching null
        SmbFileInputStream(smbFile).use { stream -> BitmapFactory.decodeStream(stream) }
    }.getOrNull()

    private fun loadEmbeddedArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val smbFile = SmbFile(mediaSmbUrl, SmbContextFactory.build(config))
        val ext = mediaSmbUrl.substringAfterLast('.', "").lowercase()
        val suffix = if (ext.isBlank() || ext.length > 8) "tmp" else ext
        val temp = File.createTempFile("artwork-", ".$suffix")
        try {
            val fastUsed = copySmbForMetadataProbe(smbFile, temp, suffix, fastPath = true)
            var artwork = runCatching { AudioFileIO.read(temp).tag?.firstArtwork }.getOrNull()
            if (artwork == null && fastUsed) {
                copySmbForMetadataProbe(smbFile, temp, suffix, fastPath = false)
                artwork = runCatching { AudioFileIO.read(temp).tag?.firstArtwork }.getOrNull()
            }
            artwork ?: return@runCatching null
            BitmapFactory.decodeByteArray(artwork.binaryData, 0, artwork.binaryData.size)
        } finally {
            temp.delete()
        }
    }.getOrNull()

    private fun copySmbForMetadataProbe(
        smbFile: SmbFile,
        temp: File,
        suffix: String,
        fastPath: Boolean,
    ): Boolean {
        SmbFileInputStream(smbFile).use { input ->
            FileOutputStream(temp, false).use { output ->
                if (!fastPath) {
                    input.copyTo(output)
                    return false
                }
                return copyFastMetadataProbe(input, output, suffix)
            }
        }
    }

    private fun extractAudioTagInfo(temp: File): AudioTagInfo? {
        val tag = runCatching { AudioFileIO.read(temp).tag }.getOrNull() ?: return null
        val title = tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() }
        val artist = tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() }
        val album = tag.getFirst(FieldKey.ALBUM).takeIf { it.isNotBlank() }
        if (title == null && artist == null && album == null) return null
        return AudioTagInfo(
            title = title,
            artist = artist,
            albumTitle = album,
        )
    }

    private fun copyFastMetadataProbe(
        input: InputStream,
        output: OutputStream,
        suffix: String,
    ): Boolean {
        return when (suffix) {
            "mp3" -> {
                copyId3TagRegion(input, output)
                true
            }

            "flac" -> copyFlacMetadataRegion(input, output)

            "m4a", "mp4", "m4b", "aac", "alac" -> {
                copyLimited(input, output, MP4_TAG_PROBE_BYTES)
                true
            }

            "ogg", "opus" -> {
                copyLimited(input, output, OGG_TAG_PROBE_BYTES)
                true
            }

            else -> {
                input.copyTo(output)
                false
            }
        }
    }

    private fun copyFlacMetadataRegion(input: InputStream, output: OutputStream): Boolean {
        val signature = ByteArray(4)
        val signatureRead = input.readFully(signature)
        output.write(signature, 0, signatureRead)
        if (signatureRead < 4) {
            input.copyTo(output)
            return false
        }
        if (
            signature[0] != 'f'.code.toByte() ||
            signature[1] != 'L'.code.toByte() ||
            signature[2] != 'a'.code.toByte() ||
            signature[3] != 'C'.code.toByte()
        ) {
            input.copyTo(output)
            return false
        }

        var copiedMetadataBytes = 0L
        var isLastBlock = false
        val blockHeader = ByteArray(4)
        while (!isLastBlock) {
            val headerRead = input.readFully(blockHeader)
            if (headerRead < 4) return true
            output.write(blockHeader)

            isLastBlock = (blockHeader[0].toInt() and 0x80) != 0
            val blockLength =
                ((blockHeader[1].toInt() and 0xFF) shl 16) or
                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                (blockHeader[3].toInt() and 0xFF)

            if (blockLength > 0) {
                copyExactly(input, output, blockLength.toLong())
                copiedMetadataBytes += blockLength.toLong()
                if (copiedMetadataBytes >= FLAC_MAX_METADATA_BYTES) break
            }
        }

        copyLimited(input, output, FLAC_POST_METADATA_AUDIO_BYTES)
        return true
    }

    private fun copyExactly(input: InputStream, output: OutputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (n <= 0) break
            output.write(buffer, 0, n)
            remaining -= n
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, bytes: Long) {
        copyExactly(input, output, bytes)
    }

    private fun InputStream.readFully(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    /**
     * MP3 专用：只把 ID3v2 tag 区域复制到 output，跳过后续音频数据。
     * ID3v2 封面在文件头部，通常几十到几百 KB，远小于完整音频文件。
     * 若文件没有 ID3v2 header 则回退复制全部内容。
     */
    private fun copyId3TagRegion(input: InputStream, output: OutputStream) {
        val header = ByteArray(10)
        var totalRead = 0
        while (totalRead < 10) {
            val n = input.read(header, totalRead, 10 - totalRead)
            if (n < 0) break
            totalRead += n
        }
        output.write(header, 0, totalRead)
        if (totalRead < 10) { input.copyTo(output); return }
        // 检查 "ID3" 标识
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
            input.copyTo(output); return
        }
        // ID3v2 tag size 使用 syncsafe integer（每字节只用低7位）
        val tagContentSize =
            ((header[6].toInt() and 0x7F) shl 21) or
            ((header[7].toInt() and 0x7F) shl 14) or
            ((header[8].toInt() and 0x7F) shl  7) or
             (header[9].toInt() and 0x7F)
        var remaining = tagContentSize.toLong()
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            output.write(buf, 0, n)
            remaining -= n
        }
        // jaudiotagger 解析 MP3 需要在 tag 后找到音频帧 sync word，
        // 多读 64KB 确保它能找到第一个音频帧（stream 仍在 tag 末尾位置）。
        val audioBuf = ByteArray(65536)
        val audioRead = input.read(audioBuf)
        if (audioRead > 0) output.write(audioBuf, 0, audioRead)
    }

    private fun savePlaybackSnapshot() {
        if (!UiSettingsStore.rememberLastPlayback(this)) return
        val controller = mediaController ?: return
        if (controller.mediaItemCount == 0) return
        val uris = buildList {
            repeat(controller.mediaItemCount) { i ->
                controller.getMediaItemAt(i).localConfiguration?.uri?.toString()?.let(::add)
            }
        }
        val ids = buildList {
            repeat(controller.mediaItemCount) { i ->
                add(controller.getMediaItemAt(i).mediaId)
            }
        }
        if (uris.isEmpty()) return
        LastPlaybackStore.save(
            this,
            LastPlaybackStore.Snapshot(
                queueUris = uris,
                queueMediaIds = ids,
                currentIndex = controller.currentMediaItemIndex,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                title = controller.mediaMetadata.title?.toString().orEmpty()
            )
        )
    }

    private fun releaseController() {
        progressJob?.cancel()
        progressJob = null
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun formatMs(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun seekStepMs(repeatCount: Int): Long {
        return when {
            repeatCount < 5 -> 5_000L
            repeatCount < 12 -> 10_000L
            repeatCount < 25 -> 30_000L
            repeatCount < 40 -> 60_000L
            else -> 90_000L
        }
    }

    private suspend fun loadLyricsWithRetry(entry: SmbEntry): LrcTimeline? {
        repeat(3) { attempt ->
            val timeline = runCatching {
                lyricsRepository.load(resolvePlaybackConfig(), entry)
            }.getOrNull()
            if (timeline != null && timeline.lines.isNotEmpty()) return timeline
            if (attempt < 2) delay(250L * (attempt + 1))
        }
        return null
    }

    private suspend fun resolvePlaybackConfig(): SmbConfig {
        val active = PlaybackConfigStore.current()
        if (active.host.isNotBlank()) return active
        refreshFallbackConfigIfNeeded()
        return fallbackConfig
    }

    private suspend fun refreshFallbackConfigIfNeeded() {
        if (fallbackConfig.host.isNotBlank()) return
        val loaded = runCatching { configStore.loadState().activeConfig }.getOrNull() ?: return
        if (loaded.host.isBlank()) return
        fallbackConfig = loaded
        PlaybackConfigStore.update(loaded)
    }

    private fun mediaCacheKey(mediaItem: MediaItem): String {
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        return if (uri.isNotBlank()) uri else mediaItem.mediaId
    }
}
