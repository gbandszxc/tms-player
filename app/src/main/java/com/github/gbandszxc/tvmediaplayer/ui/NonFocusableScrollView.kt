package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 对遥控器方向键完全不可聚焦的 ScrollView。
 * - requestFocus 直接返回 false，拒绝编程式焦点请求
 * - onFocusChanged 一旦系统强制给焦点，立即 clearFocus() 弹走
 * 触屏滑动不受影响。
 */
class NonFocusableScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?) = false

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) clearFocus()
    }
}
