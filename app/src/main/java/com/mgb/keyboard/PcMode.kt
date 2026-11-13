package com.mgb.keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.typing.PcState

/** v0.1.2: what the PC rows need from the keyboard service (implemented in MgbIme). */
interface PcIo {
    fun sendKey(code: Int, extraMeta: Int)   // key down + up with the sticky Ctrl / Alt added
    fun edit(kind: String)                   // the existing edit bar action: all / cut / copy / paste
    fun feedback(v: View)
    fun keyDp(): Int
    fun holdStart(code: Int)   // v0.17.49 (PC1): key DOWN stays down (sticky mods go down first)
    fun holdRepeat(code: Int, n: Int)   // v0.17.49: repeat DOWN with count n
    fun holdEnd()   // v0.17.49: key UP, then modifier UP(s); safe to call twice
    fun modTouched()   // v0.17.49: a modifier button was tapped (starts the 8 s idle release of a ONCE modifier)
}

/**
 * v0.1.2 PC rows (shown above the letters when mode = PC):
 *  Esc Tab Ctrl Alt Del Ins / F1..F12 / Home PgUp Up PgDn End Left Down Right / Ctrl+A C V X Z.
 * Ctrl and Alt are sticky: 1 tap = next key only, 2nd tap quickly = lock, tap again = off.
 * v0.17.49 (PC1): + Shift and Win (same sticky rules); arrows / PgUp / PgDn / Del repeat while held (key down once, then repeat DOWN with count).
 * Built in code, never throws out (view() returns null on error -> keyboard shows only the letters).
 */
class PcRows(private val ctx: android.content.Context, private val st: PcState, private val io: PcIo) {

    private val prefs = JabraPrefs(ctx)
    private var ctrlBtn: Button? = null
    private var altBtn: Button? = null
    private var shiftBtn: Button? = null   // v0.17.49
    private var winBtn: Button? = null   // v0.17.49
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())   // v0.17.49: hold-repeat timer

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun k(label: String, size: Float = 12f, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = size
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { io.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
        lp.setMargins(dp(1), dp(2), dp(1), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun tint(b: Button?, level: Int) {
        if (b == null) return
        b.backgroundTintList = when (level) {
            PcState.LOCK -> ColorStateList.valueOf(Color.parseColor("#2271B1"))
            PcState.ONCE -> ColorStateList.valueOf(Color.parseColor("#9CC3E6"))
            else -> (try { JabraThemes.keyTint(prefs) } catch (_: Throwable) { null })
        }
    }

    /** Call after Ctrl / Alt state changed (also after a key was sent). */
    fun refresh() { try { tint(ctrlBtn, st.ctrl()); tint(altBtn, st.alt()); tint(shiftBtn, st.shift()); tint(winBtn, st.win()) } catch (_: Throwable) { } }

    private fun plain(label: String, code: Int, size: Float = 12f): Button = k(label, size) { io.sendKey(code, 0) }

    /** v0.17.49 (PC1): key that goes DOWN once, repeats while held (first ~350 ms, then every ~50 ms) and goes UP on release / cancel / detach. */
    private fun held(label: String, code: Int, size: Float = 12f): Button {
        val b = k(label, size) { }
        var down = false
        var n = 0
        val rep = object : Runnable {
            override fun run() {
                if (!down) return
                n++
                try { io.holdRepeat(code, n) } catch (_: Throwable) { }
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        fun release() {
            handler.removeCallbacks(rep)
            if (down) { down = false; try { io.holdEnd() } catch (_: Throwable) { } }
            b.isPressed = false
        }
        b.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { }
            override fun onViewDetachedFromWindow(v: View) { release() }   // never leave a key down when the row disappears
        })
        b.setOnTouchListener { _, ev ->
            try {
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        release()
                        b.isPressed = true
                        try { io.feedback(b) } catch (_: Throwable) { }
                        down = true
                        n = 0
                        io.holdStart(code)
                        handler.postDelayed(rep, FIRST_MS)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { release(); true }
                    else -> true
                }
            } catch (_: Throwable) { release(); true }
        }
        return b
    }

    fun view(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL

        val a = row()
        a.addView(plain("Esc", KeyEvent.KEYCODE_ESCAPE))
        a.addView(plain("Tab", KeyEvent.KEYCODE_TAB))
        val cb = k("Ctrl") { st.tapCtrl(android.os.SystemClock.uptimeMillis()); refresh(); io.modTouched() }
        val ab = k("Alt") { st.tapAlt(android.os.SystemClock.uptimeMillis()); refresh(); io.modTouched() }
        val sb = k("Shift", 11f) { st.tapShift(android.os.SystemClock.uptimeMillis()); refresh(); io.modTouched() }   // v0.17.49
        val wb = k("Win", 11f) { st.tapWin(android.os.SystemClock.uptimeMillis()); refresh(); io.modTouched() }   // v0.17.49
        ctrlBtn = cb; altBtn = ab; shiftBtn = sb; winBtn = wb
        a.addView(cb); a.addView(ab); a.addView(sb); a.addView(wb)
        a.addView(held("Del", KeyEvent.KEYCODE_FORWARD_DEL))   // v0.17.49: repeats while held
        a.addView(plain("Ins", KeyEvent.KEYCODE_INSERT))
        col.addView(a)

        val f = row()
        for (i in 1..12) f.addView(plain("F$i", KeyEvent.KEYCODE_F1 + i - 1, 10f))
        col.addView(f)

        val n = row()
        n.addView(plain("Home", KeyEvent.KEYCODE_MOVE_HOME, 10f))
        n.addView(held("PgUp", KeyEvent.KEYCODE_PAGE_UP, 10f))
        n.addView(held("\u2191", KeyEvent.KEYCODE_DPAD_UP, 14f))
        n.addView(held("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 10f))
        n.addView(plain("End", KeyEvent.KEYCODE_MOVE_END, 10f))
        n.addView(held("\u2190", KeyEvent.KEYCODE_DPAD_LEFT, 14f))
        n.addView(held("\u2193", KeyEvent.KEYCODE_DPAD_DOWN, 14f))
        n.addView(held("\u2192", KeyEvent.KEYCODE_DPAD_RIGHT, 14f))
        col.addView(n)

        val c = row()
        c.addView(k("^A") { io.edit("all") })
        c.addView(k("^C") { io.edit("copy") })
        c.addView(k("^V") { io.edit("paste") })
        c.addView(k("^X") { io.edit("cut") })
        c.addView(k("^Z") { io.sendKey(KeyEvent.KEYCODE_Z, PcState.META_CTRL) })
        col.addView(c)

        refresh()
        return col
    }

    companion object {
        private const val FIRST_MS = 350L   // v0.17.49: delay before the first repeat
        private const val REPEAT_MS = 50L   // v0.17.49: repeat rate cap (a slow app is never flooded)
    }
}
