package com.example.tvmediaplayer.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.playback.PlaybackQueueBuilder
import com.example.tvmediaplayer.playback.PlaybackService
import com.example.tvmediaplayer.playback.SmbMediaItemFactory
import com.example.tvmediaplayer.ui.presenter.SimpleTextPresenter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvBrowseFragment : VerticalGridSupportFragment() {

    private val viewModel by viewModels<TvBrowserViewModel> {
        TvBrowserViewModel.factory(requireContext().applicationContext)
    }
    private val listAdapter by lazy { ArrayObjectAdapter(SimpleTextPresenter()) }
    private val mediaItemFactory by lazy { SmbMediaItemFactory() }
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "TV Music Player"

        gridPresenter = VerticalGridPresenter().apply {
            numberOfColumns = 1
        }
        adapter = listAdapter

        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is UiItem.ActionItem -> onActionClicked(item)
                is UiItem.FileItem -> onFileClicked(item.entry)
            }
        }

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (viewModel.state.value.currentPath.isNotBlank()) {
                        viewModel.enterDirectory(SmbEntry("..", viewModel.state.value.currentPath, true))
                        true
                    } else {
                        false
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    showConfigDialog()
                    true
                }
                else -> false
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureController()
    }

    override fun onStop() {
        releaseController()
        super.onStop()
    }

    private fun render(state: TvBrowserState) {
        listAdapter.clear()

        listAdapter.add("== Connection & Actions ==")
        listAdapter.add(UiItem.ActionItem(Action.EDIT_CONFIG, "Connection: ${configText(state.config)}"))
        listAdapter.add(UiItem.ActionItem(Action.SWITCH_CONFIG, "Switch saved connections (${state.savedConnections.size})"))
        listAdapter.add(UiItem.ActionItem(Action.REFRESH, "Refresh current directory"))
        if (state.error != null) {
            listAdapter.add(UiItem.ActionItem(Action.RETRY, "Retry connection"))
        }
        listAdapter.add(UiItem.ActionItem(Action.PLAY_ALL, "Play current directory (sequential)"))
        listAdapter.add(UiItem.ActionItem(Action.PLAY_SHUFFLE, "Play current directory (shuffle)"))

        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        listAdapter.add("== Files: $pathLabel ==")

        if (state.loading) {
            listAdapter.add("Loading...")
        } else {
            if (state.currentPath.isNotBlank()) {
                listAdapter.add(UiItem.FileItem(SmbEntry("..", state.currentPath, true), "[DIR] .. (up)"))
            }
            if (state.entries.isEmpty()) {
                listAdapter.add("Current directory is empty")
            } else {
                state.entries.forEach { entry ->
                    val icon = if (entry.isDirectory) "[DIR]" else "[AUDIO]"
                    listAdapter.add(UiItem.FileItem(entry, "$icon ${entry.name}"))
                }
            }
        }

        state.error?.let {
            listAdapter.add("== Error ==")
            listAdapter.add(it)
        }
    }

    private fun onActionClicked(item: UiItem.ActionItem) {
        when (item.action) {
            Action.EDIT_CONFIG -> showConfigDialog()
            Action.SWITCH_CONFIG -> showSwitchDialog()
            Action.REFRESH -> viewModel.loadCurrentPath()
            Action.RETRY -> viewModel.loadCurrentPath()
            Action.PLAY_ALL -> playDirectory(shuffle = false)
            Action.PLAY_SHUFFLE -> playDirectory(shuffle = true)
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

    private fun playDirectory(shuffle: Boolean) {
        val queue = PlaybackQueueBuilder.fromDirectory(viewModel.state.value.entries)
        playQueue(queue, startIndex = 0, shuffle = shuffle)
    }

    private fun playQueue(queue: List<SmbEntry>, startIndex: Int, shuffle: Boolean) {
        if (queue.isEmpty()) {
            Toast.makeText(requireContext(), "No playable audio in current directory", Toast.LENGTH_SHORT).show()
            return
        }
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(requireContext(), "Player is initializing, retry shortly", Toast.LENGTH_SHORT).show()
            ensureController()
            return
        }

        val config = viewModel.state.value.config
        lifecycleScope.launch {
            val mediaItems = withContext(Dispatchers.IO) {
                mediaItemFactory.create(config, queue)
            }
            controller.setShuffleModeEnabled(shuffle)
            controller.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            controller.prepare()
            controller.play()
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
                    .onSuccess { controller -> mediaController = controller }
                    .onFailure {
                        controllerFuture = null
                        Toast.makeText(requireContext(), "Failed to connect player", Toast.LENGTH_SHORT).show()
                    }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun releaseController() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private fun showSwitchDialog() {
        val saved = viewModel.state.value.savedConnections
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), "No saved connection yet", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = saved.map { "${it.name} (${it.config.host})" }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Switch SMB connection")
            .setItems(labels) { _, which ->
                viewModel.switchConnection(saved[which].id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfigDialog() {
        val current = viewModel.state.value.config
        val context = requireContext()

        val nameInput = EditText(context).apply {
            hint = "Connection name, e.g. Home NAS"
            typeface = AppFonts.regular(context)
            val active = viewModel.state.value.savedConnections
                .firstOrNull { it.id == viewModel.state.value.activeConnectionId }
            setText(active?.name.orEmpty())
        }
        val hostInput = EditText(context).apply {
            hint = "Server host, e.g. 192.168.0.10"
            typeface = AppFonts.regular(context)
            setText(current.host)
        }
        val shareInput = EditText(context).apply {
            hint = "Share name (optional; empty shows all shares)"
            typeface = AppFonts.regular(context)
            setText(current.share)
        }
        val pathInput = EditText(context).apply {
            hint = "Sub path (optional)"
            typeface = AppFonts.regular(context)
            setText(current.path)
        }
        val userInput = EditText(context).apply {
            hint = "Username (optional for guest)"
            typeface = AppFonts.regular(context)
            setText(current.username)
        }
        val passInput = EditText(context).apply {
            hint = "Password (optional for guest)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            typeface = AppFonts.regular(context)
            setText(current.password)
        }
        val guestCheck = CheckBox(context).apply {
            text = "Guest / Anonymous"
            typeface = AppFonts.regular(context)
            isChecked = current.guest
        }
        val smb1Check = CheckBox(context).apply {
            text = "Enable SMB1 compatibility (off by default)"
            typeface = AppFonts.regular(context)
            isChecked = current.smb1Enabled
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
        }

        AlertDialog.Builder(context)
            .setTitle("SMB connection")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save and connect") { _, _ ->
                val config = SmbConfig(
                    host = hostInput.text.toString().trim(),
                    share = shareInput.text.toString().trim(),
                    path = pathInput.text.toString().trim(),
                    username = userInput.text.toString().trim(),
                    password = passInput.text.toString(),
                    guest = guestCheck.isChecked,
                    smb1Enabled = smb1Check.isChecked
                )
                viewModel.saveConfig(config, nameInput.text.toString().trim())
            }
            .show()
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank()) return "Not configured"
        return if (config.share.isBlank()) {
            "smb://${config.host} (all shares)"
        } else {
            val path = config.normalizedPath()
            if (path.isBlank()) "smb://${config.host}/${config.share}"
            else "smb://${config.host}/${config.share}/$path"
        }
    }

    private sealed interface UiItem {
        data class ActionItem(val action: Action, private val text: String) : UiItem {
            override fun toString(): String = text
        }

        data class FileItem(val entry: SmbEntry, private val text: String) : UiItem {
            override fun toString(): String = text
        }
    }

    private enum class Action {
        EDIT_CONFIG,
        SWITCH_CONFIG,
        REFRESH,
        RETRY,
        PLAY_ALL,
        PLAY_SHUFFLE
    }
}
