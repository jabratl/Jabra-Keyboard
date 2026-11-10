package com.mgb.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView

/**
 * v0.1.3 "Popup on keypress": while a letter key is held, a small bubble with its label shows above the key.
 * Setting key_popup (default OFF). Read at touch time, so no rebuild needed. The touch listener always returns false,
 * so the normal click / haptic / typing code of the key is not changed. Every call is wrapped in try/catch.
 */
object KeyPopup {

    private var pw: PopupWindow? = null

    fun dismiss() { try { pw?.dismiss() } catch (_: Throwable) { }; pw = null }

    fun attach(b: Button, prefs: JabraPrefs) {
        b.setOnTouchListener { v, e ->
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> if (prefs.bool("key_popup", false)) show(v as Button)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dismiss()
                }
            } catch (_: Throwable) { dismiss() }
            false
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { dismiss() }
        })
    }

    private fun show(b: Button) {
        dismiss()
        val label = b.text?.toString() ?: ""
        if (label.isEmpty()) return
        val d = b.resources.displayMetrics.density
        val w = (b.width + 12 * d).toInt().coerceAtLeast((44 * d).toInt())
        val h = (b.height * 1.25f).toInt().coerceAtLeast((52 * d).toInt())
        val t = TextView(b.context)
        t.text = label
        t.textSize = 26f
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.parseColor("#222222"))
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#FFFFFF"))
        bg.setStroke((1 * d).toInt().coerceAtLeast(1), Color.parseColor("#888888"))
        bg.cornerRadius = 10 * d
        t.background = bg
        val p = PopupWindow(t, w, h, false)
        p.isTouchable = false
        p.isFocusable = false
        val loc = IntArray(2)
        b.getLocationOnScreen(loc)
        val x = loc[0] + b.width / 2 - w / 2
        val y = (loc[1] - h - (4 * d).toInt()).coerceAtLeast(0)
        p.showAtLocation(b, Gravity.NO_GRAVITY, x, y)
        pw = p
    }
}
