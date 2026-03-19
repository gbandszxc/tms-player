package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import com.github.gbandszxc.tvmediaplayer.R
import com.github.gbandszxc.tvmediaplayer.playback.LastPlaybackStore
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackArtworkCache
import com.github.gbandszxc.tvmediaplayer.playback.PlaybackLyricsCache

class SettingsActivity : BaseActivity() {

    private data class SettingsItem(
        val title: String,
        val isGroupHeader: Boolean = false,
        val descriptionProvider: () -> String = { "" },
        val valueProvider: (() -> String)? = null,
        val iconResId: Int? = null,
        val action: (() -> Unit)? = null
    )

    private data class SettingsCategory(
        val title: String,
        val itemsProvider: () -> List<SettingsItem>
    )

    private lateinit var containerCategories: LinearLayout
    private lateinit var containerDetail: LinearLayout
    private var selectedCategoryIndex = 0
    private val categoryViews = mutableListOf<View>()

    private val categories by lazy {
        listOf(
            SettingsCategory(
                title = "显示设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "全局缩放",
                            descriptionProvider = { "预设档位：90% / 95% / 100% / 105% / 110%" },
                            valueProvider = { "${UiSettingsStore.globalScalePercent(this)}%" }
                        ) {
                            val next = UiSettingsStore.cycleGlobalScalePreset(this)
                            UiSettingsApplier.applyGlobalScale(this)
                            Toast.makeText(this, "全局缩放已切换到 ${next}%", Toast.LENGTH_SHORT).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "播放设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(title = "歌词", isGroupHeader = true),
                        SettingsItem(
                            title = "播放页歌词字号",
                            descriptionProvider = { "默认值 ${UiSettingsStore.defaultPlaybackLyricsFontSp}sp，可手动输入" },
                            valueProvider = { "${UiSettingsStore.playbackLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = "设置播放页歌词字号",
                                currentValue = UiSettingsStore.playbackLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultPlaybackLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setPlaybackLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(
                            title = "全屏歌词字号",
                            descriptionProvider = { "默认值 ${UiSettingsStore.defaultFullscreenLyricsFontSp}sp，可手动输入" },
                            valueProvider = { "${UiSettingsStore.fullscreenLyricsFontSp(this)}sp" }
                        ) {
                            showLyricsFontDialog(
                                title = "设置全屏歌词字号",
                                currentValue = UiSettingsStore.fullscreenLyricsFontSp(this),
                                defaultValue = UiSettingsStore.defaultFullscreenLyricsFontSp,
                                onSave = { value -> UiSettingsStore.setFullscreenLyricsFontSp(this, value) }
                            )
                        },
                        SettingsItem(title = "其它", isGroupHeader = true),
                        SettingsItem(
                            title = "记忆上次播放",
                            descriptionProvider = { "记录上次播放的曲目与进度，下次打开可一键继续" },
                            valueProvider = {
                                if (UiSettingsStore.rememberLastPlayback(this)) "已开启" else "已关闭"
                            }
                        ) {
                            val next = !UiSettingsStore.rememberLastPlayback(this)
                            UiSettingsStore.setRememberLastPlayback(this, next)
                            if (!next) LastPlaybackStore.clear(this)
                            Toast.makeText(
                                this,
                                if (next) "已开启记忆上次播放" else "已关闭记忆上次播放（已清除记录）",
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "应用设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "应用休眠保护",
                            descriptionProvider = { "开启后应用运行时保持常亮，不触发休眠/息屏" },
                            valueProvider = {
                                if (UiSettingsStore.keepScreenAwake(this)) "已开启" else "已关闭"
                            }
                        ) {
                            val next = !UiSettingsStore.keepScreenAwake(this)
                            UiSettingsStore.setKeepScreenAwake(this, next)
                            UiSettingsApplier.applyKeepScreenAwake(this)
                            Toast.makeText(
                                this,
                                if (next) "已开启休眠保护" else "已关闭休眠保护",
                                Toast.LENGTH_SHORT
                            ).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "其它设置",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "清理缓存",
                            descriptionProvider = { "歌词与封面磁盘缓存，重启恢复播放时可跳过 SMB 加载" },
                            valueProvider = {
                                val total = PlaybackLyricsCache.diskCacheSize(this) +
                                        PlaybackArtworkCache.diskCacheSize(this)
                                val kb = total / 1024
                                if (kb < 1024) "${kb} KB" else "${"%.1f".format(kb / 1024.0)} MB"
                            }
                        ) {
                            PlaybackLyricsCache.clearDisk(this)
                            PlaybackArtworkCache.clearDisk(this)
                            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show()
                            rebuildCurrentCategory(moveFocusToDetail = false)
                        }
                    )
                }
            ),
            SettingsCategory(
                title = "关于",
                itemsProvider = {
                    listOf(
                        SettingsItem(
                            title = "项目描述",
                            descriptionProvider = { "一款适配安卓TV，基于遥控器操作的本地SMB网络音乐播放器。" }
                        ),
                        SettingsItem(
                            title = "GitHub",
                            descriptionProvider = { "项目主页" },
                            iconResId = R.drawable.ic_github
                        ) {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/gbandszxc/tms-player")
                                )
                            )
                        }
                    )
                }
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        UiSettingsApplier.applyAll(this)

        containerCategories = findViewById(R.id.container_categories)
        containerDetail = findViewById(R.id.container_detail)

        buildCategoryList()
        selectCategory(0, moveFocusToDetail = false)

        containerCategories.post {
            categoryViews.firstOrNull()?.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
        rebuildCurrentCategory(moveFocusToDetail = false)
    }

    private fun buildCategoryList() {
        categories.forEachIndexed { index, category ->
            val itemView = layoutInflater.inflate(
                R.layout.item_settings_category, containerCategories, false
            )
            itemView.findViewById<TextView>(R.id.tv_category_title).text = category.title
            itemView.setOnClickListener { selectCategory(index, moveFocusToDetail = true) }
            containerCategories.addView(itemView)
            categoryViews.add(itemView)
        }
    }

    private fun selectCategory(index: Int, moveFocusToDetail: Boolean) {
        selectedCategoryIndex = index
        categoryViews.forEachIndexed { i, view ->
            view.isSelected = (i == index)
        }
        buildDetailPanel(categories[index], moveFocusToDetail)
    }

    private fun rebuildCurrentCategory(moveFocusToDetail: Boolean) {
        if (categories.isEmpty()) return
        buildDetailPanel(categories[selectedCategoryIndex], moveFocusToDetail)
    }

    private fun buildDetailPanel(category: SettingsCategory, moveFocusToDetail: Boolean) {
        val items = category.itemsProvider.invoke()
        containerDetail.removeAllViews()

        val headerView = TextView(this).apply {
            text = category.title
            setTextColor(0xFF60A5FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = AppFonts.bold(this@SettingsActivity)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(20)
            layoutParams = lp
        }
        containerDetail.addView(headerView)

        var firstEntryView: View? = null
        items.forEachIndexed { i, item ->
            if (item.isGroupHeader) {
                val groupHeaderView = TextView(this).apply {
                    text = item.title
                    setTextColor(0xFF94A3B8.toInt())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = AppFonts.medium(this@SettingsActivity)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = dp(if (i == 0) 0 else 16)
                    lp.bottomMargin = dp(4)
                    layoutParams = lp
                }
                containerDetail.addView(groupHeaderView)
                return@forEachIndexed
            }

            val itemView = layoutInflater.inflate(
                R.layout.item_settings_entry, containerDetail, false
            )
            itemView.findViewById<TextView>(R.id.tv_settings_title).text = item.title
            val tvDesc = itemView.findViewById<TextView>(R.id.tv_settings_desc)
            val desc = item.descriptionProvider.invoke()
            if (desc.isNotBlank()) {
                tvDesc.text = desc
                tvDesc.visibility = View.VISIBLE
            } else {
                tvDesc.visibility = View.GONE
            }

            val tvValue = itemView.findViewById<TextView>(R.id.tv_settings_value)
            val valueText = item.valueProvider?.invoke().orEmpty()
            if (valueText.isNotBlank()) {
                tvValue.text = valueText
                tvValue.visibility = View.VISIBLE
            } else {
                tvValue.visibility = View.GONE
            }

            val ivIcon = itemView.findViewById<ImageView>(R.id.iv_settings_action_icon)
            if (item.iconResId != null) {
                ivIcon.setImageResource(item.iconResId)
                ivIcon.visibility = View.VISIBLE
            } else {
                ivIcon.visibility = View.GONE
            }

            itemView.setOnClickListener {
                if (item.action != null) {
                    item.action.invoke()
                } else {
                    Toast.makeText(this, "该项仅用于展示", Toast.LENGTH_SHORT).show()
                }
            }
            containerDetail.addView(itemView)
            if (firstEntryView == null) firstEntryView = itemView
        }

        if (moveFocusToDetail) {
            val target = firstEntryView
            containerDetail.post { target?.requestFocus() }
        }
    }

    private fun showLyricsFontDialog(
        title: String,
        currentValue: Int,
        defaultValue: Int,
        onSave: (Int) -> Unit
    ) {
        val input = EditText(this).apply {
            hint = "输入字号（${UiSettingsStore.minLyricsFontSp}-${UiSettingsStore.maxLyricsFontSp}）"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentValue.toString())
            setSelectAllOnFocus(true)
            typeface = AppFonts.regular(this@SettingsActivity)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNeutralButton("恢复默认($defaultValue)") { _, _ ->
                onSave(defaultValue)
                Toast.makeText(this, "已恢复默认字号 ${defaultValue}sp", Toast.LENGTH_SHORT).show()
                rebuildCurrentCategory(moveFocusToDetail = false)
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (value !in UiSettingsStore.minLyricsFontSp..UiSettingsStore.maxLyricsFontSp) {
                    Toast.makeText(
                        this,
                        "字号范围需在 ${UiSettingsStore.minLyricsFontSp}-${UiSettingsStore.maxLyricsFontSp}sp",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                onSave(value)
                Toast.makeText(this, "字号已设置为 ${value}sp", Toast.LENGTH_SHORT).show()
                rebuildCurrentCategory(moveFocusToDetail = false)
            }
            .show()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()
}
