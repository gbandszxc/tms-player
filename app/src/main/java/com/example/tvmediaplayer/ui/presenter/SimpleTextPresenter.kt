package com.example.tvmediaplayer.ui.presenter

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.example.tvmediaplayer.ui.AppFonts

class SimpleTextPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(32, 20, 32, 20)
            textSize = 20f
            typeface = AppFonts.medium(parent.context)
            setTextColor(Color.parseColor("#F8FAFC"))
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(Color.TRANSPARENT)
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) Color.parseColor("#2238CA") else Color.TRANSPARENT)
            }
        }
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val tv = viewHolder.view as TextView
        tv.text = item.toString()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
