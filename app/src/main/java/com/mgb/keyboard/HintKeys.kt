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
import com.mgb.keyboard.typing.HintStyle
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
    /** v0.9.4 (R4): true = a Bangla layout is active and the field accepts Bangla (not password / email / URL / number). Default false = old behaviour. */
    fun banglaNumbers(): Boolean = false
}

object HintKeys {

    private fun letterOf(b: View): Char? = b.tag as? Char

    fun attach(b: Button, prefs: JabraPrefs, host: HintHost) {
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val r = Runnable {
            try {
                val c = letterOf(b) ?: return@Runnable
                val isNum = HintStyle.isDigit(c)   // v0.8.6: number-row key (Bangla digit hint); language does not matter
                if ((!isNum && !host.hintTypingActive()) || !prefs.bool("hint_on", true)) return@Runnable
                val hint = (if (isNum) HintStyle.digitHintFor(c, prefs.bool("hint_digits", false), prefs.bool("numrow_bn", false), host.banglaNumbers()) else LongPressHints.hintFor(c, prefs.bool("numrow", true), prefs.str("hint_custom", ""))) ?: return@Runnable   // v0.5.2 (custom override first); v0.9.4: digitHintFor (numrow_bn)
                fired = true
                host.typeHint(hint)
                if (KeyCfg.hintBuzz(b)) host.hintFeedback(b)   // v0.9.1: no 2nd buzz when fb_down already buzzed at finger-down
            } catch (_: Throwable) { }
        }
        b.setOnTouchListener { v, e ->
            try { if (e.actionMasked == MotionEvent.ACTION_DOWN && KeyCfg.downBuzz(v, prefs)) host.hintFeedback(v) } catch (_: Throwable) { }   // v0.9.1: fb_down = feedback at finger-down (OFF = nothing here)
            try { KeyPopup.touch(v, e, prefs) } catch (_: Throwable) { }   // popup behaviour is the same as v0.1.3
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        h.removeCallbacks(r)
                        if (KeyCfg.hintOn(prefs)) {   // v0.6.0: cached flags (fallback = pref reads)
                            val ms = KeyCfg.lpMs(prefs)
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
                    MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); fired = false; KeyCfg.cancelBuzz(v); false }   // v0.9.1
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
    fun refresh(b: Button, prefs: JabraPrefs, host: HintHost, numrow: Boolean? = null, custom: String? = null, hide: Boolean? = null) {   // v0.6.1: optional pre-read prefs (null = read here, as before)
        try {
            val c = letterOf(b)
            val on = KeyCfg.hintOn(prefs)   // v0.6.1: cached
            val isNum = c != null && HintStyle.isDigit(c)   // v0.8.6
            val show = c != null && LongPressHints.labelVisible(on && (isNum || host.hintLabelActive()), hide ?: prefs.bool("hint_hide", false))
            val t = if (!show) null else if (isNum) HintStyle.digitHintFor(c!!, prefs.bool("hint_digits", false), prefs.bool("numrow_bn", false), host.banglaNumbers()) else LongPressHints.hintFor(c!!, numrow ?: prefs.bool("numrow", true), custom ?: prefs.str("hint_custom", ""))   // v0.5.2; v0.9.4: digitHintFor (numrow_bn)
            if (t == null) { if (b.foreground is HintDrawable) b.foreground = null; return }
            val col = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
            val cur = b.foreground
            val al = try { HintStyle.alpha(prefs.int("hint_alpha_lv", HintStyle.LV_DEFAULT)) } catch (_: Throwable) { 170 }   // v0.8.6
            val sz = try { prefs.int("hint_size_lv", HintStyle.LV_DEFAULT) } catch (_: Throwable) { HintStyle.LV_DEFAULT }
            if (cur is HintDrawable) cur.set(t, col, al, sz) else b.foreground = HintDrawable(t, col, al, sz)
        } catch (_: Throwable) { }
    }

    /** Small grey character in the top-right corner; scales with the key size. */
    class HintDrawable(private var text: String, color: Int, private var a: Int = 170, private var sizeLv: Int = HintStyle.LV_DEFAULT) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { p.textAlign = Paint.Align.RIGHT; p.color = color; p.alpha = a }
        fun set(t: String, color: Int, alpha: Int = 170, lv: Int = HintStyle.LV_DEFAULT) { text = t; p.color = color; a = alpha; sizeLv = lv; p.alpha = alpha; invalidateSelf() }
        override fun draw(canvas: Canvas) {
            val r = bounds
            p.textSize = HintStyle.textSize(r.height().toFloat(), sizeLv)   // level 1 = the old 0.27 x height rule
            canvas.drawText(text, r.right - r.width() * 0.10f, r.top + r.height() * 0.08f + p.textSize * 0.85f, p)
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
