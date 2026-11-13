package com.mgb.keyboard

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutText
import com.mgb.keyboard.layout.RowDef
import com.mgb.keyboard.layout.SwipeDir
import com.mgb.keyboard.typing.HintStyle

/** v0.17.5 (K5a.1): what CustomRows needs from the keyboard (implemented in MgbIme, which owns the private typing functions). */
interface CustomHost {
    /** A themed key like the classic ones (key()): [pct] = row height % (100 = normal). */
    fun newKey(label: String, weight: Float, pct: Int, onClick: () -> Unit): Button
    /** Letter size % + bold, same as the classic letter keys. */
    fun styleLetter(b: Button)
    /** Empty spot: draws nothing, types nothing. */
    fun gapView(weight: Float, pct: Int): View
    /** Tap of a character key of [layer] (en / av / pb = shift, caps lock, PC Ctrl / Alt, per-language typing; nr / bot = commit + type). */
    fun tapChar(k: KeyDef, layer: String)
    /** Long-press text (several characters allowed) or an @action of key [k]. */
    fun typeExtra(k: KeyDef, s: String)
    /** v0.17.6: the label to SHOW now (Bangla digits on the number row, danda on the "?" key); [base] = the model label. */
    fun dynLabel(k: KeyDef, layer: String, base: String): String
    /** An ACTION key (layout/ActionPlan.ACTION_DONE). null = not supported (e.g. combo): the key stays inert. */
    fun actionKey(k: KeyDef, pct: Int): Button?
    fun feedbackAt(v: View)
}

/** Tag of a custom key (letter keys use Char tags and are not touched by relabelLetters / HintKeys). */
class CustomKeyTag(val key: KeyDef, val layer: String = "en")

/**
 * v0.17.5 (K5a.1) / v0.17.6 (K5a.2) builds the rows of ONE layer (en / av / pb) or of a shared row (nr / bot) from a LayoutDef: character keys (tap, shift output, long-press text, popup, feedback),
 * ACTION keys through the host, empty spots. Reference rows (shared number row / bottom row) are skipped here: the shared layers get their own CustomRows (shared = true).
 * ONE touch listener per key (like HintKeys.attach): feedback at down, KeyPopup.touch, long-press via Handler.
 */
class CustomRows(private val host: CustomHost, private val prefs: JabraPrefs, private val layer: String = "en", private val shared: Boolean = false) {
    val rows = mutableListOf<LinearLayout>()
    private val charKeys = mutableListOf<Button>()

    /** Builds the rows. Empty list = nothing to draw (the caller keeps the classic rows). Any problem inside = empty list. */
    fun build(model: LayoutDef, newRow: () -> LinearLayout): List<LinearLayout> {
        rows.clear(); charKeys.clear()
        try {
            // v0.17.6: letter layers (en / av / pb) = their own rows; shared layers (nr / bot) = the first row of that layer
            val own: List<RowDef> = (if (shared) model.layer(layer)?.rows?.take(1)?.filter { !it.isRef() } else model.layer(layer)?.rows?.filter { !it.isRef() }) ?: return rows
            for (rd in own) {
                val r = newRow()
                for (k in rd.keys) {
                    val v: View? = when (k.kind) {
                        KeyDef.SPACER -> host.gapView(k.weight, rd.heightPct)
                        KeyDef.CHAR -> charKey(k, rd.heightPct)
                        else -> host.actionKey(k, rd.heightPct) ?: inertKey(k, rd.heightPct)
                    }
                    if (v != null) r.addView(v)
                }
                rows.add(r)
            }
        } catch (_: Throwable) { rows.clear(); charKeys.clear() }
        return rows
    }

    /** Label of a character key for the shift state (labels only: setText when it changed). */
    fun relabel(shift: Boolean) {
        for (b in charKeys) {
            val k = (b.tag as? CustomKeyTag)?.key ?: continue
            val t = host.dynLabel(k, layer, labelOf(k, shift && !shared))
            if (!t.contentEquals(b.text)) b.text = t
        }
    }

    private fun labelOf(k: KeyDef, shift: Boolean): String {
        if (!shift) return if (k.label[0].isNotEmpty()) k.label[0] else k.out[0]
        if (k.label[1].isNotEmpty()) return k.label[1]
        if (k.label[0].isNotEmpty()) return if (LayoutText.isLatinLetters(k.label[0])) k.label[0].uppercase() else k.label[0]
        return k.shiftOut()
    }

    private fun inertKey(k: KeyDef, pct: Int): Button {
        val b = host.newKey(if (k.label[0].isNotEmpty()) k.label[0] else k.action, k.weight, pct) { }
        b.isEnabled = false
        return b
    }

    private fun charKey(k: KeyDef, pct: Int): Button {
        val b = host.newKey(host.dynLabel(k, layer, labelOf(k, false)), k.weight, pct) { host.tapChar(k, layer) }
        b.tag = CustomKeyTag(k, layer)
        host.styleLetter(b)
        charKeys.add(b)
        val lp = if (k.lp.isNotEmpty()) k.lp else (k.lpList.firstOrNull() ?: "")
        if (lp.isNotEmpty() && !lp.startsWith("@") && !layer.startsWith("bot")) showHint(b, lp)   // v0.17.6: the classic bottom row shows no hint on "."
        attachTouch(b, k, lp)
        return b
    }

    private fun showHint(b: Button, text: String) {
        try {
            if (prefs.bool("hint_hide", false)) return
            val col = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
            val al = try { HintStyle.alpha(prefs.int("hint_alpha_lv", HintStyle.LV_DEFAULT)) } catch (_: Throwable) { 170 }
            val sz = try { prefs.int("hint_size_lv", HintStyle.LV_DEFAULT) } catch (_: Throwable) { HintStyle.LV_DEFAULT }
            b.foreground = HintKeys.HintDrawable(text, col, al, sz)
        } catch (_: Throwable) { }
    }

    private fun attachTouch(b: Button, k: KeyDef, lp: String) {
        try { KeyPopup.attach(b, prefs) } catch (_: Throwable) { }   // popup bubble + detach cleanup (the listener below calls KeyPopup.touch itself)
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val hasSw = SwipeDir.hasAny(k)   // v0.17.7 (K5b): the move is tracked only on keys that have a swipe text
        val minPx = SwipeDir.minPx(b.resources.displayMetrics.density)
        var x0 = 0f; var y0 = 0f; var swipeTxt = ""
        val opts = com.mgb.keyboard.layout.LpBarPlan.options(k)   // v0.17.20 (L5a): 2 or more long-press options = slide-to-pick bar
        val useBar = opts.size >= com.mgb.keyboard.layout.LpBarPlan.MIN_OPTIONS
        var barOn = false
        val r = Runnable {
            try {
                if (lp.isEmpty()) return@Runnable
                fired = true
                if (useBar && LpBar.show(b, opts, prefs)) {   // v0.17.20: the bar is up; the finger decides on lift (MOVE / UP below)
                    barOn = true
                    KeyPopup.dismiss()
                    if (KeyCfg.hintBuzz(b)) host.feedbackAt(b)
                    return@Runnable
                }
                host.typeExtra(k, lp)
                if (KeyCfg.hintBuzz(b)) host.feedbackAt(b)
            } catch (_: Throwable) { }
        }
        b.setOnTouchListener { v, e ->
            try { if (e.actionMasked == MotionEvent.ACTION_DOWN && KeyCfg.downBuzz(v, prefs)) host.feedbackAt(v) } catch (_: Throwable) { }
            try { if (e.actionMasked == MotionEvent.ACTION_DOWN) KeyCfg.pDown() } catch (_: Throwable) { }
            try { KeyPopup.touch(v, e, prefs) } catch (_: Throwable) { }
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        fired = false
                        x0 = e.x; y0 = e.y; swipeTxt = ""   // v0.17.7
                        h.removeCallbacks(r)
                        if (lp.isNotEmpty()) {
                            val ms = KeyCfg.lpMs(prefs)
                            h.postDelayed(r, (if (ms > 0) ms else ViewConfiguration.getLongPressTimeout()).toLong())
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> if (barOn) { LpBar.move(e.rawX); true } else {   // v0.17.20 (L5a)
                        if (e.x < 0f || e.y < 0f || e.x > v.width || e.y > v.height) h.removeCallbacks(r)
                        if (hasSw && !fired) {   // v0.17.7 (K5b): swipe up / down / left / right; a swipe with a text cancels the long-press
                            val t = SwipeDir.textFor(k, SwipeDir.classify(e.x - x0, e.y - y0, minPx))
                            swipeTxt = t
                            if (t.isNotEmpty()) h.removeCallbacks(r)
                        }
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(r)
                        if (barOn) {   // v0.17.20 (L5a): lift = type the option under the finger (first one when the finger never moved)
                            barOn = false; fired = false
                            val s = LpBar.picked()
                            LpBar.dismiss(); v.isPressed = false; KeyPopup.dismiss()
                            try { if (s != null) { host.typeExtra(k, s); if (KeyCfg.hintBuzz(b)) host.feedbackAt(b) } } catch (_: Throwable) { }
                            true
                        } else if (fired) { fired = false; v.isPressed = false; KeyPopup.dismiss(); true }   // long-press typed: no tap
                        else if (swipeTxt.isNotEmpty()) {   // v0.17.7: swipe typed: no tap
                            val t = swipeTxt; swipeTxt = ""
                            v.isPressed = false; KeyPopup.dismiss()
                            try { host.typeExtra(k, t); if (KeyCfg.hintBuzz(b)) host.feedbackAt(b) } catch (_: Throwable) { }
                            true
                        } else false
                    }
                    MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); if (barOn) { barOn = false; LpBar.dismiss() }; fired = false; swipeTxt = ""; KeyCfg.cancelBuzz(v); false }
                    else -> false
                }
            } catch (_: Throwable) { false }
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r); if (barOn) { barOn = false; LpBar.dismiss() } }
        })
    }
}
