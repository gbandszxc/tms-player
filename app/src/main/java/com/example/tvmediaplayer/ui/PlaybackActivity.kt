package com.example.tvmediaplayer.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.tvmediaplayer.R
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.lyrics.LrcParser
import com.example.tvmediaplayer.lyrics.LrcTimeline
import com.example.tvmediaplayer.lyrics.SmbLyricsRepository
import com.example.tvmediaplayer.playback.PlaybackConfigStore
import com.example.tvmediaplayer.playback.PlaybackService
import com.example.tvmediaplayer.playback.SmbContextFactory
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO

class PlaybackActivity : FragmentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private val lyricsRepository = SmbLyricsRepository()

    private var currentTimeline: LrcTimeline? = null
    private var currentLyricKey: String? = null
    private var currentArtworkKey: String? = null

    private lateinit var ivArtwork: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var tvTime: TextView
    private lateinit var pbProgress: SeekBar
    private lateinit var tvLyricPrev: TextView
    private lateinit var tvLyricCurrent: TextView
    private lateinit var tvLyricNext: TextView
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
        setContentView(R.layout.activity_playback)
        bindViews()
        bindActions()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
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
        tvLyricPrev = findViewById(R.id.tv_lyric_prev)
        tvLyricCurrent = findViewById(R.id.tv_lyric_current)
        tvLyricNext = findViewById(R.id.tv_lyric_next)
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

        val artist = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "-" }
        val album = player.mediaMetadata.albumTitle?.toString().orEmpty().ifBlank { "-" }
        tvArtist.text = "艺术家：$artist"
        tvAlbum.text = "专辑：$album"
        btnPlayPause.text = if (player.isPlaying) "暂停" else "播放"

        renderProgress(player.currentPosition, player.duration)
        maybeLoadLyrics(player)
        maybeLoadArtwork(player)
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
        val key = mediaItem.mediaId + "|" + mediaItem.localConfiguration?.uri
        if (key == currentLyricKey) return
        currentLyricKey = key
        currentTimeline = null
        tvLyricPrev.text = ""
        tvLyricCurrent.text = "歌词加载中..."
        tvLyricNext.text = ""

        val config = PlaybackConfigStore.current()
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
            val timeline = withContext(Dispatchers.IO) {
                runCatching { lyricsRepository.load(config, entry) }.getOrNull()
            }
            if (currentLyricKey != key) return@launch
            currentTimeline = timeline
            if (timeline == null || timeline.lines.isEmpty()) {
                tvLyricCurrent.text = "暂无歌词"
                tvLyricPrev.text = ""
                tvLyricNext.text = ""
                return@launch
            }
            renderLyrics(player.currentPosition)
        }
    }

    private fun renderLyrics(positionMs: Long) {
        val timeline = currentTimeline ?: return
        val index = LrcParser.findCurrentLineIndex(
            lines = timeline.lines,
            playbackPositionMs = positionMs,
            offsetMs = timeline.offsetMs
        )
        if (index < 0) {
            tvLyricPrev.text = ""
            tvLyricCurrent.text = "..."
            tvLyricNext.text = timeline.lines.firstOrNull()?.text.orEmpty()
            return
        }
        tvLyricPrev.text = if (index > 0) timeline.lines[index - 1].text else ""
        tvLyricCurrent.text = timeline.lines[index].text.ifBlank { "..." }
        tvLyricNext.text = timeline.lines.getOrNull(index + 1)?.text.orEmpty()
    }

    private fun maybeLoadArtwork(player: Player) {
        val mediaItem = player.currentMediaItem ?: return
        val artworkKey = mediaItem.mediaId + "|" + mediaItem.localConfiguration?.uri
        if (artworkKey == currentArtworkKey) return
        currentArtworkKey = artworkKey
        val config = PlaybackConfigStore.current()
        ivArtwork.setImageResource(R.drawable.ic_launcher_foreground)

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadArtworkBitmap(config, mediaItem)
            }
            if (currentArtworkKey != artworkKey) return@launch
            if (bitmap != null) {
                ivArtwork.setImageBitmap(bitmap)
            } else {
                ivArtwork.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }

    private fun loadArtworkBitmap(config: SmbConfig, mediaItem: MediaItem) = runCatching {
        val artworkUri = mediaItem.mediaMetadata.artworkUri?.toString().orEmpty()
        if (artworkUri.isNotBlank()) {
            loadSmbBitmap(artworkUri, config)?.let { return@runCatching it }
        }

        val mediaUri = mediaItem.localConfiguration?.uri?.toString().orEmpty()
        if (mediaUri.startsWith("smb://", ignoreCase = true)) {
            loadSiblingArtwork(mediaUri, config)?.let { return@runCatching it }
            loadEmbeddedArtwork(mediaUri, config)?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun loadSiblingArtwork(mediaSmbUrl: String, config: SmbConfig) = runCatching {
        val context = SmbContextFactory.build(config)
        val parentPath = mediaSmbUrl.substringBeforeLast('/', "").trimEnd('/') + "/"
        val parentDir = SmbFile(parentPath, context)
        val candidates = listOf("folder.jpg", "cover.jpg", "front.jpg")
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
        val suffix = mediaSmbUrl.substringAfterLast('.', "").lowercase().let {
            if (it.isBlank() || it.length > 8) ".tmp" else ".$it"
        }
        val temp = File.createTempFile("artwork-", suffix)
        try {
            SmbFileInputStream(smbFile).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            val artwork = AudioFileIO.read(temp).tag?.firstArtwork ?: return@runCatching null
            BitmapFactory.decodeByteArray(artwork.binaryData, 0, artwork.binaryData.size)
        } finally {
            temp.delete()
        }
    }.getOrNull()

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
}
