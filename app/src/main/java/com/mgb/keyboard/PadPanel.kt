package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.PadState

/** v0.2.3: MgbIme implements this (key event with meta bits, and the existing edit-bar actions all / copy / cut / paste). */
interface PadIo {
    fun sendKey(code: Int, meta: Int)
    fun edit(kind: String)
}

/**
 * v0.2.3 cursor / select pad (route showPanel("pad"), toolbar item "pad", More settings -> Cursor pad >).
 * Keys: Select (toggle) | Up | All | Backspace  /  Left | Down | Right | Copy  /  Home | End | Cut | Paste  /  ABC.
 * Select ON = arrows + Home / End are sent with Shift, so they extend the selection (the app must read key events, same limit as PC mode).
 * Arrows, Home / End and Backspace repeat while held. Built in code like the other panels; build() returns null on any problem.
 */
class PadPanel(private val ctx: Context, private val host: JabraHost, private val io: PadIo) {

    private val prefs = JabraPrefs(ctx)
    private val state = PadState()

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.14 (D2.4): pk() = UiKit.btn (key-height tall, 14f, same 2dp margins, theme style, feedback first). Old body kept as pkOld() = fallback.
    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, host.keyHeightDp(), 14f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, onClick)
    }

    private fun pkOld(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 14f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(host.keyHeightDp()), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    // v0.10.14 (D2.4): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    /** Key that fires at once and repeats while held (same helper as Backspace). */
    private fun held(label: String, fire: () -> Unit): Button {
        val b = pk(label, 1f) { }
        try { RepeatKeyHelper({ fire() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(b) } catch (_: Throwable) { }
        return b
    }

    private fun move(code: Int) { io.sendKey(code, state.meta()) }

    private fun selectLook(b: Button) {
        b.text = if (state.isSelect()) "Select ON" else "Select"
        try {
            b.backgroundTintList = if (state.isSelect()) ColorStateList.valueOf(Color.parseColor("#2271B1"))
                else (try { JabraThemes.keyTint(prefs) } catch (_: Throwable) { null })
        } catch (_: Throwable) { }
    }

    // v0.10.14 (D2.4): note() = UiKit.noteText (11sp, note colour, 6 / 2dp padding). Old body kept as noteOld() = fallback.
    private fun note(text: String): TextView {
        try { return UiKit.noteText(ctx, prefs, text) } catch (_: Throwable) { }
        return noteOld(text)
    }

    private fun noteOld(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        state.setSelect(false)   // Select always starts OFF when the pad opens
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val r1 = row()
        val sel = pk("Select", 1f) { btn -> state.toggle(); selectLook(btn) }
        selectLook(sel)
        r1.addView(sel)
        r1.addView(held("\u2191") { move(KeyEvent.KEYCODE_DPAD_UP) })
        r1.addView(pk("All", 1f) { io.edit("all") })
        val bs = pk("\u232B", 1f) { }
        try { JabraIcons.putThemed(ctx, bs, "back") } catch (_: Throwable) { }
        try { RepeatKeyHelper({ host.backspace() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(bs) } catch (_: Throwable) { }
        r1.addView(bs)
        root.addView(r1)

        val r2 = row()
        r2.addView(held("\u2190") { move(KeyEvent.KEYCODE_DPAD_LEFT) })
        r2.addView(held("\u2193") { move(KeyEvent.KEYCODE_DPAD_DOWN) })
        r2.addView(held("\u2192") { move(KeyEvent.KEYCODE_DPAD_RIGHT) })
        r2.addView(pk("Copy", 1f) { io.edit("copy") })
        root.addView(r2)

        val r3 = row()
        r3.addView(held("Home") { move(KeyEvent.KEYCODE_MOVE_HOME) })
        r3.addView(held("End") { move(KeyEvent.KEYCODE_MOVE_END) })
        r3.addView(pk("Cut", 1f) { io.edit("cut") })
        r3.addView(pk("Paste", 1f) { io.edit("paste") })
        root.addView(r3)

        root.addView(note("Select ON + arrows / Home / End = extend the selection. Arrows, Home, End need an app that reads key events (same limit as PC mode); All / Copy / Cut / Paste work in every app."))

        val r4 = row()
        r4.addView(pk("ABC", 1f) { host.showLetters() })
        root.addView(r4)
        return root
    }
}
