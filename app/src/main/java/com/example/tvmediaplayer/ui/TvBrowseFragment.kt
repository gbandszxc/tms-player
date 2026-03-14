package com.example.tvmediaplayer.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.lifecycle.lifecycleScope
import com.example.tvmediaplayer.domain.model.SmbConfig
import com.example.tvmediaplayer.domain.model.SmbEntry
import com.example.tvmediaplayer.ui.presenter.SimpleTextPresenter
import kotlinx.coroutines.launch

class TvBrowseFragment : BrowseSupportFragment() {

    private val viewModel by viewModels<TvBrowserViewModel> {
        TvBrowserViewModel.factory(requireContext().applicationContext)
    }
    private val rowsAdapter by lazy { ArrayObjectAdapter(ListRowPresenter()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "电视音乐播放器"
        headersState = HEADERS_DISABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = 0xFF22C55E.toInt()

        adapter = rowsAdapter
        setOnItemViewClickedListener { _, item, _, _ ->
            when (item) {
                is UiItem.ActionItem -> onActionClicked(item)
                is UiItem.FileItem -> viewModel.enterDirectory(item.entry)
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

    private fun render(state: TvBrowserState) {
        rowsAdapter.clear()

        val configRow = ArrayObjectAdapter(SimpleTextPresenter()).apply {
            add(UiItem.ActionItem(Action.EDIT_CONFIG, "连接：${configText(state.config)}"))
            add(UiItem.ActionItem(Action.REFRESH, "刷新当前目录"))
        }
        rowsAdapter.add(ListRow(HeaderItem(0, "连接与操作"), configRow))

        val pathLabel = if (state.currentPath.isBlank()) "/" else "/${state.currentPath}"
        val browserRow = ArrayObjectAdapter(SimpleTextPresenter())
        if (state.loading) {
            browserRow.add("加载中...")
        } else {
            if (state.currentPath.isNotBlank()) {
                browserRow.add(UiItem.FileItem(SmbEntry("..", state.currentPath, true), "[目录] ..（上一级）"))
            }
            if (state.entries.isEmpty()) {
                browserRow.add("当前目录为空")
            } else {
                state.entries.filterNot { it.name == ".." }.forEach { entry ->
                    val icon = if (entry.isDirectory) "[目录]" else "[音频]"
                    browserRow.add(UiItem.FileItem(entry, "$icon ${entry.name}"))
                }
            }
        }
        rowsAdapter.add(ListRow(HeaderItem(1, "浏览：$pathLabel"), browserRow))

        state.error?.let {
            val errorRow = ArrayObjectAdapter(SimpleTextPresenter()).apply {
                add("错误：$it")
            }
            rowsAdapter.add(ListRow(HeaderItem(2, "连接状态"), errorRow))
        }
    }

    private fun onActionClicked(item: UiItem.ActionItem) {
        when (item.action) {
            Action.EDIT_CONFIG -> showConfigDialog()
            Action.REFRESH -> viewModel.loadCurrentPath()
        }
    }

    private fun showConfigDialog() {
        val current = viewModel.state.value.config
        val context = requireContext()

        val hostInput = EditText(context).apply {
            hint = "服务器 IP，例如 192.168.31.233"
            typeface = AppFonts.regular(context)
            setText(current.host)
        }
        val shareInput = EditText(context).apply {
            hint = "共享名，例如 Banana"
            typeface = AppFonts.regular(context)
            setText(current.share)
        }
        val pathInput = EditText(context).apply {
            hint = "子目录，例如 h/DLsite"
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

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 20, 36, 20)
            addView(hostInput)
            addView(shareInput)
            addView(pathInput)
            addView(userInput)
            addView(passInput)
            addView(guestCheck)
            addView(smb1Check)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("SMB 连接")
            .setView(container)
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
                viewModel.saveConfig(config)
            }
            .show()
    }

    private fun configText(config: SmbConfig): String {
        if (config.host.isBlank() || config.share.isBlank()) return "未配置"
        val path = config.normalizedPath()
        return if (path.isBlank()) {
            "smb://${config.host}/${config.share}"
        } else {
            "smb://${config.host}/${config.share}/$path"
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
        REFRESH
    }
}
