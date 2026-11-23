package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.MotionEvent
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
    fun word(left: Boolean, selectOn: Boolean) { }   // v0.45.3: one word left / right (default: does nothing)
}

/**
 * v0.2.3 cursor / select pad (route showPanel("pad"), toolbar item "pad", More settings -> Cursor pad >).
 * Keys: Select (toggle) | Up | All | Backspace  /  Left | Down | Right | Copy  /  Home | End | Cut | Paste  /  ABC.
 * Select ON = arrows + Home / End are sent with Shift, so they extend the selection (the app must read key events, same limit as PC mode).
 * Arrows, Home / End and Backspace repeat while held. Built in code like the other panels; build() returns null on any problem.
 *
 * v0.46.0/v0.46.1 (redesign): rows are grouped into rounded theme-aware cards (UiKit.cardBackground) instead of
 * one flat block, and the status line is a single-line coloured badge instead of two lines of text. The status
 * line also doubles as a drag strip ("trackpad"): drag left/right/up/down on it to glide the cursor (or extend
 * the selection when Select is ON) without tapping the small arrow keys one at a time (v0.46.1: moved onto the
 * existing status line instead of its own row -- see the note on attachDrag() for why). A new "Clear" key (extras
 * row) collapses an active selection (cursor jumps to the right edge, Select switches back OFF) without leaving
 * the pad.
 */
class PadPanel(private val ctx: Context, private val host: JabraHost, private val io: PadIo) {

    private val prefs = JabraPrefs(ctx)
    private val state = PadState()

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.14 (D2.4): pk() = UiKit.btn (key-height tall, same 2dp margins, theme style, feedback first). Old body kept as pkOld() = fallback.
    // v0.45.2: sp = text size (arrows are bigger, 14f = as before for every other key).
    // v0.45.4: h = key height in dp (0 = the normal key height; the extra rows are lower).
    private fun pk(label: String, weight: Float, sp: Float = 14f, h: Int = 0, onClick: (Button) -> Unit): Button {
        val hh = if (h > 0) h else host.keyHeightDp()
        try { return UiKit.btn(ctx, prefs, label, weight, hh, sp, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, sp, hh, onClick)
    }

    private fun pkOld(label: String, weight: Float, sp: Float, hh: Int, onClick: (Button) -> Unit): Button {
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
        val lp = LinearLayout.LayoutParams(0, dp(hh), weight)
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

    // v0.46.1 (was v0.46.0): wrap one or more rows in a rounded, theme-aware card (same look as the clipboard
    // cards). v0.46.0 used a 2dp margin + 1dp padding on every card, which (together with the drag strip as its
    // own row) made the whole pad tall enough to cross MgbIme's capTall() cap on some devices/themes -> the pad
    // got wrapped in a ScrollView there, and a ScrollView above a grid of small buttons swallows some taps as
    // scroll gestures (the intermittent Top/Bottom/Select bugs). v0.46.1 trims the card to a 1dp margin and no
    // extra padding, so the added height versus the pre-card layout is a few dp, not ~25dp per card.
    private fun cardWrap(vararg rows: LinearLayout): LinearLayout {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        try { card.background = UiKit.cardBackground(ctx, prefs) } catch (_: Throwable) { }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(1), dp(1), dp(1), dp(1))
        card.layoutParams = lp
        for (r in rows) card.addView(r)
        return card
    }

    // v0.46.1: the v0.46.0 trackpad was a brand-new row (+ ~22dp of height) between the top row and the arrow
    // grid -- that extra row was the main reason the pad could cross capTall()'s cap (see cardWrap note above).
    // Same drag-to-glide feature, zero extra height: the gesture is attached to the status line at the bottom,
    // which the pad already had. Drag it like a trackpad to glide the cursor (Select ON = extend the selection);
    // a plain tap does nothing (only drags trigger movement), so it still reads fine as a status line.
    private fun attachDrag(bar: TextView) {
        val step = dp(16).coerceAtLeast(12)
        var lastX = 0f; var lastY = 0f; var accX = 0f; var accY = 0f
        bar.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                // must return true on DOWN or the OS never delivers the MOVE events that follow
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY; accX = 0f; accY = 0f; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - lastX; val dy = e.rawY - lastY
                    lastX = e.rawX; lastY = e.rawY
                    accX += dx; accY += dy
                    var fired = false
                    // horizontal wins when it is the larger movement (avoids sending both on a diagonal drag)
                    while (kotlin.math.abs(accX) >= step && kotlin.math.abs(accX) >= kotlin.math.abs(accY)) {
                        move(if (accX > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
                        accX -= if (accX > 0) step else -step
                        fired = true
                    }
                    while (kotlin.math.abs(accY) >= step && kotlin.math.abs(accY) > kotlin.math.abs(accX)) {
                        move(if (accY > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP)
                        accY -= if (accY > 0) step else -step
                        fired = true
                    }
                    if (fired) { try { host.feedback(v) } catch (_: Throwable) { } }
                    true
                }
                else -> true
            }
        }
    }

    /** Key that fires at once and repeats while held (same helper as Backspace). */
    private fun held(label: String, sp: Float = 14f, h: Int = 0, fire: () -> Unit): Button {
        val b = pk(label, 1f, sp, h) { }
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

    private var extras = true   // v0.45.4: pref pad_extra (read when the pad is built)

    private fun statusText(on: Boolean): String =
        (if (on) "\u25CF Select ON \u2014 blue keys extend the selection" else "\u25CB Select OFF \u2014 keys just move the cursor") +
        " \u00b7 drag this line to glide"

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
        root.setPadding(dp(2), dp(3), dp(2), dp(3))

        // v0.45.2 layout (4 columns): the LEFT 3 columns = moving (Select, Top / Bottom, Home / up / End, arrows), the RIGHT column = editing (Backspace, Copy, Cut, Paste).
        // v0.46.0: the rows are the same keys as before, just grouped into cards (cardWrap) instead of sitting flat on the panel background.
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
        root.addView(cardWrap(r1))

        val r2 = row()
        r2.addView(track(held("Home") { move(KeyEvent.KEYCODE_MOVE_HOME) }))
        r2.addView(track(held("\u2191", 20f) { move(KeyEvent.KEYCODE_DPAD_UP) }))
        r2.addView(track(held("End") { move(KeyEvent.KEYCODE_MOVE_END) }))
        r2.addView(pk("Copy", 1f) { io.edit("copy") })

        val r3 = row()
        r3.addView(track(held("\u2190", 20f) { move(KeyEvent.KEYCODE_DPAD_LEFT) }))
        r3.addView(track(held("\u2193", 20f) { move(KeyEvent.KEYCODE_DPAD_DOWN) }))
        r3.addView(track(held("\u2192", 20f) { move(KeyEvent.KEYCODE_DPAD_RIGHT) }))
        r3.addView(pk("Cut", 1f) { io.edit("cut") })
        root.addView(cardWrap(r2, r3))

        val r4 = row()
        r4.addView(pk("ABC", 1f) { state.setSelect(false); host.showLetters() })   // v0.17.35 (M2): ABC is the only way out; Select ends with it
        r4.addView(pk("All", 1f) { io.edit("all") })
        r4.addView(pk("Paste", 2f) { io.edit("paste") })
        root.addView(cardWrap(r4))

        // v0.45.3 / v0.45.4 extra rows (pref pad_extra, default ON, OFF = the 4 old rows): row 5 = move by word / page, row 6 = Line / Clear / Undo / Redo. Lower keys (85%).
        extras = try { prefs.bool("pad_extra", true) } catch (_: Throwable) { true }
        if (extras) {
            val eh = try { (host.keyHeightDp() * 85 / 100).coerceAtLeast(30) } catch (_: Throwable) { 36 }
            val r5 = row()
            val wl = held("\u25C2 Word", 12f, eh) { io.word(true, state.isSelect()) }
            try { wl.contentDescription = "Previous word" } catch (_: Throwable) { }
            r5.addView(track(wl))
            val wr = held("Word \u25B8", 12f, eh) { io.word(false, state.isSelect()) }
            try { wr.contentDescription = "Next word" } catch (_: Throwable) { }
            r5.addView(track(wr))
            val pu = held("PgUp", 12f, eh) { move(KeyEvent.KEYCODE_PAGE_UP) }   // Select ON = Shift + Page Up (selects a page)
            try { pu.contentDescription = "Page up" } catch (_: Throwable) { }
            r5.addView(track(pu))
            val pd = held("PgDn", 12f, eh) { move(KeyEvent.KEYCODE_PAGE_DOWN) }
            try { pd.contentDescription = "Page down" } catch (_: Throwable) { }
            r5.addView(track(pd))

            val r6 = row()
            val ln = pk("Line", 1f, 12f, eh) { io.sendKey(KeyEvent.KEYCODE_MOVE_HOME, 0); io.sendKey(KeyEvent.KEYCODE_MOVE_END, PadState.META_SHIFT) }
            try { ln.contentDescription = "Select this line" } catch (_: Throwable) { }
            r6.addView(ln)
            // v0.46.0 (new): collapses an active selection to its right edge and switches Select back OFF,
            // instead of having to tap an arrow key just to drop out of a selection you no longer want.
            val clr = pk("Clear", 1f, 12f, eh) { io.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, 0); if (state.isSelect()) { state.setSelect(false); paintSelect() } }
            try { clr.contentDescription = "Clear selection" } catch (_: Throwable) { }
            r6.addView(clr)
            r6.addView(pk("Undo", 1f, 12f, eh) { io.sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON) })
            r6.addView(pk("Redo", 1f, 12f, eh) { io.sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON) })
            root.addView(cardWrap(r5, r6))
        }

        // v0.46.1: single-line coloured status badge (was a two-line plain note); also doubles as the drag strip
        // (attachDrag) so the glide feature costs no extra row / height.
        val st = note("")
        st.gravity = android.view.Gravity.CENTER
        statusView = st
        try { attachDrag(st) } catch (_: Throwable) { }
        root.addView(st)
        paintSelect()
        return root
    }
}
