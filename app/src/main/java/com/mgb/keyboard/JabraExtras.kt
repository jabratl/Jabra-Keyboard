package com.mgb.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.extras.SpaceDrag
import com.mgb.keyboard.extras.SpaceGesture
import com.mgb.keyboard.typing.LangSwipe
import com.mgb.keyboard.typing.PanelLayout   // v0.16.11 (X11)
import com.mgb.keyboard.typing.PanelTiles   // v0.16.11 (X11)

/*
 * v0.0.5 extras (symbols panel, number pad, in-keyboard quick settings, space-drag cursor, prefs helper).
 * All new code lives here; MgbIme.kt only has tiny hooks. Every hook is try/catch, so if anything here fails
 * the keyboard keeps working like v0.0.3 / v0.0.4.
 * Prefs file "jabra" (same file as before). New keys (old keys untouched):
 *   numrow(bool,true) haptic(bool,true) sound(bool,false) numpad_auto(bool,true)
 *   kh_p / kh_l (int 0..3, default 1 = 48dp, portrait / landscape)   bn_layout("avro"|"probhat")
 */

/** What the panels need from the keyboard. Implemented inside MgbIme. */
interface JabraHost {
    fun typeText(s: String)
    fun commitWord()
    fun backspace()
    fun enter()
    fun showLetters()
    fun showPanel(name: String)      // "symbols" | "numpad" | "settings"
    fun feedback(v: View)            // haptic / sound on key press
    fun keyHeightDp(): Int
    fun layoutChanged()              // bn_layout pref changed in the settings panel
    fun pickKeyboard() { }           // v0.0.9: open the phone's keyboard picker (default: does nothing)
    fun fieldText(): String = ""     // v0.5.0: selected text or whole field text ("" in password fields / default)
    fun currentApp(): String = ""    // v0.10.18 (W6.1): package name of the app that owns the open field ("" = none / unknown)
}

/** Safe pref reader/writer: every read has a default, nothing ever throws. */
class JabraPrefs(private val ctx: Context) {
    private fun sp() = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    fun bool(k: String, d: Boolean): Boolean = try { sp().getBoolean(k, d) } catch (_: Throwable) { d }
    fun int(k: String, d: Int): Int = try { sp().getInt(k, d) } catch (_: Throwable) { d }
    fun str(k: String, d: String): String = try { sp().getString(k, d) ?: d } catch (_: Throwable) { d }
    fun setBool(k: String, v: Boolean) { try { sp().edit().putBoolean(k, v).apply() } catch (_: Throwable) { } }
    fun setInt(k: String, v: Int) { try { sp().edit().putInt(k, v).apply() } catch (_: Throwable) { } }
    fun setStr(k: String, v: String) { try { sp().edit().putString(k, v).apply() } catch (_: Throwable) { } }

    fun heightKey(land: Boolean): String = if (land) "kh_l" else "kh_p"

    /** Key height in dp. Level 1 (default) = 48dp = exactly the old size. */
    fun keyHeightDp(land: Boolean): Int {
        try { val pct = int(SizeKeys.heightKey(land), -1); if (com.mgb.keyboard.typing.SizePct.heightValid(pct)) return com.mgb.keyboard.typing.SizePct.heightDp(pct) } catch (_: Throwable) { }   // v0.1.4: height % (unset = old levels below)
        val lv = int(heightKey(land), 1)
        val i = if (lv < 0) 0 else if (lv > 3) 3 else lv
        return intArrayOf(40, 48, 56, 64)[i]
    }

    companion object {
        fun isLand(ctx: Context): Boolean =
            try { ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE } catch (_: Throwable) { false }
    }
}

/** Drag on the space key = move cursor; plain tap = space. Logic is in extras/SpaceDrag.java (tested). */
class SpaceDragHelper(
    density: Float,
    private val onStep: (Int) -> Unit,
    private val onTap: () -> Unit,
    private val onFeedback: (View) -> Unit,
    stepDp: Float = 16f   // v0.1.1: setting "cursor_speed" (16 = old value)
) {
    private val drag = SpaceDrag(stepDp * density)
    private val dens = density
    private val cursorPx = stepDp * density

    fun attach(v: View) {
        v.setOnTouchListener { view, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        drag.down(ev.x)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val n = drag.move(ev.x)
                        if (n != 0) onStep(n)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        if (!drag.wasDragged()) {
                            onFeedback(view)
                            onTap()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        true
                    }
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    /**
     * v0.8.9: like attach(), plus: v0.9.3: swipe at once (no hold) = [onLang] steps (+ = next language) instead of cursor steps.
     * A quick drag is still the cursor. Armed but not swiped = a normal space. Logic = extras/SpaceGesture.java (tested). Used only when setting space_lang is ON.
     */
    fun attachLang(v: View, onLang: (Int) -> Unit, onArm: (View) -> Unit, onShow: (View) -> Unit = { }, onHide: () -> Unit = { }, onEnd: (Boolean) -> Unit = { }, langStepDp: () -> Float = { LangSwipe.STEP_DP }) {   // v0.9.2: onShow = live language bubble, onHide = remove it; v0.9.8: onEnd(cancelled) = finger up (save once) / cancel (restore), langStepDp = swipe distance read at each touch
        var g = SpaceGesture(cursorPx, LangSwipe.STEP_DP * dens)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        val arm = Runnable { try { if (g.arm()) { v.isPressed = true; onArm(v); try { onShow(v) } catch (_: Throwable) { } } } catch (_: Throwable) { } }   // v0.9.2
        v.setOnTouchListener { view, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        try { g = SpaceGesture(cursorPx, langStepDp() * dens) } catch (_: Throwable) { }   // v0.9.8: sp_step read at touch time (no rebuild needed)
                        g.down(ev.x)
                        h.removeCallbacks(arm)
                        if (LangSwipe.ARM_MS <= 0L) g.arm() else h.postDelayed(arm, LangSwipe.ARM_MS)   // v0.9.3: armed at once = no 0.2 s hold (bubble shows from the first language step)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val n = g.move(ev.x)
                        if (n != 0) { if (g.kind() == SpaceGesture.LANG) { onLang(n); try { onShow(view) } catch (_: Throwable) { } } else onStep(n) }   // v0.9.2: bubble follows each language step
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(arm)
                        try { onHide() } catch (_: Throwable) { }   // v0.9.2
                        try { onEnd(false) } catch (_: Throwable) { }   // v0.9.8: save the language ONCE
                        view.isPressed = false
                        if (g.up()) { onFeedback(view); onTap() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        h.removeCallbacks(arm)
                        try { onHide() } catch (_: Throwable) { }   // v0.9.2
                        try { onEnd(true) } catch (_: Throwable) { }   // v0.9.8: cancelled swipe = back to the start language
                        view.isPressed = false
                        true
                    }
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
        }
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(x: View) { }
            override fun onViewDetachedFromWindow(x: View) { h.removeCallbacks(arm); try { onHide() } catch (_: Throwable) { }; try { onEnd(true) } catch (_: Throwable) { } }   // v0.9.2; v0.9.8
        })
    }
}

/** Symbols panel, number pad, quick settings. Built in code (no XML), like the main keyboard. */
class JabraPanels(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var symPage = 0

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }   // v0.1.0
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(host.keyHeightDp()), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun rootView(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.VERTICAL
        r.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, r) } catch (_: Throwable) { }   // v0.1.0   // v0.2.6
        r.setPadding(dp(2), dp(4), dp(2), dp(4))
        return r
    }

    /** Returns null if anything fails; the keyboard then simply shows the normal letters again. */
    fun build(name: String): View? = try {
        when (name) {
            "numpad" -> numpad()
            "settings" -> settings()
            else -> symbols()
        }
    } catch (_: Throwable) {
        null
    }

    /** v0.0.9: hold = repeat (setting "bs_repeat"). On any problem the plain click stays. */
    private fun repeatable(b: Button): Button {
        try { if (prefs.bool("bs_repeat", true)) RepeatKeyHelper({ host.backspace() }, { v -> host.feedback(v) }).attach(b) } catch (_: Throwable) { }
        return b
    }

    private fun sym(s: String) {
        host.commitWord()
        host.typeText(s)
    }

    private fun bottomRow(withNumpadKey: Boolean): LinearLayout {
        val r = row()
        r.addView(pk("ABC", 1.5f) { host.showLetters() })
        r.addView(JabraIcons.putThemed(ctx, pk("⚙", 1f) { host.showPanel("settings") }, "gear"))   // v0.0.7
        if (withNumpadKey) r.addView(pk("123", 1f) { host.showPanel("numpad") })
        r.addView(pk(",", 1f) { sym(",") })
        r.addView(pk("space", 3f) { sym(" ") })
        r.addView(pk(".", 1f) { sym(".") })
        r.addView(JabraIcons.putThemed(ctx, pk("⏎", 1.4f) { host.commitWord(); host.enter() }, "enter"))   // v0.0.7
        return r
    }

    /** v0.3.4: character / digit keys of symbols + number pad follow Letter size % and Bold letters (unset = old look). */
    private fun cs(b: Button): Button {
        try { val lp = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(lp)) b.textSize = com.mgb.keyboard.typing.SizePct.letterSp(lp) } catch (_: Throwable) { }
        try { if (prefs.bool("letter_bold", false)) b.setTypeface(b.typeface, android.graphics.Typeface.BOLD) } catch (_: Throwable) { }
        return b
    }

    private fun symbols(): View {
        val root = rootView()
        val pages: Array<String> = try { com.mgb.keyboard.typing.SymbolPages.page(symPage) } catch (_: Throwable) {   // v0.9.5: 3 pages from SymbolPages; old 2 pages = fallback
            if (symPage == 0)
                arrayOf("1234567890", "@#৳%&-+()/", "*\"':;!?")
            else
                arrayOf("~`|•°^=_{}", "\\[]<>£€¥₹\$", "…©®™§")
        }

        for (i in 0..1) {
            val r = row()
            pages[i].forEach { ch -> r.addView(cs(pk(ch.toString(), 1f) { sym(ch.toString()) })) }
            root.addView(r)
        }
        val r3 = row()
        r3.addView(pk(try { com.mgb.keyboard.typing.SymbolPages.switchLabel(symPage) } catch (_: Throwable) { if (symPage == 0) "=<" else "?123" }, 1.5f) {   // v0.9.5: label = next page
            symPage = try { com.mgb.keyboard.typing.SymbolPages.next(symPage) } catch (_: Throwable) { 1 - (symPage and 1) }   // v0.9.5: 0 -> 1 -> 2 -> 0
            host.showPanel("symbols")
        })
        pages[2].forEach { ch -> r3.addView(cs(pk(ch.toString(), 1f) { sym(ch.toString()) })) }
        r3.addView(repeatable(JabraIcons.putThemed(ctx, pk("⌫", 1.5f) { host.backspace() }, "back")))   // v0.0.7 + v0.0.9 hold-repeat
        root.addView(r3)
        root.addView(bottomRow(true))
        return root
    }

    private fun numpad(): View {
        val root = rootView()
        val grid = arrayOf(
            arrayOf("1", "2", "3"),
            arrayOf("4", "5", "6"),
            arrayOf("7", "8", "9")
        )
        val side = arrayOf("⌫", "-", ".")
        for (i in 0..2) {
            val r = row()
            grid[i].forEach { s -> r.addView(cs(pk(s, 1f) { sym(s) })) }
            if (i == 0) r.addView(repeatable(JabraIcons.putThemed(ctx, pk(side[i], 1f) { host.backspace() }, "back")))   // v0.0.7 + v0.0.9 hold-repeat
            else r.addView(cs(pk(side[i], 1f) { sym(side[i]) }))
            root.addView(r)
        }
        val r4 = row()
        r4.addView(pk("ABC", 1f) { host.showLetters() })
        r4.addView(cs(pk("+", 1f) { sym("+") }))
        r4.addView(cs(pk("0", 1f) { sym("0") }))
        r4.addView(JabraIcons.putThemed(ctx, pk("⏎", 1f) { host.commitWord(); host.enter() }, "enter"))   // v0.0.7
        root.addView(r4)
        return root
    }

    private fun onOff(b: Boolean): String = if (b) "ON" else "OFF"

    private fun toggleKey(title: String, key: String, def: Boolean): Button {
        val b = pk("", 1f) { btn ->
            val nv = !prefs.bool(key, def)
            prefs.setBool(key, nv)
            btn.text = title + ": " + onOff(nv)
        }
        b.text = title + ": " + onOff(prefs.bool(key, def))
        b.textSize = 12f
        return b
    }

    private fun heightKey(): Button {
        val names = arrayOf("Small", "Medium", "Large", "XL")
        val land = JabraPrefs.isLand(ctx)
        val k = prefs.heightKey(land)
        val tail = if (land) " (landscape)" else ""
        val cur = prefs.int(k, 1).coerceIn(0, 3)
        val b = pk("", 1f) { btn ->
            val nv = (prefs.int(k, 1).coerceIn(0, 3) + 1) % 4
            prefs.setInt(k, nv)
            try { prefs.setInt(SizeKeys.heightKey(land), -1) } catch (_: Throwable) { }   // v0.1.4: old Height button works again (clears the height %)
            btn.text = "Height: " + names[nv] + tail
        }
        b.text = "Height: " + names[cur] + tail
        b.textSize = 12f
        return b
    }

    private fun layoutKey(): Button {
        val b = pk("", 1f) { btn ->
            val nv = if (prefs.str("bn_layout", "avro") == "probhat") "avro" else "probhat"
            prefs.setStr("bn_layout", nv)
            host.layoutChanged()
            btn.text = "Bangla: " + (try { com.mgb.keyboard.typing.LangNames.name(nv == "probhat") } catch (_: Throwable) { if (nv == "probhat") "Probhat" else "Avro" })   // v0.16.2
        }
        b.text = "Bangla: " + (try { com.mgb.keyboard.typing.LangNames.name(prefs.str("bn_layout", "avro") == "probhat") } catch (_: Throwable) { if (prefs.str("bn_layout", "avro") == "probhat") "Probhat" else "Avro" })   // v0.16.2
        b.textSize = 12f
        return b
    }

    // v0.16.11 (X11): pref panel_ui = 1 -> grouped tile grid (every tile opens the SAME old panel). Any problem / default 0 = the classic gear panel below, untouched.
    private fun settingsNew(): View? = try {
        val root = rootView()
        val cols = PanelLayout.columns((ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density).toInt())
        for (g in PanelTiles.groups()) {
            root.addView(PanelKit.sectionLabel(ctx, prefs, g))
            val tiles = ArrayList<PanelKit.Tile>()
            for (t in PanelTiles.tilesOf(g)) { val route = t.route; tiles.add(PanelKit.Tile(t.label) { host.showPanel(route) }) }
            root.addView(PanelKit.tileGrid(ctx, prefs, host, cols, tiles))
        }
        val q1 = row(); q1.addView(toggleKey("Number row", "numrow", true)); q1.addView(toggleKey("Vibrate", "haptic", true)); root.addView(q1)   // quick toggles stay reachable
        val q2 = row(); q2.addView(toggleKey("Sound", "sound", false)); q2.addView(toggleKey("Auto numpad", "numpad_auto", true)); root.addView(q2)
        root.addView(PanelKit.stickyBar(ctx, prefs, host, listOf(Pair("ABC", { host.showLetters() }), Pair("More settings", { host.showPanel("more") }))))
        root
    } catch (_: Throwable) { null }

    private fun settings(): View {
        try { if (PanelLayout.isNewStyle(prefs.int("panel_ui", 0))) { val nv = settingsNew(); if (nv != null) return nv } } catch (_: Throwable) { }   // v0.16.11 (X11)
        val root = rootView()
        try {   // v0.5.6: small logo + name on top of the gear panel
            val h = LinearLayout(ctx)
            h.orientation = LinearLayout.HORIZONTAL
            h.gravity = android.view.Gravity.CENTER_VERTICAL
            val iv = android.widget.ImageView(ctx)
            iv.setImageResource(R.drawable.jabra_logo)
            iv.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            val tv = android.widget.TextView(ctx)
            tv.text = "Jabra Keyboard " + BuildConfig.VERSION_NAME
            tv.textSize = 13f
            tv.setPadding(dp(8), 0, 0, 0)
            try { tv.setTextColor(JabraThemes.iconColor(prefs)) } catch (_: Throwable) { }
            h.addView(iv); h.addView(tv)
            root.addView(h)
        } catch (_: Throwable) { }
        val r1 = row()
        r1.addView(toggleKey("Number row", "numrow", true))
        r1.addView(toggleKey("Vibrate", "haptic", true))
        root.addView(r1)
        val r2 = row()
        r2.addView(toggleKey("Sound", "sound", false))
        r2.addView(toggleKey("Auto numpad", "numpad_auto", true))
        root.addView(r2)
        val r3 = row()
        r3.addView(heightKey())
        r3.addView(layoutKey())
        root.addView(r3)
        val r35 = row()   // v0.0.6
        r35.addView(toggleKey("Edit bar", "edit_bar", true))
        r35.addView(toggleKey("Clip history", "clip_on", true))
        root.addView(r35)
        val r4 = row()
        r4.addView(toggleKey("Icons", "icons_on", true))   // v0.0.7: OFF = old text/emoji labels
        r4.addView(toggleKey("Formatter", "fmt_on", true))   // v0.0.8: OFF = no "Aa" button in the edit bar
        r4.addView(pk("ABC  (apply)", 1f) { host.showLetters() })
        root.addView(r4)
        val r5 = row()   // v0.0.9: second settings page
        r5.addView(pk("More settings  >", 1f) { host.showPanel("more") })
        try { r5.addView(pk("Key map", 1f) { host.showPanel("keymap") }) } catch (_: Throwable) { }   // v0.16.5 (X5)
        root.addView(r5)
        return root
    }
}
