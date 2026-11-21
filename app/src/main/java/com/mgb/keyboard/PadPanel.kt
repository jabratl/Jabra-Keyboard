package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
    fun jump(top: Boolean, selectOn: Boolean)   // v0.17.36 (M3): Top / Bottom of the whole text
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

    // v0.10.14 (D2.4): pk() = UiKit.btn (key-height tall, same 2dp margins, theme style, feedback first). Old body kept as pkOld() = fallback.
    // v0.45.2: sp = text size (arrows are bigger, 14f = as before for every other key).
    private fun pk(label: String, weight: Float, sp: Float = 14f, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, host.keyHeightDp(), sp, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, sp, onClick)
    }

    private fun pkOld(label: String, weight: Float, sp: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = sp
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
    private fun held(label: String, sp: Float = 14f, fire: () -> Unit): Button {
        val b = pk(label, 1f, sp) { }
        try { RepeatKeyHelper({ fire() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(b) } catch (_: Throwable) { }
        return b
    }

    private fun move(code: Int) { io.sendKey(code, state.meta()) }

    // v0.45.2: look of the pad keys. The Select key and every key that Select changes (arrows, Home, End, Top, Bottom) are remembered with their
    // normal look; Select ON = Select key strong blue (white bold text), those keys soft blue (dark text, readable in every theme); OFF = the normal look back.
    private class Saved(val tint: ColorStateList?, val txt: ColorStateList, val face: Typeface?)

    private val tracked = ArrayList<Pair<Button, Saved>>()
    private var selectBtn: Button? = null
    private var statusView: TextView? = null

    private fun track(b: Button): Button {
        try { tracked.add(Pair(b, Saved(b.backgroundTintList, b.textColors, b.typeface))) } catch (_: Throwable) { }
        return b
    }

    private fun restore(b: Button, s: Saved) {
        b.backgroundTintList = s.tint
        b.setTextColor(s.txt)
        b.typeface = s.face
    }

    private fun paintSelect() {
        val on = state.isSelect()
        for ((b, s) in tracked) {
            try {
                if (b === selectBtn) {
                    b.text = if (on) "Select ON" else "Select"
                    try { b.contentDescription = if (on) "Select on" else "Select off" } catch (_: Throwable) { }
                    if (on) {
                        b.backgroundTintList = ColorStateList.valueOf(JabraTokens.ACCENT)
                        b.setTextColor(Color.WHITE)
                        b.setTypeface(s.face, Typeface.BOLD)
                    } else restore(b, s)
                } else if (on) {
                    b.backgroundTintList = ColorStateList.valueOf(JabraTokens.ACCENT_SOFT)
                    b.setTextColor(JabraTokens.ON_ACCENT_SOFT)
                } else restore(b, s)
            } catch (_: Throwable) { }
        }
        try { statusView?.text = statusText(on) } catch (_: Throwable) { }
    }

    private fun statusText(on: Boolean): String =
        (if (on) "Select ON: blue keys extend the selection.\n" else "Select OFF: keys only move the cursor.\n") +
        "Home / End = this line, Top / Bottom = whole text. Arrows need an app that reads key events."

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
    /** v0.17.35 (M2): keepSelect = the pad is rebuilt while it is already open (Select stays as the user left it). A fresh open starts with Select OFF as before. */
    fun build(keepSelect: Boolean = false): View? = try { make(keepSelect) } catch (_: Throwable) { null }

    private fun make(keepSelect: Boolean): View {
        if (!keepSelect) state.setSelect(false)   // Select starts OFF when the pad opens (v0.17.35: not when it is only rebuilt)
        tracked.clear()
        selectBtn = null
        statusView = null
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // v0.45.2 layout (4 columns): the LEFT 3 columns = moving (Select, Top / Bottom, Home / up / End, arrows), the RIGHT column = editing (Backspace, Copy, Cut, Paste).
        val r1 = row()
        val sel = pk("Select", 1.5f) { _ -> state.toggle(); paintSelect() }
        selectBtn = sel
        r1.addView(track(sel))
        r1.addView(track(pk("Top", 1f) { io.jump(true, state.isSelect()) }))   // v0.17.36 (M3): cursor to the very start (Select ON: select up to it)
        r1.addView(track(pk("Bottom", 1f) { io.jump(false, state.isSelect()) }))   // v0.17.36 (M3): cursor to the very end (Select ON: select up to it)
        val bs = pk("\u232B", 1f) { }
        try { JabraIcons.putThemed(ctx, bs, "back") } catch (_: Throwable) { }
        try { RepeatKeyHelper({ host.backspace() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(bs) } catch (_: Throwable) { }
        r1.addView(bs)
        root.addView(r1)

        val r2 = row()
        r2.addView(track(held("Home") { move(KeyEvent.KEYCODE_MOVE_HOME) }))
        r2.addView(track(held("\u2191", 20f) { move(KeyEvent.KEYCODE_DPAD_UP) }))
        r2.addView(track(held("End") { move(KeyEvent.KEYCODE_MOVE_END) }))
        r2.addView(pk("Copy", 1f) { io.edit("copy") })
        root.addView(r2)

        val r3 = row()
        r3.addView(track(held("\u2190", 20f) { move(KeyEvent.KEYCODE_DPAD_LEFT) }))
        r3.addView(track(held("\u2193", 20f) { move(KeyEvent.KEYCODE_DPAD_DOWN) }))
        r3.addView(track(held("\u2192", 20f) { move(KeyEvent.KEYCODE_DPAD_RIGHT) }))
        r3.addView(pk("Cut", 1f) { io.edit("cut") })
        root.addView(r3)

        val r4 = row()
        r4.addView(pk("ABC", 1f) { state.setSelect(false); host.showLetters() })   // v0.17.35 (M2): ABC is the only way out; Select ends with it
        r4.addView(pk("All", 1f) { io.edit("all") })
        r4.addView(pk("Paste", 2f) { io.edit("paste") })
        root.addView(r4)

        val st = note("")
        statusView = st
        root.addView(st)   // v0.45.2: the note is now the Select status (first line) + the short help (second line)
        paintSelect()
        return root
    }
}
