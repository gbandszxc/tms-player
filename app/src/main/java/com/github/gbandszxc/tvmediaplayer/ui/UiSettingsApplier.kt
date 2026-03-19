package com.github.gbandszxc.tvmediaplayer.ui

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup
import android.view.WindowManager
import kotlin.math.roundToInt

object UiSettingsApplier {

    fun applyAll(activity: Activity) {
        applyGlobalScale(activity)
        applyKeepScreenAwake(activity)
    }

    fun applyGlobalScale(activity: Activity) {
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val content = root.getChildAt(0) ?: return
        val scale = UiSettingsStore.globalScalePercent(activity) / 100f
        content.post {
            val parentWidth = root.width
            val parentHeight = root.height
            val params = content.layoutParams
            if (scale != 1f && parentWidth > 0 && parentHeight > 0) {
                params.width = (parentWidth / scale).roundToInt()
                params.height = (parentHeight / scale).roundToInt()
            } else {
                params.width = MATCH_PARENT
                params.height = MATCH_PARENT
            }
            content.layoutParams = params
            content.pivotX = 0f
            content.pivotY = 0f
            content.scaleX = scale
            content.scaleY = scale
        }
    }

    fun applyKeepScreenAwake(activity: Activity) {
        if (UiSettingsStore.keepScreenAwake(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
