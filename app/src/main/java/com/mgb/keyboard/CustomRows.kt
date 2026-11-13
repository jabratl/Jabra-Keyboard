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
import com.mgb.keyboard.typing.HintStyle

/** v0.17.5 (K5a.1): what CustomRows needs from the keyboard (implemented in MgbIme, which owns the private typing functions). */
interface CustomHost {
    /** A themed key like the classic ones (key()): [pct] = row height % (100 = normal). */
    fun newKey(label: String, weight: Float, pct: Int, onClick: () -> Unit): Button
    /** Letter size % + bold, same as the classic letter keys. */
    fun styleLetter(b: Button)
    /** Empty spot: draws nothing, types nothing. */
    fun gapView(weight: Float, pct: Int): View
    /** Tap of a character key (shift, caps lock, PC Ctrl / Alt, typing). */
    fun tapChar(k: KeyDef)
    /** Long-press text (several characters allowed) or an @action. */
    fun typeExtra(s: String)
    /** An ACTION key (shift, backspace, enter, sym, space, lang). null = not supported in this version (K5b): the key stays inert. */
    fun actionKey(k: KeyDef, pct: Int): Button?
    fun feedbackAt(v: View)
}

/** Tag of a custom key (letter keys use Char tags and are not touched by relabelLetters / HintKeys). */
class CustomKeyTag(val key: KeyDef)

/**
 * v0.17.5 (K5a.1) builds the rows of layer "en" from a LayoutDef: character keys (tap, shift output, long-press text, popup, feedback),
 * ACTION keys through the host, empty spots. Reference rows (shared number row / bottom row) are skipped: the classic ones stay.
 * ONE touch listener per key (like HintKeys.attach): feedback at down, KeyPopup.touch, long-press via Handler.
 */
class CustomRows(private val host: CustomHost, private val prefs: JabraPrefs) {
    val rows = mutableListOf<LinearLayout>()
    private val charKeys = mutableListOf<Button>()

    /** Builds the rows. Empty list = nothing to draw (the caller keeps the classic rows). Any problem inside = empty list. */
    fun build(model: LayoutDef, newRow: () -> LinearLayout): List<LinearLayout> {
        rows.clear(); charKeys.clear()
        try {
            val own: List<RowDef> = model.layer("en")?.rows?.filter { !it.isRef() } ?: return rows
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
            val t = labelOf(k, shift)
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
        val b = host.newKey(labelOf(k, false), k.weight, pct) { host.tapChar(k) }
        b.tag = CustomKeyTag(k)
        host.styleLetter(b)
        charKeys.add(b)
        val lp = if (k.lp.isNotEmpty()) k.lp else (k.lpList.firstOrNull() ?: "")
        if (lp.isNotEmpty() && !lp.startsWith("@")) showHint(b, lp)
        attachTouch(b, lp)
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

    private fun attachTouch(b: Button, lp: String) {
        try { KeyPopup.attach(b, prefs) } catch (_: Throwable) { }   // popup bubble + detach cleanup (the listener below calls KeyPopup.touch itself)
        val h = Handler(Looper.getMainLooper())
        var fired = false
        val r = Runnable {
            try {
                if (lp.isEmpty()) return@Runnable
                fired = true
                host.typeExtra(lp)
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
                        h.removeCallbacks(r)
                        if (lp.isNotEmpty()) {
                            val ms = KeyCfg.lpMs(prefs)
                            h.postDelayed(r, (if (ms > 0) ms else ViewConfiguration.getLongPressTimeout()).toLong())
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> { if (e.x < 0f || e.y < 0f || e.x > v.width || e.y > v.height) h.removeCallbacks(r); false }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(r)
                        if (fired) { fired = false; v.isPressed = false; KeyPopup.dismiss(); true } else false   // long-press typed: no tap
                    }
                    MotionEvent.ACTION_CANCEL -> { h.removeCallbacks(r); fired = false; KeyCfg.cancelBuzz(v); false }
                    else -> false
                }
            } catch (_: Throwable) { false }
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacks(r) }
        })
    }
}
