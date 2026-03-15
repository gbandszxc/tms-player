package com.github.gbandszxc.tvmediaplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.github.gbandszxc.tvmediaplayer.ui.TvBrowseFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.root_container, TvBrowseFragment())
                .commitNow()
        }
    }
}
