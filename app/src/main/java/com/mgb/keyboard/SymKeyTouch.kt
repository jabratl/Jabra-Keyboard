package com.mgb.keyboard

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LpBarPlan
import com.mgb.keyboard.layout.SwipeDir

/**
 * v0.17.21 (L5b.2): long-press (single text or slide-to-pick options bar) + swipe texts on the character keys of the SYMBOLS panel (custom keyboard, layers s1 - s3).
 * Same behaviour as CustomRows.attachTouch (v0.17.20) but small and separate, so the letters code is not touched. A text starting with "@" is an action (run), else plain text (run).
 * Keys without long-press / swipe texts get NO listener at all = exactly as before. Any problem = the plain tap keeps working.
 */
object SymKeyTouch {
    fun attach(b: Button, k: KeyDef, prefs: JabraPrefs, run: (String) -> Unit, buzz: (View) -> Unit) {
        try {
            val lp = if (k.lp.isNotEmpty()) k.lp else (k.lpList.firstOrNull() ?: "")
            val hasSw = SwipeDir.hasAny(k)
            if (lp.isEmpty() && !hasSw) return
            val opts = LpBarPlan.options(k)
            val useBar = opts.size >= LpBarPlan.MIN_OPTIONS
            val h = Handler(Looper.getMainLooper())
            val minPx = SwipeDir.minPx(b.resources.displayMetrics.density)
            var fired = false
            var barOn = false
            var x0 = 0f; var y0 = 0f; var swipeTxt = ""
            val r = Runnable {
                try {
                    if (lp.isEmpty()) return@Runnable
                    fired = true
                    if (useBar && LpBar.show(b, opts, prefs)) { barOn = true; buzz(b); return@Runnable }
                    run(lp); buzz(b)
                } catch (_: Throwable) { }
            }
            b.setOnTouchListener { v, e ->
                try {
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            fired = false; swipeTxt = ""; x0 = e.x; y0 = e.y
                            h.removeCallbacks(r)
                            if (lp.isNotEmpty()) {
                                val ms = KeyCfg.lpMs(prefs)
                                h.postDelayed(r, (if (ms > 0) ms else ViewConfiguration.getLongPressTimeout()).toLong())
                            }
                            false
                        }
                        MotionEvent.ACTION_MOVE -> if (barOn) { LpBar.move(e.rawX, e.rawY); true } else {
                            if (e.x < 0f || e.y < 0f || e.x > v.width || e.y > v.height) h.removeCallbacks(r)
                            if (hasSw && !fired) {
                                val t = SwipeDir.textFor(k, SwipeDir.classify(e.x - x0, e.y - y0, minPx))
                                swipeTxt = t
                                if (t.isNotEmpty()) h.removeCallbacks(r)
                            }
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            h.removeCallbacks(r)
                            if (barOn) {
                                barOn = false; fired = false
                                val s = LpBar.picked()
                                LpBar.dismiss(); v.isPressed = false
                                try { if (s != null) { run(s); buzz(b) } } catch (_: Throwable) { }
                                true
                            } else if (fired) { fired = false; v.isPressed = false; true }
                            else if (swipeTxt.isNotEmpty()) {
                                val t = swipeTxt; swipeTxt = ""
                                v.isPressed = false
                                try { run(t); buzz(b) } catch (_: Throwable) { }
                                true
                            } else false
                        }
                        MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); if (barOn) { barOn = false; LpBar.dismiss() }; fired = false; swipeTxt = ""; false }
                        else -> false
                    }
                } catch (_: Throwable) { false }
            }
            b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { }
                override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r); if (barOn) { barOn = false; LpBar.dismiss() } }
            })
        } catch (_: Throwable) { }
    }
}
