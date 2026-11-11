package com.mgb.keyboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.PopupWindow
import android.widget.TextView
import com.mgb.keyboard.typing.TipPlace

/**
 * v0.8.8 toolbar name tip: hold a toolbar icon 0.3 s -> a small dim bubble with the item name floats above it.
 * Setting tb_tip (default ON). The bubble is not touchable and follows the theme (key colour + text colour, slightly see-through).
 * Release / slide off / the 1.5 s "Edit shortcut" bubble = tip disappears. Every call has try/catch; any problem = no tip (old behaviour).
 * ToolbarEdit.attach() calls show() / dismiss() for the normal toolbar keys; attach() here is only for keys that have no other touch code (fold key).
 */
object ToolTip {

    private var pw: PopupWindow? = null

    fun dismiss() { try { pw?.dismiss() } catch (_: Throwable) { }; pw = null }

    fun enabled(prefs: JabraPrefs): Boolean = try { prefs.bool("tb_tip", true) } catch (_: Throwable) { false }

    fun show(b: View, prefs: JabraPrefs, raw: String?) {
        try {
            dismiss()
            val text = TipPlace.text(raw)
            if (text.isEmpty()) return
            val d = b.resources.displayMetrics.density
            val txt = try { JabraTokens.btnText(prefs) } catch (_: Throwable) { null } ?: Color.parseColor("#222222")
            val fill = try { JabraTokens.btnFill(prefs) } catch (_: Throwable) { null } ?: Color.WHITE
            val t = TextView(b.context)
            t.text = text
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding((12 * d).toInt(), (7 * d).toInt(), (12 * d).toInt(), (7 * d).toInt())
            t.setTextColor(txt)
            val bg = GradientDrawable()
            bg.setColor(JabraTokens.withAlpha(fill, TipPlace.ALPHA_FILL))
            bg.setStroke((1 * d).toInt().coerceAtLeast(1), JabraTokens.withAlpha(txt, 0x55))
            bg.cornerRadius = 12 * d
            t.background = bg
            t.alpha = 0f
            t.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val w = t.measuredWidth
            val h = t.measuredHeight
            val p = PopupWindow(t, w, h, false)
            p.isTouchable = false
            p.isFocusable = false
            val loc = IntArray(2)
            b.getLocationOnScreen(loc)
            val screenW = b.resources.displayMetrics.widthPixels
            p.showAtLocation(b, Gravity.NO_GRAVITY, TipPlace.x(loc[0], b.width, w, screenW), TipPlace.y(loc[1], h, (6 * d).toInt()))
            pw = p
            try { t.animate().alpha(1f).setDuration(120).start() } catch (_: Throwable) { t.alpha = 1f }   // soft fade-in
        } catch (_: Throwable) { dismiss() }
    }

    /** Tip-only touch handling for a key that has no other touch listener. Returns false from touch = the click stays as before; after a tip the click is skipped (peek, not press). */
    fun attach(b: Button, prefs: JabraPrefs, textOf: () -> String) {
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val r = Runnable { fired = true; show(b, prefs, textOf()) }
        b.setOnTouchListener { v, e ->
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { fired = false; dismiss(); h.removeCallbacks(r); if (enabled(prefs)) h.postDelayed(r, TipPlace.HOLD_MS); false }
                    MotionEvent.ACTION_MOVE -> { if (e.x < 0f || e.y < 0f || e.x > v.width || e.y > v.height) { h.removeCallbacks(r); dismiss() }; false }
                    MotionEvent.ACTION_UP -> { h.removeCallbacks(r); dismiss(); if (fired) { fired = false; v.isPressed = false; true } else false }
                    MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); dismiss(); fired = false; false }
                    else -> false
                }
            } catch (_: Throwable) { false }
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r); dismiss() }
        })
    }
}
