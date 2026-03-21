package com.github.gbandszxc.tvmediaplayer.ui

import android.content.ComponentName
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcParser
import com.github.gbandszxc.tvmediaplayer.lyrics.LrcTimeline
import com.github.gbandszxc.tvmediaplayer.lyrics.SmbLyricsRepository
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LyricsFullscreenActivity : BaseActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var refreshJob: Job? = null
    private val lyricsRepository = SmbLyricsRepository()

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null

    private lateinit var tvTitle: TextView
    private lateinit var scrollLyrics: ScrollView
    private lateinit var tvLyrics: TextView
    private lateinit var btnClose: Button

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY)
            ) {
                maybeLoadLyrics(player)
                renderLyrics(player.currentPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lyrics_fullscreen)
        tvTitle = findViewById(R.id.tv_fullscreen_title)
        scrollLyrics = findViewById(R.id.scroll_lyrics)
        tvLyrics = findViewById(R.id.tv_fullscreen_lyrics)
        btnClose = findViewById(R.id.btn_close_fullscreen_lyrics)
        btnClose.setOnClickListener { finish() }
        applyUiSettings()
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
        refreshJob?.cancel()
        refreshJob = null
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
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
                        maybeLoadLyrics(controller)
                        renderLyrics(controller.currentPosition)
                        startTicker()
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(this, "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun applyUiSettings() {
        UiSettingsApplier.applyAll(this)
        tvLyrics.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            UiSettingsStore.fullscreenLyricsFontSp(this).toFloat()
        )
        val spacing = UiSettingsStore.fullscreenLyricsLineSpacing(this).coerceAtLeast(1.0f)
        tvLyrics.setLineSpacing(0f, spacing)
    }

    private fun startTicker() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                mediaController?.let { renderLyrics(it.currentPosition) }
                delay(250)
            }
        }
    }

    private fun maybeLoadLyrics(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val title = mediaItem.mediaMetadata.title?.toString().orEmpty()
        tvTitle.text = if (title.isBlank()) "歌词全屏" else "歌词全屏 - $title"

        // key 与 PlaybackActivity 保持一致：uri 优先，否则 mediaId
        val uri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        val key = if (uri.isNotBlank()) uri else mediaItem.mediaId
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null

        // 内存缓存命中
        PlaybackLyricsCache.get(key)?.let {
            currentTimeline = it
            renderLyrics(player.currentPosition)
            return
        }
        tvLyrics.text = "歌词加载中..."

        val fullPath = mediaItem.mediaId
        val entry = SmbEntry(
            name = fullPath.substringAfterLast('/'),
            fullPath = fullPath,
            isDirectory = false,
            streamUri = uri
        )

        lifecycleScope.launch {
            // 磁盘缓存命中
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

            // 从 SMB 加载
            val config = PlaybackConfigStore.current()
            val timeline = withContext(Dispatchers.IO) {
                runCatching { lyricsRepository.load(config, entry) }.getOrNull()
            }
            if (currentLyricKey != key) return@launch
            currentTimeline = timeline
            if (timeline == null || timeline.lines.isEmpty()) {
                tvLyrics.text = "暂无歌词"
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
        tvLyrics.text = builder

        if (highlightStart >= 0) {
            scrollLyrics.post {
                val layout = tvLyrics.layout ?: return@post
                val lineIndex = layout.getLineForOffset(highlightStart)
                val lineTop = layout.getLineTop(lineIndex)
                val targetY = (lineTop - scrollLyrics.height / 3).coerceAtLeast(0)
                scrollLyrics.smoothScrollTo(0, targetY)
            }
        }
    }
}
