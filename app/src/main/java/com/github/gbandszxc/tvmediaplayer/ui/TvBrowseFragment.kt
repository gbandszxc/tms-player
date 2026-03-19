package com.github.gbandszxc.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbConfig
import com.github.gbandszxc.tvmediaplayer.domain.model.SmbEntry
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackQueueBuilder
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackConfigStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackService
import com.github.gbandszxc.tvmediaplayer.playback.SmbMediaItemFactory
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvBrowseFragment : Fragment() {

    private val viewModel by viewModels<TvBrowserViewModel> {
        TvBrowserViewModel.factory(requireContext().applicationContext)
    }

    private val mediaItemFactory by lazy { SmbMediaItemFactory() }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private lateinit var panelConnection: View
    private lateinit var btnBackTop: Button
    private lateinit var btnSettings: Button
    private lateinit var tvConnection: TextView
    private lateinit var tvSavedCount: TextView
    private lateinit var btnManage: Button
    private lateinit var tvPath: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnRetry: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnPlayAll: Button
    private lateinit var btnPlayShuffle: Button
    private lateinit var btnNowPlaying: Button
    private lateinit var filesContainer: LinearLayout
    private val browsePlayerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (
                events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED)
            ) {
                updateNowPlayingButton()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_tv_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        bindActions(view)
        collectState()
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun bindViews(root: View) {
        panelConnection = root.findViewById(R.id.panel_connection)
        btnBackTop = root.findViewById(R.id.btn_back_top)
        btnSettings = root.findViewById(R.id.btn_settings)
        tvConnection = root.findViewById(R.id.tv_connection)
        tvSavedCount = root.findViewById(R.id.tv_saved_count)
        btnManage = root.findViewById(R.id.btn_manage)
        tvPath = root.findViewById(R.id.tv_path)
        btnRefresh = root.findViewById(R.id.btn_refresh)
        btnRetry = root.findViewById(R.id.btn_retry)
        tvStatus = root.findViewById(R.id.tv_status)
        btnPlayAll = root.findViewById(R.id.btn_play_all)
        btnPlayShuffle = root.findViewById(R.id.btn_play_shuffle)
        btnNowPlaying = root.findViewById(R.id.btn_now_playing)
        filesContainer = root.findViewById(R.id.container_files)
    }

    private fun bindActions(root: View) {
        btnBackTop.setOnClickListener { navigateUpDirectory() }
        btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        btnManage.setOnClickListener { showConnectionManagerDialog() }
        btnRefresh.setOnClickListener { viewModel.loadCurrentPath() }
        btnRetry.setOnClickListener { viewModel.loadCurrentPath() }
        btnPlayAll.setOnClickListener { playDirectory(shuffle = false) }
        btnPlayShuffle.setOnClickListener { playDirectory(shuffle = true) }
        btnNowPlaying.setOnClickListener {
            if (mediaController?.currentMediaItem != null) {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            } else {
                resumeLastPlayback()
            }
        }

        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> navigateUpDirectory()
                KeyEvent.KEYCODE_MENU -> {
                    showConnectionManagerDialog()
                    true
                }
                else -> false
            }
        }
        updateNowPlayingButton()
    }

    private fun collectState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                render(state)
                state.toast?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    viewModel.consumeToast()
                }
            }
        }
    }

    private fun render(state: TvBrowserState) {
        val showConnectionSection = state.currentPath.isBlank()
        panelConnection.visibility = if (showConnectionSection) View.VISIBLE else View.GONE

        btnBackTop.isEnabled = state.currentPath.isNotBlank()
        btnBackTop.alpha = if (btnBackTop.isEnabled) 1f else 0.55f

        tvConnection.text = "当前连接：${configText(state.config)}"
        tvSavedCount.text = "已保存连接：${state.savedConnections.size} 个"

        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        tvPath.text = "当前路径：$pathLabel"

        btnRetry.visibility = if (state.error != null) View.VISIBLE else View.GONE
        when {
            state.error != null -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = state.error
            }
            state.loading -> {
                tvStatus.visibility = View.VISIBLE
                tvStatus.text = "加载中..."
            }
            else -> tvStatus.visibility = View.GONE
        }

        val displayEntries = buildList {
            if (state.currentPath.isNotBlank()) {
                add(SmbEntry(name = "..", fullPath = state.currentPath, isDirectory = true))
            }
            addAll(state.entries)
        }
        renderFileItems(displayEntries)
        ensureBrowseFocus(displayEntries)
    }

    private fun renderFileItems(entries: List<SmbEntry>) {
        filesContainer.removeAllViews()
        entries.forEach { entry ->
            val itemView = layoutInflater.inflate(R.layout.item_file_entry, filesContainer, false)
            val tvTag: TextView = itemView.findViewById(R.id.tv_tag)
            val tvName: TextView = itemView.findViewById(R.id.tv_name)

            tvTag.text = if (entry.isDirectory) "📁" else "🎵"
            tvTag.background = null
            tvName.text = entry.name

            itemView.setOnClickListener { onFileClicked(entry) }
            filesContainer.addView(itemView)
        }
    }

    private fun ensureBrowseFocus(entries: List<SmbEntry>) {
        filesContainer.post {
            val root = view ?: return@post
            val focused = root.findFocus()
            if (focused != null && focused !== root) return@post

            if (entries.isNotEmpty()) {
                filesContainer.getChildAt(0)?.requestFocus()
            } else {
                btnBackTop.requestFocus()
            }
        }
    }

    private fun onFileClicked(entry: SmbEntry) {
        if (entry.isDirectory) {
            viewModel.enterDirectory(entry)
            return
        }
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        val startIndex = PlaybackQueueBuilder.startIndex(queue, entry)
        playQueue(queue, startIndex, shuffle = false)
    }

    private fun navigateUpDirectory(): Boolean {
        if (viewModel.state.value.currentPath.isBlank()) return false
        viewModel.enterDirectory(SmbEntry("..", viewModel.state.value.currentPath, true))
        return true
    }

    private fun playDirectory(shuffle: Boolean) {
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        playQueue(queue, startIndex = 0, shuffle = shuffle)
    }

    private fun playQueue(queue: List<SmbEntry>, startIndex: Int, shuffle: Boolean) {
        if (queue.isEmpty()) {
            Toast.makeText(requireContext(), "当前目录没有可播放音频", Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(requireContext(), "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        PlaybackConfigStore.update(config)
        lifecycleScope.launch {
            runCatching {
                val targetEntry = queue[startIndex.coerceIn(0, queue.lastIndex)]
                val targetUri = targetEntry.streamUri.orEmpty()
                val queueUris = queue.mapNotNull { it.streamUri }
                val existingUris = currentQueueUris(controller)

                if (existingUris == queueUris && controller.currentMediaItem?.localConfiguration?.uri.toString() == targetUri) {
                    if (!controller.isPlaying) {
                        controller.play()
                    }
                    return@runCatching
                }

                if (existingUris == queueUris) {
                    controller.setShuffleModeEnabled(shuffle)
                    controller.seekToDefaultPosition(startIndex.coerceIn(0, controller.mediaItemCount - 1))
                    controller.play()
                    return@runCatching
                }

                val mediaItems = withContext(Dispatchers.IO) {
                    mediaItemFactory.create(config, queue)
                }
                controller.setShuffleModeEnabled(shuffle)
                controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
                controller.prepare()
                controller.play()
            }.onSuccess {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            }.onFailure { ex ->
                Toast.makeText(
                    requireContext(),
                    "播放失败：${ex.message ?: "请检查 SMB 连接与账号"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun ensureController() {
        if (mediaController != null || controllerFuture != null) return

        val token = SessionToken(
            requireContext(),
            ComponentName(requireContext(), PlaybackService::class.java)
        )
        val future = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(browsePlayerListener)
                        updateNowPlayingButton()
                    }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(requireContext(), "播放器连接失败", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseController() {
        mediaController?.removeListener(browsePlayerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        updateNowPlayingButton()
    }

    private fun updateNowPlayingButton() {
        val controller = mediaController
        val hasActivePlaying = controller?.currentMediaItem != null

        if (hasActivePlaying) {
            btnNowPlaying.visibility = View.VISIBLE
            val title = controller?.mediaMetadata?.title?.toString().orEmpty()
            btnNowPlaying.text = if (title.isBlank()) "回到当前播放" else "回到当前播放：$title"
            return
        }

        val ctx = context ?: return
        if (UiSettingsStore.rememberLastPlayback(ctx) && LastPlaybackStore.hasSnapshot(ctx)) {
            val snapshot = LastPlaybackStore.load(ctx)
            if (snapshot != null) {
                btnNowPlaying.visibility = View.VISIBLE
                btnNowPlaying.text = if (snapshot.title.isBlank()) "继续上次播放" else "继续上次播放：${snapshot.title}"
                return
            }
        }

        btnNowPlaying.visibility = View.GONE
    }

    private fun currentQueueUris(controller: MediaController): List<String> {
        return buildList {
            repeat(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).localConfiguration?.uri?.toString()?.let(::add)
            }
        }
    }

    private fun resumeLastPlayback() {
        val context = requireContext()
        val snapshot = LastPlaybackStore.load(context) ?: return
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(context, "播放器初始化中，请稍后重试", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        PlaybackConfigStore.update(config)

        lifecycleScope.launch {
            runCatching {
                val mediaItems = snapshot.queueUris.mapIndexed { i, uri ->
                    val mediaId = snapshot.queueMediaIds.getOrElse(i) { "" }
                    val title = mediaId.substringAfterLast('/').substringBeforeLast('.')
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaId(mediaId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title.ifBlank { null })
                                .build()
                        )
                        .build()
                }
                val index = snapshot.currentIndex.coerceIn(0, mediaItems.lastIndex)
                controller.setMediaItems(mediaItems, index, snapshot.positionMs)
                controller.prepare()
            }.onSuccess {
                startActivity(Intent(requireContext(), PlaybackActivity::class.java))
            }.onFailure { ex ->
                Toast.makeText(
                    context,
                    "恢复播放失败：${ex.message ?: "请检查 SMB 连接"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showConnectionManagerDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("连接管理")
            .setMessage("请选择操作")
            .setPositiveButton("编辑当前连接") { _, _ -> showConfigDialog(false) }
            .setNeutralButton("新建连接") { _, _ -> showConfigDialog(true) }
            .setNegativeButton("切换连接") { _, _ -> showSwitchDialog() }
            .show()
    }

    private fun showSwitchDialog() {
        val saved = viewModel.state.value.savedConnections
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), "还没有已保存连接，请先保存一个连接", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = saved.map { "${it.name}（${it.config.host}）" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("切换 SMB 连接")
            .setItems(labels) { _, which ->
                viewModel.switchConnection(saved[which].id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showConfigDialog(saveAsNewDefault: Boolean) {
        val current = viewModel.state.value.config
        val context = requireContext()

        val nameInput = EditText(context).apply {
            hint = "连接名称，例如 客厅 NAS"
            typeface = AppFonts.regular(context)
            val active = viewModel.state.value.savedConnections
                .firstOrNull { it.id == viewModel.state.value.activeConnectionId }
            setText(active?.name.orEmpty())
        }
        val hostInput = EditText(context).apply {
            hint = "SMB 服务器地址，例如 192.168.0.10"
            typeface = AppFonts.regular(context)
            setText(current.host)
        }
        val shareInput = EditText(context).apply {
            hint = "共享名（可留空，留空显示所有共享）"
            typeface = AppFonts.regular(context)
            setText(current.share)
        }
        val pathInput = EditText(context).apply {
            hint = "子路径（可留空）"
            typeface = AppFonts.regular(context)
            setText(current.path)
        }
        val userInput = EditText(context).apply {
            hint = "用户名（访客可留空）"
            typeface = AppFonts.regular(context)
            setText(current.username)
        }
        val passInput = EditText(context).apply {
            hint = "密码（访客可留空）"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = AppFonts.regular(context)
            setText(current.password)
        }
        val guestCheck = CheckBox(context).apply {
            text = "访客 / 匿名"
            typeface = AppFonts.regular(context)
            isChecked = current.guest
        }
        val smb1Check = CheckBox(context).apply {
            text = "启用 SMB1 兼容（默认关闭）"
            typeface = AppFonts.regular(context)
            isChecked = current.smb1Enabled
        }
        val saveAsNewCheck = CheckBox(context).apply {
            text = "另存为新连接"
            typeface = AppFonts.regular(context)
            isChecked = saveAsNewDefault
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 20, 36, 20)
            addView(nameInput)
            addView(hostInput)
            addView(shareInput)
            addView(pathInput)
            addView(userInput)
            addView(passInput)
            addView(guestCheck)
            addView(smb1Check)
            addView(saveAsNewCheck)
        }

        val scrollView = ScrollView(context).apply {
            addView(container)
        }

        AlertDialog.Builder(context)
            .setTitle("SMB 连接配置")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存并连接") { _, _ ->
                val config = SmbConfig(
                    host = hostInput.text.toString().trim(),
                    share = shareInput.text.toString().trim(),
                    path = pathInput.text.toString().trim(),
                    username = userInput.text.toString().trim(),
                    password = passInput.text.toString(),
                    guest = guestCheck.isChecked,
                    smb1Enabled = smb1Check.isChecked
                )
                viewModel.saveConfig(
                    config = config,
                    name = nameInput.text.toString().trim(),
                    saveAsNew = saveAsNewCheck.isChecked
                )
            }
            .show()
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank()) return "未配置"
        return if (config.share.isBlank()) {
            "smb://${config.host}（全部共享）"
        } else {
            val path = config.normalizedPath()
            if (path.isBlank()) "smb://${config.host}/${config.share}"
            else "smb://${config.host}/${config.share}/$path"
        }
    }
}
