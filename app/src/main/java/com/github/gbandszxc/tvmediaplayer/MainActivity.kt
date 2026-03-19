package com.github.gbandszxc.tvmediaplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragment
import com.github.gbandszxc.tvmediaplayer.ui.UiSettingsApplier

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UiSettingsApplier.applyAll(this)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.root_container, TvBrowseFragment())
                .commitNow()
        }
    }

    override fun onResume() {
        super.onResume()
        UiSettingsApplier.applyAll(this)
    }
}
