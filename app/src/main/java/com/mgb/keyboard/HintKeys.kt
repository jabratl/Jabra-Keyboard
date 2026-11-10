package com.mgb.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import com.mgb.keyboard.typing.LongPressHints
import com.mgb.keyboard.typing.TypingLevels

/**
 * v0.1.6 long-press hint letters. A small hint character is drawn in the top-right corner of each English LETTER key
 * (as the key's foreground drawable, not a second TextView); holding the key types that hint instead of the letter.
 * Settings: hint_on (default true) = long-press typing; hint_hide (default false) = hides ONLY the small label.
 * ONE combined touch listener per letter key: the key popup (v0.1.3, KeyPopup.touch) + the long-press timer.
 * Every call is wrapped in try/catch; on any problem the key behaves exactly like v0.1.5.
 */
interface HintHost {
    /** true = English typing right now (not Avro / Probhat, no Ctrl / Alt held in PC mode). */
    fun hintTypingActive(): Boolean
    /** true = English typing (the label may be shown even when Ctrl / Alt is held). */
    fun hintLabelActive(): Boolean
    fun typeHint(s: String)
    fun hintFeedback(v: View)
}

object HintKeys {

    private fun letterOf(b: View): Char? = b.tag as? Char

    fun attach(b: Button, prefs: JabraPrefs, host: HintHost) {
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val r = Runnable {
            try {
                if (!host.hintTypingActive() || !prefs.bool("hint_on", true)) return@Runnable
                val c = letterOf(b) ?: return@Runnable
                val hint = LongPressHints.hintFor(c, prefs.bool("numrow", true)) ?: return@Runnable
                fired = true
                host.typeHint(hint)
                host.hintFeedback(b)
            } catch (_: Throwable) { }
        }
        b.setOnTouchListener { v, e ->
            try { KeyPopup.touch(v, e, prefs) } catch (_: Throwable) { }   // popup behaviour is the same as v0.1.3
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        h.removeCallbacks(r)
                        if (prefs.bool("hint_on", true)) {
                            val ms = TypingLevels.longPressMs(prefs.int("lp_delay", TypingLevels.LP_DEFAULT))
                            h.postDelayed(r, (if (ms > 0) ms else ViewConfiguration.getLongPressTimeout()).toLong())
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (e.x < 0f || e.y < 0f || e.x > v.width || e.y > v.height) h.removeCallbacks(r)
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(r)
                        if (fired) { fired = false; v.isPressed = false; KeyPopup.dismiss(); true }   // hint typed: no letter click
                        else false
                    }
                    MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); fired = false; false }
                    else -> false
                }
            } catch (_: Throwable) { false }
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r) }
        })
    }

    /** Shows or hides the small label on one letter key. */
    fun refresh(b: Button, prefs: JabraPrefs, host: HintHost) {
        try {
            val c = letterOf(b)
            val on = prefs.bool("hint_on", true)
            val show = c != null && LongPressHints.labelVisible(on && host.hintLabelActive(), prefs.bool("hint_hide", false))
            val t = if (show) LongPressHints.hintFor(c!!, prefs.bool("numrow", true)) else null
            if (t == null) { if (b.foreground is HintDrawable) b.foreground = null; return }
            val col = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
            val cur = b.foreground
            if (cur is HintDrawable) cur.set(t, col) else b.foreground = HintDrawable(t, col)
        } catch (_: Throwable) { }
    }

    /** Small grey character in the top-right corner; scales with the key size. */
    class HintDrawable(private var text: String, color: Int) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { p.textAlign = Paint.Align.RIGHT; p.color = color; p.alpha = 170 }
        fun set(t: String, color: Int) { text = t; p.color = color; p.alpha = 170; invalidateSelf() }
        override fun draw(canvas: Canvas) {
            val r = bounds
            p.textSize = (r.height() * 0.27f).coerceIn(9f, 40f)
            canvas.drawText(text, r.right - r.width() * 0.10f, r.top + r.height() * 0.08f + p.textSize * 0.85f, p)
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
