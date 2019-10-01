package com.cameron.pomodoro

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker

/**
 * Used as a small work around to increase the text size
 * of the default Number Picker without distorting it.
 * */
class CustomNumberPicker(context: Context, attrs: AttributeSet) : NumberPicker(context, attrs) {
    override fun addView(child: View?) {
        super.addView(child)
        updateView(child)
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        super.addView(child, params)
        updateView(child)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        updateView(child)
    }

    private fun updateView(v: View?) {
        if (v is EditText) {
            v.textSize = 20f
            v.setTextColor(Color.parseColor("#333333"))
        }
    }
}