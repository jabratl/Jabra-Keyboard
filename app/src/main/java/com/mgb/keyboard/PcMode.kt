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
}

/**
 * v0.1.2 PC rows (shown above the letters when mode = PC):
 *  Esc Tab Ctrl Alt Del Ins / F1..F12 / Home PgUp Up PgDn End Left Down Right / Ctrl+A C V X Z.
 * Ctrl and Alt are sticky: 1 tap = next key only, 2nd tap quickly = lock, tap again = off.
 * Built in code, never throws out (view() returns null on error -> keyboard shows only the letters).
 */
class PcRows(private val ctx: android.content.Context, private val st: PcState, private val io: PcIo) {

    private val prefs = JabraPrefs(ctx)
    private var ctrlBtn: Button? = null
    private var altBtn: Button? = null

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
    fun refresh() { try { tint(ctrlBtn, st.ctrl()); tint(altBtn, st.alt()) } catch (_: Throwable) { } }

    private fun plain(label: String, code: Int, size: Float = 12f): Button = k(label, size) { io.sendKey(code, 0) }

    fun view(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL

        val a = row()
        a.addView(plain("Esc", KeyEvent.KEYCODE_ESCAPE))
        a.addView(plain("Tab", KeyEvent.KEYCODE_TAB))
        val cb = k("Ctrl") { st.tapCtrl(android.os.SystemClock.uptimeMillis()); refresh() }
        val ab = k("Alt") { st.tapAlt(android.os.SystemClock.uptimeMillis()); refresh() }
        ctrlBtn = cb; altBtn = ab
        a.addView(cb); a.addView(ab)
        a.addView(plain("Del", KeyEvent.KEYCODE_FORWARD_DEL))
        a.addView(plain("Ins", KeyEvent.KEYCODE_INSERT))
        col.addView(a)

        val f = row()
        for (i in 1..12) f.addView(plain("F$i", KeyEvent.KEYCODE_F1 + i - 1, 10f))
        col.addView(f)

        val n = row()
        n.addView(plain("Home", KeyEvent.KEYCODE_MOVE_HOME, 10f))
        n.addView(plain("PgUp", KeyEvent.KEYCODE_PAGE_UP, 10f))
        n.addView(plain("\u2191", KeyEvent.KEYCODE_DPAD_UP, 14f))
        n.addView(plain("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 10f))
        n.addView(plain("End", KeyEvent.KEYCODE_MOVE_END, 10f))
        n.addView(plain("\u2190", KeyEvent.KEYCODE_DPAD_LEFT, 14f))
        n.addView(plain("\u2193", KeyEvent.KEYCODE_DPAD_DOWN, 14f))
        n.addView(plain("\u2192", KeyEvent.KEYCODE_DPAD_RIGHT, 14f))
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
}
