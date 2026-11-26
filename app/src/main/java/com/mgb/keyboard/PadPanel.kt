package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.PadGesture
import com.mgb.keyboard.typing.PadInfo
import com.mgb.keyboard.typing.PadState

/** v0.2.3: MgbIme implements this (key event with meta bits, and the existing edit-bar actions all / copy / cut / paste). */
interface PadIo {
    fun sendKey(code: Int, meta: Int)
    fun edit(kind: String)
    fun jump(top: Boolean, selectOn: Boolean)   // v0.17.36 (M3): Top / Bottom of the whole text
    fun word(left: Boolean, selectOn: Boolean) { }   // v0.45.3: one word left / right (default: does nothing)
    fun delWord() { }   // v0.51.0: delete one word before the cursor (a selection = delete the selection)
    fun delFwd() { }    // v0.51.0: forward Delete (a selection = delete the selection)
    fun info(): IntArray? = null   // v0.51.0: {line, column, selected chars, selected words}, -1 = not known; null = nothing known
}

/**
 * Cursor / select pad (route showPanel("pad"), toolbar item "pad", More settings -> Cursor pad >).
 *
 * v0.51.0 (redesign "minimal + powerful", from cursor-pad-redesign-mock.html): the pad is now 4 blocks instead of 6 card rows.
 *   1. top bar:   ABC | "Cursor" | trackpad speed pill (tap = Slow / Normal / Fast) | Select pill
 *   2. trackpad:  ONE big pad (dashed outline, solid blue when Select is ON). Drag = glide, 2 taps = word, 3 taps = line.
 *                 (it replaced the thin status line that used to carry the drag gesture)
 *   3. keys:      LEFT = D-pad with a round Select key in the middle; RIGHT = jump keys in 2 columns
 *                 (Word left / right, Home / End, PgUp / PgDn, Top / Bottom)
 *   4. edit:      row 1 = All, Line, Copy, Cut, Paste; row 2 = Undo, Redo, Backspace, Backspace Word, Delete
 *   + live status line under the keys: "Ln 2, Col 7  .  12 chars, 3 words selected" (follows the cursor, shows "Copied" / "Cut" for a moment)
 * Red text only on the 3 delete keys. Select ON = Select key / pill / trackpad outline strong blue, every moving key soft blue.
 * Turning Select OFF while text is selected collapses the selection (same as the old "Clear" key, which is gone).
 * Setting "pad_extra" (default ON) still decides the extra keys: OFF = no Word / PgUp / PgDn jump keys, no Line / Undo / Redo / Backspace Word.
 * Everything is sized from the key height (host.keyHeightDp()), so the pad stays about as tall as the old one (no new ScrollView in capTall()).
 * Built in code like the other panels; build() returns null on any problem.
 *
 * Older notes: v0.46.x grouped rows into cards and put the drag gesture on the status line; v0.48.0 / v0.48.1 added the speed / delay settings,
 * double / triple tap and auto-open. All of that logic is unchanged (typing/PadGesture.java); only where it sits on screen changed.
 */
class PadPanel(private val ctx: Context, private val host: JabraHost, private val io: PadIo) {

    private val prefs = JabraPrefs(ctx)
    private val state = PadState()
    private val ui = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.14 (D2.4): pk() = UiKit.btn (key-height tall, same 2dp margins, theme style, feedback first). Old body kept as pkOld() = fallback.
    // v0.45.2: sp = text size. v0.45.4: h = key height in dp (0 = the normal key height).
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
        val lp = if (weight > 0f) LinearLayout.LayoutParams(0, dp(hh), weight) else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(hh))
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

    private fun col(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        return c
    }

    /** Empty cell of the D-pad grid (same width share as a key, keeps the arrows in the middle). */
    private fun gap(hDp: Int): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(0, dp(hDp) + dp(4), 1f)
        return v
    }

    // ------------------------------------------------------------------ trackpad (drag / tap gestures)

    private fun stepDp(): Int = PadGesture.stepDp(speedLevel())

    private fun speedLevel(): Int = try { prefs.int("pad_speed", PadGesture.SPEED_DEF).coerceIn(0, 2) } catch (_: Throwable) { PadGesture.SPEED_DEF }

    private fun selectWordAtCursor() {
        // cursor to the start of the current word, then extend (Shift) to its end; both calls go through the same
        // io.word() the Word keys use, so the app-side fallback for fields that ignore Ctrl+Arrow applies here too
        io.word(true, false)
        io.word(false, true)
        selectionMade()
    }

    private fun selectLineAtCursor() {
        io.sendKey(KeyEvent.KEYCODE_MOVE_HOME, 0)
        io.sendKey(KeyEvent.KEYCODE_MOVE_END, PadState.META_SHIFT)
        selectionMade()
    }

    /** A selection is on screen now: show Select ON so the arrows keep extending it (same as the auto-open path). */
    private fun selectionMade() {
        if (!state.isSelect()) { state.setSelect(true); paintSelect() }
    }

    /** v0.51.0: same gesture code as v0.48.1 (PadGesture does the rules), now on the big trackpad view instead of the status line. */
    private fun attachDrag(pad: View) {
        var step = dp(stepDp()).coerceAtLeast(8)
        val glide = PadGesture.Glide()
        val taps = PadGesture.TapCounter()
        var lastX = 0f; var lastY = 0f; var downX = 0f; var downY = 0f
        var moved = false          // did the current touch drag at all?
        val slop = dp(8).coerceAtLeast(6).toFloat()
        pad.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                // must return true on DOWN or the OS never delivers the MOVE events that follow
                MotionEvent.ACTION_DOWN -> {
                    try { v.parent?.requestDisallowInterceptTouchEvent(true) } catch (_: Throwable) { }   // v0.51.0: a ScrollView around the pad must not steal the drag
                    step = dp(stepDp()).coerceAtLeast(8)
                    lastX = e.rawX; lastY = e.rawY; downX = e.rawX; downY = e.rawY; glide.reset(); moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawX - downX) > slop || kotlin.math.abs(e.rawY - downY) > slop) { moved = true; taps.reset() }   // a drag is never part of a multi-tap
                    val dx = e.rawX - lastX; val dy = e.rawY - lastY
                    lastX = e.rawX; lastY = e.rawY
                    val st = glide.feed(dx, dy, step)
                    var fired = false
                    for (i in 0 until kotlin.math.abs(st[0])) { move(if (st[0] > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT); fired = true }
                    for (i in 0 until kotlin.math.abs(st[1])) { move(if (st[1] > 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP); fired = true }
                    if (fired) { try { host.feedback(v) } catch (_: Throwable) { } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        when (taps.tap(SystemClock.uptimeMillis())) {
                            2 -> { try { host.feedback(v) } catch (_: Throwable) { }; try { selectWordAtCursor() } catch (_: Throwable) { } }
                            3 -> { try { host.feedback(v) } catch (_: Throwable) { }; try { selectLineAtCursor() } catch (_: Throwable) { } }
                            else -> { }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { taps.reset(); true }
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

    // ------------------------------------------------------------------ look of Select ON / OFF

    // v0.45.2: every key that Select changes is remembered with its normal look. Select ON = the "strong" keys (Select key, Select pill) strong blue
    // (white bold text), every moving key soft blue (dark text, readable in every theme); OFF = the normal look back.
    private class Saved(val tint: ColorStateList?, val txt: ColorStateList, val face: Typeface?)
    private class Tr(val b: Button, val s: Saved, val strong: Boolean)

    private val tracked = ArrayList<Tr>()
    private var statusView: TextView? = null
    private var padView: View? = null
    private var padTitle: TextView? = null
    private var padHint: TextView? = null
    private var padBg: GradientDrawable? = null
    private var flashUntil = 0L

    private fun track(b: Button, strong: Boolean = false): Button {
        try { tracked.add(Tr(b, Saved(b.backgroundTintList, b.textColors, b.typeface), strong)) } catch (_: Throwable) { }
        return b
    }

    private fun restore(b: Button, s: Saved) {
        b.backgroundTintList = s.tint
        b.setTextColor(s.txt)
        b.typeface = s.face
    }

    private fun paintSelect() {
        val on = state.isSelect()
        for (t in tracked) {
            try {
                if (t.strong) {
                    if (on) {
                        t.b.backgroundTintList = ColorStateList.valueOf(JabraTokens.ACCENT)
                        t.b.setTextColor(Color.WHITE)
                        t.b.setTypeface(t.s.face, Typeface.BOLD)
                    } else restore(t.b, t.s)
                    try { t.b.contentDescription = if (on) "Select on" else "Select off" } catch (_: Throwable) { }
                } else if (on) {
                    t.b.backgroundTintList = ColorStateList.valueOf(JabraTokens.ACCENT_SOFT)
                    t.b.setTextColor(JabraTokens.ON_ACCENT_SOFT)
                } else restore(t.b, t.s)
            } catch (_: Throwable) { }
        }
        try {
            padTitle?.text = if (on) "Drag to select" else "Drag to glide"
            paintPadOutline(on)
        } catch (_: Throwable) { }
        updateStatus()
    }

    private fun paintPadOutline(on: Boolean) {
        val bg = padBg ?: return
        if (on) bg.setStroke(dp(2), JabraTokens.ACCENT)
        else bg.setStroke(dp(1), JabraTokens.withAlpha(JabraTokens.panelText(prefs), 0x66), dp(5).toFloat(), dp(3).toFloat())
    }

    // ------------------------------------------------------------------ status line (Ln / Col / selection, short messages)

    /** Debounced: MgbIme calls this on every selection update, a long glide must not read the whole field on every step. */
    fun refresh() {
        try {
            ui.removeCallbacks(refreshRun)
            ui.postDelayed(refreshRun, 60L)
        } catch (_: Throwable) { }
    }

    private val refreshRun = Runnable { try { updateStatus() } catch (_: Throwable) { } }

    private fun updateStatus() {
        val sv = statusView ?: return
        if (SystemClock.uptimeMillis() < flashUntil) return   // a short message ("Copied") is still on screen
        sv.setTextColor(JabraTokens.note(prefs))
        sv.text = try { PadInfo.text(io.info()) } catch (_: Throwable) { "" }
    }

    /** Short message in the status line for about 0.9 s, then the numbers are back. */
    private fun flash(msg: String) {
        val sv = statusView ?: return
        flashUntil = SystemClock.uptimeMillis() + 900L
        sv.setTextColor(JabraTokens.ACCENT)
        sv.text = msg
        ui.removeCallbacks(refreshRun)
        ui.postDelayed(refreshRun, 950L)
    }

    private fun selectedChars(): Int = try { io.info()?.get(2) ?: -1 } catch (_: Throwable) { -1 }

    // v0.47.0: called by MgbIme right before showPanel("pad") when it auto-opens the pad because the app's own
    // text selection just became non-empty (setting "auto_pad_select"). Sets Select ON so the pad opens already
    // matching the selection that is on screen. MgbIme also pre-sets its own inPanel/shownPanel="pad" before that
    // showPanel() call so build() is called with keepSelect = true and does not reset this back to OFF.
    fun forceSelectOn() { state.setSelect(true) }

    /** v0.48.0: MgbIme calls this when a NEW field starts (onStartInputView, restarting = false): Select is OFF again, so a pad opened later never starts with a stale Select ON. */
    fun reset() { try { state.setSelect(false) } catch (_: Throwable) { } }

    /** Returns null on any problem -> keyboard shows normal letters. */
    /** v0.17.35 (M2): keepSelect = the pad is rebuilt while it is already open (Select stays as the user left it). A fresh open starts with Select OFF as before. */
    fun build(keepSelect: Boolean = false): View? = try { make(keepSelect) } catch (_: Throwable) { null }

    private fun toggleSelect() {
        val on = state.toggle()
        // like the mock: Select OFF with text selected = drop the selection (cursor to its right edge, what the old "Clear" key did)
        if (!on && selectedChars() > 0) io.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
        paintSelect()
    }

    private fun redText(b: Button): Button {
        try { b.setTextColor(JabraTokens.WARN) } catch (_: Throwable) { }
        return b
    }

    private fun dimText(b: Button): Button {
        try { b.setTextColor(JabraTokens.dimText(prefs)) } catch (_: Throwable) { }
        return b
    }

    private fun make(keepSelect: Boolean): View {
        if (!keepSelect) state.setSelect(false)   // Select starts OFF when the pad opens (v0.17.35: not when it is only rebuilt)
        tracked.clear()
        statusView = null; padView = null; padTitle = null; padHint = null; padBg = null
        flashUntil = 0L
        extras = try { prefs.bool("pad_extra", true) } catch (_: Throwable) { true }   // v0.45.4: pref pad_extra (read when the pad is built)

        val kh = try { host.keyHeightDp() } catch (_: Throwable) { 48 }
        val barH = (kh * 66 / 100).coerceAtLeast(30)
        val tpH = (kh * 105 / 100).coerceAtLeast(44)
        val dpH = (kh * 80 / 100).coerceAtLeast(34)        // D-pad row
        val botH = (kh * 72 / 100).coerceAtLeast(32)       // edit rows
        val sideRows = if (extras) 4 else 2
        val sideH = (3 * (dpH + 4) / sideRows - 4).coerceAtLeast(26)   // jump keys: same total height as the 3 D-pad rows

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(4), dp(3), dp(4), dp(3))

        // ---- 1. top bar: ABC | Cursor | speed pill | Select pill
        val bar = row()
        bar.gravity = Gravity.CENTER_VERTICAL
        val abc = pk("ABC", 0f, 13f, barH) { state.setSelect(false); host.showLetters() }   // v0.17.35 (M2): ABC is the only way out; Select ends with it
        abc.setPadding(dp(12), 0, dp(12), 0)
        bar.addView(abc)
        val ttl = TextView(ctx)
        ttl.text = "Cursor"
        ttl.textSize = 15f
        ttl.setTypeface(ttl.typeface, Typeface.BOLD)
        ttl.setTextColor(JabraTokens.panelText(prefs))
        ttl.gravity = Gravity.CENTER_VERTICAL
        ttl.setPadding(dp(6), 0, dp(4), 0)
        ttl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        bar.addView(ttl)
        val speedNames = arrayOf("Slow", "Normal", "Fast")
        val speed = pk(speedNames[speedLevel()], 0f, 12f, barH) { b ->
            val n = (speedLevel() + 1) % 3
            prefs.setInt("pad_speed", n)   // read at every drag start, no need to reopen the pad
            b.text = speedNames[n]
            flash("Glide: " + speedNames[n])
        }
        speed.setPadding(dp(12), 0, dp(12), 0)
        try { speed.contentDescription = "Glide speed" } catch (_: Throwable) { }
        bar.addView(speed)
        val selPill = pk("Select", 0f, 12f, barH) { toggleSelect() }
        selPill.setPadding(dp(12), 0, dp(12), 0)
        bar.addView(track(selPill, true))
        root.addView(bar)

        // ---- 2. trackpad
        val tp = LinearLayout(ctx)
        tp.orientation = LinearLayout.VERTICAL
        tp.gravity = Gravity.CENTER
        val tpBg = GradientDrawable()
        tpBg.cornerRadius = dp(14).toFloat()
        tpBg.setColor(try { JabraTokens.cardFill(prefs, false) } catch (_: Throwable) { Color.parseColor("#F7F7F7") })
        padBg = tpBg
        tp.background = tpBg
        val tlp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(tpH))
        tlp.setMargins(dp(2), dp(4), dp(2), dp(4))
        tp.layoutParams = tlp
        val tt = TextView(ctx)
        tt.textSize = 13f
        tt.setTypeface(tt.typeface, Typeface.BOLD)
        tt.setTextColor(JabraTokens.cardText(prefs))
        tt.gravity = Gravity.CENTER
        padTitle = tt
        tp.addView(tt)
        val th = TextView(ctx)
        th.text = "2 taps = word  \u00b7  3 taps = line"
        th.textSize = 11f
        th.setTextColor(JabraTokens.cardNote(prefs))
        th.gravity = Gravity.CENTER
        padHint = th
        tp.addView(th)
        try { tp.contentDescription = "Trackpad: drag to move the cursor" } catch (_: Throwable) { }
        padView = tp
        try { attachDrag(tp) } catch (_: Throwable) { }
        root.addView(tp)

        // ---- 3. keys: D-pad (left) + jump keys (right)
        val mid = LinearLayout(ctx)
        mid.orientation = LinearLayout.HORIZONTAL
        val dpad = col()
        val d1 = row()
        d1.addView(gap(dpH))
        d1.addView(track(held("\u2191", 20f, dpH) { move(KeyEvent.KEYCODE_DPAD_UP) }))
        d1.addView(gap(dpH))
        val d2 = row()
        d2.addView(track(held("\u2190", 20f, dpH) { move(KeyEvent.KEYCODE_DPAD_LEFT) }))
        val ctr = pk("Sel", 1f, 11f, dpH) { toggleSelect() }   // round Select key in the middle (same job as the Select pill)
        try {
            val fill = JabraTokens.btnFill(prefs) ?: Color.parseColor("#F7F7F7")
            val up = GradientDrawable(); up.shape = GradientDrawable.OVAL; up.setColor(fill)
            val down = GradientDrawable(); down.shape = GradientDrawable.OVAL; down.setColor(LookColors.darken(fill, 0.18f))
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            ctr.background = sl
            ctr.backgroundTintList = null
            ctr.stateListAnimator = null
        } catch (_: Throwable) { }
        try { ctr.contentDescription = "Select on / off" } catch (_: Throwable) { }
        d2.addView(track(ctr, true))
        d2.addView(track(held("\u2192", 20f, dpH) { move(KeyEvent.KEYCODE_DPAD_RIGHT) }))
        val d3 = row()
        d3.addView(gap(dpH))
        d3.addView(track(held("\u2193", 20f, dpH) { move(KeyEvent.KEYCODE_DPAD_DOWN) }))
        d3.addView(gap(dpH))
        dpad.addView(d1); dpad.addView(d2); dpad.addView(d3)
        mid.addView(dpad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 36f))

        val side = col()
        side.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 64f)
        side.setPadding(dp(4), 0, 0, 0)
        if (extras) {
            val s1 = row()
            val wl = held("\u25C2 Word", 12f, sideH) { io.word(true, state.isSelect()) }
            try { wl.contentDescription = "Previous word" } catch (_: Throwable) { }
            val wr = held("Word \u25B8", 12f, sideH) { io.word(false, state.isSelect()) }
            try { wr.contentDescription = "Next word" } catch (_: Throwable) { }
            s1.addView(track(wl)); s1.addView(track(wr)); side.addView(s1)
        }
        val s2 = row()
        s2.addView(track(held("Home", 12f, sideH) { move(KeyEvent.KEYCODE_MOVE_HOME) }))
        s2.addView(track(held("End", 12f, sideH) { move(KeyEvent.KEYCODE_MOVE_END) }))
        side.addView(s2)
        if (extras) {
            val s3 = row()
            val pu = held("PgUp", 12f, sideH) { move(KeyEvent.KEYCODE_PAGE_UP) }   // Select ON = Shift + Page Up (selects a page)
            try { pu.contentDescription = "Page up" } catch (_: Throwable) { }
            val pd = held("PgDn", 12f, sideH) { move(KeyEvent.KEYCODE_PAGE_DOWN) }
            try { pd.contentDescription = "Page down" } catch (_: Throwable) { }
            s3.addView(track(pu)); s3.addView(track(pd)); side.addView(s3)
        }
        val s4 = row()
        s4.addView(track(pk("Top", 1f, 12f, sideH) { io.jump(true, state.isSelect()) }))   // v0.17.36 (M3): cursor to the very start (Select ON: select up to it)
        s4.addView(track(pk("Bottom", 1f, 12f, sideH) { io.jump(false, state.isSelect()) }))   // v0.17.36 (M3): cursor to the very end
        side.addView(s4)
        mid.addView(side)
        root.addView(mid)

        // ---- 4. edit rows
        val e1 = row()
        e1.addView(pk("All", 1f, 12f, botH) { io.edit("all") })
        if (extras) {
            val ln = pk("Line", 1f, 12f, botH) { selectLineAtCursor() }   // Home, then Shift+End; Select switches ON (as a triple tap does)
            try { ln.contentDescription = "Select this line" } catch (_: Throwable) { }
            e1.addView(ln)
        }
        e1.addView(pk("Copy", 1f, 12f, botH) { if (selectedChars() == 0) flash("Nothing selected") else { io.edit("copy"); flash("Copied") } })
        e1.addView(pk("Cut", 1f, 12f, botH) { if (selectedChars() == 0) flash("Nothing selected") else { io.edit("cut"); flash("Cut") } })
        e1.addView(pk("Paste", 1f, 12f, botH) { io.edit("paste") })
        root.addView(e1)

        val e2 = row()
        if (extras) {
            e2.addView(dimText(pk("Undo", 1f, 12f, botH) { io.sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON) }))
            e2.addView(dimText(pk("Redo", 1f, 12f, botH) { io.sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON) }))
        }
        val bs = pk("\u232B", 1f, 14f, botH) { }
        try { JabraIcons.putThemed(ctx, bs, "back") } catch (_: Throwable) { }
        try { RepeatKeyHelper({ host.backspace() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(bs) } catch (_: Throwable) { }
        e2.addView(redText(bs))
        if (extras) {
            val bw = pk("\u232B Word", 1f, 12f, botH) { }   // one word per tap, held = word after word
            try { RepeatKeyHelper({ io.delWord() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(bw) } catch (_: Throwable) { }
            try { bw.contentDescription = "Delete previous word" } catch (_: Throwable) { }
            e2.addView(redText(bw))
        }
        val fd = pk("\u2326", 1f, 14f, botH) { }
        try { RepeatKeyHelper({ io.delFwd() }, { v -> try { host.feedback(v) } catch (_: Throwable) { } }).attach(fd) } catch (_: Throwable) { }
        try { fd.contentDescription = "Delete next character" } catch (_: Throwable) { }
        e2.addView(redText(fd))
        root.addView(e2)

        // ---- status line
        val st = note("")
        st.gravity = Gravity.CENTER
        st.setSingleLine(true)
        st.ellipsize = android.text.TextUtils.TruncateAt.END
        st.minHeight = dp(18)
        statusView = st
        root.addView(st)
        paintSelect()
        return root
    }

    private var extras = true   // v0.45.4: pref pad_extra (read when the pad is built)

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
}
