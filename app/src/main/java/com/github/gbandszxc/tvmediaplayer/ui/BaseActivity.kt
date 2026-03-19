package com.github.gbandszxc.tvmediaplayer.ui

import androidx.fragment.app.FragmentActivity

/**
 * 所有 Activity 的公共基类，统一管理沉浸式全屏逻辑。
 * 继承此类的 Activity 无需手动处理 onWindowFocusChanged。
 */
abstract class BaseActivity : FragmentActivity() {

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiSettingsApplier.applyImmersiveFullscreen(this)
    }
}
