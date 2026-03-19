package com.github.gbandszxc.tvmediaplayer.ui

import android.content.Context

object UiSettingsStore {
    private const val PREF_NAME = "ui_settings"
    private const val KEY_GLOBAL_SCALE_PERCENT = "global_scale_percent"
    private const val KEY_PLAYBACK_LYRICS_FONT_SP = "playback_lyrics_font_sp"
    private const val KEY_FULLSCREEN_LYRICS_FONT_SP = "fullscreen_lyrics_font_sp"
    private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val KEY_REMEMBER_LAST_PLAYBACK = "remember_last_playback"

    val globalScalePresets: IntArray = intArrayOf(90, 95, 100, 105, 110)
    const val defaultGlobalScalePercent: Int = 100
    const val defaultPlaybackLyricsFontSp: Int = 20
    const val defaultFullscreenLyricsFontSp: Int = 28
    const val minLyricsFontSp: Int = 14
    const val maxLyricsFontSp: Int = 56

    fun globalScalePercent(context: Context): Int {
        val value = prefs(context).getInt(KEY_GLOBAL_SCALE_PERCENT, defaultGlobalScalePercent)
        return if (globalScalePresets.contains(value)) value else defaultGlobalScalePercent
    }

    fun setGlobalScalePercent(context: Context, value: Int) {
        if (!globalScalePresets.contains(value)) return
        prefs(context).edit().putInt(KEY_GLOBAL_SCALE_PERCENT, value).apply()
    }

    fun cycleGlobalScalePreset(context: Context): Int {
        val current = globalScalePercent(context)
        val index = globalScalePresets.indexOf(current).coerceAtLeast(0)
        val nextIndex = (index + 1) % globalScalePresets.size
        val next = globalScalePresets[nextIndex]
        setGlobalScalePercent(context, next)
        return next
    }

    fun playbackLyricsFontSp(context: Context): Int {
        val value = prefs(context).getInt(KEY_PLAYBACK_LYRICS_FONT_SP, defaultPlaybackLyricsFontSp)
        return value.coerceIn(minLyricsFontSp, maxLyricsFontSp)
    }

    fun setPlaybackLyricsFontSp(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_PLAYBACK_LYRICS_FONT_SP, value.coerceIn(minLyricsFontSp, maxLyricsFontSp))
            .apply()
    }

    fun fullscreenLyricsFontSp(context: Context): Int {
        val value = prefs(context).getInt(KEY_FULLSCREEN_LYRICS_FONT_SP, defaultFullscreenLyricsFontSp)
        return value.coerceIn(minLyricsFontSp, maxLyricsFontSp)
    }

    fun setFullscreenLyricsFontSp(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_FULLSCREEN_LYRICS_FONT_SP, value.coerceIn(minLyricsFontSp, maxLyricsFontSp))
            .apply()
    }

    fun keepScreenAwake(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
    }

    fun setKeepScreenAwake(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled).apply()
    }

    fun rememberLastPlayback(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REMEMBER_LAST_PLAYBACK, true)
    }

    fun setRememberLastPlayback(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMEMBER_LAST_PLAYBACK, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
