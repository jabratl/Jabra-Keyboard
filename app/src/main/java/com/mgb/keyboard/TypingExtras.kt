package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.PanelLayout   // v0.16.11 (X11 part 2)

/*
 * v0.0.9 typing basics. All new code lives here + typing/ShiftState.java + typing/EnterAction.java (pure Java, tested).
 * MgbIme.kt / JabraExtras.kt only have tiny hooks marked "v0.0.9"; every hook is try/catch (on a problem the old behaviour stays).
 * Prefs file "jabra", NEW keys only: bs_repeat(bool,true) autocap(bool,true) enter_action(bool,true)
 *  - RepeatKeyHelper : hold a key = repeat (used for Backspace)
 *  - MorePanel       : "More settings" page (opened from the gear panel)
 */

/** Hold a key: fires once at once, then after a short delay again and again (faster after a while). Releasing stops it. */
class RepeatKeyHelper(
    private val onFire: () -> Unit,
    private val onFeedback: (View) -> Unit,
    private val onFast: (() -> Unit)? = null   // v0.3.2: after a long hold this replaces onFire (word delete); null = old behaviour
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var count = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val fast = onFast
            if (fast != null && count >= com.mgb.keyboard.typing.WordDelete.FAST_AFTER) {   // v0.3.2: long hold = whole words
                try { fast() } catch (_: Throwable) { }
                count++
                handler.postDelayed(this, 120L)
                return
            }
            try { onFire() } catch (_: Throwable) { }
            count++
            handler.postDelayed(this, if (count > 12) 35L else 70L)
        }
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    fun attach(v: View) {
        // keys can disappear while pressed (panel switch): never leave a repeat running
        v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { }
            override fun onViewDetachedFromWindow(view: View) { stop() }
        })
        v.setOnTouchListener { view, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        try { onFeedback(view) } catch (_: Throwable) { }
                        try { onFire() } catch (_: Throwable) { }
                        stop()
                        running = true
                        count = 0
                        handler.postDelayed(tick, 400L)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        stop()
                        true
                    }
                    else -> true
                }
            } catch (_: Throwable) {
                stop()
                false
            }
        }
    }
}

/** "More settings" page (gear panel -> More settings). Built in code like the other panels. */
class MorePanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.15 (D2.5): pk() = UiKit.btn (key-height tall, 12sp, same 2dp margins, theme style, feedback before click). Old body kept as pkOld() = fallback.
    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, host.keyHeightDp(), 12f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, onClick)
    }

    private fun pkOld(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
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

    // v0.10.15 (D2.5): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun onOff(b: Boolean): String = if (b) "ON" else "OFF"

    private fun toggleKey(title: String, key: String, def: Boolean): Button {
        val b = pk("", 1f) { btn ->
            val nv = !prefs.bool(key, def)
            prefs.setBool(key, nv)
            btn.text = title + ": " + onOff(nv)
        }
        b.text = title + ": " + onOff(prefs.bool(key, def))
        return b
    }

    // v0.10.15 (D2.5): note() = UiKit.noteText (11sp, note colour, 6 / 2dp padding). Old body kept as noteOld() = fallback.
    private fun note(text: String): TextView {
        try { return UiKit.noteText(ctx, prefs, text) } catch (_: Throwable) { }
        return noteOld(text)
    }

    private fun noteOld(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })   // v0.1.0: readable on Dark
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    // v0.16.11 (X11 part 2): "More settings" as tiles (panel_ui = 1). Same prefs, same panels as the classic page: nothing removed.
    // A toggle / cycle tile changes the pref, then reopens this page (showPanel("more")) so the tile shows the new value.
    private fun makeNew(): View? = try {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        val cols = PanelLayout.columns((ctx.resources.displayMetrics.widthPixels / ctx.resources.displayMetrics.density).toInt())
        val content = LinearLayout(ctx)   // v0.16.11 (X11 part 2b): tiles live in [content]; the search row hides it while typing
        content.orientation = LinearLayout.VERTICAL
        fun again() { host.showPanel("more") }
        fun tog(title: String, key: String, def: Boolean): PanelKit.Tile {
            val on = prefs.bool(key, def)
            return PanelKit.Tile(title, if (on) "ON" else "OFF", on) { prefs.setBool(key, !prefs.bool(key, def)); again() }
        }
        fun go(label: String, route: String): PanelKit.Tile = PanelKit.Tile(label) { host.showPanel(route) }

        content.addView(PanelKit.sectionLabel(ctx, prefs, "Typing"))
        content.addView(PanelKit.tileGrid(ctx, prefs, host, cols, listOf(
            tog("Hold backspace", "bs_repeat", true), tog("Auto caps", "autocap", true),
            tog("Enter action", "enter_action", true), tog("Word delete", "bs_word", true))))

        val look = ArrayList<PanelKit.Tile>()
        try {
            look.add(PanelKit.Tile("Theme", JabraThemes.cur(prefs).name) { prefs.setInt("theme", (JabraThemes.index(prefs) + 1) % JabraThemes.ALL.size); again() })
            val ohNames = arrayOf("Off", "Left", "Right")
            look.add(PanelKit.Tile("One-hand", ohNames[JabraOneHand.mode(prefs)]) { prefs.setInt("onehand", (JabraOneHand.mode(prefs) + 1) % 3); again() })
            look.add(PanelKit.Tile("Look", if (prefs.int("look", 0) == 1) "Jabra" else "Classic") { prefs.setInt("look", if (prefs.int("look", 0) == 1) 0 else 1); again() })
            look.add(PanelKit.Tile("Key border", if (prefs.int("key_border", 0) == 1) "Borderless" else "Border") { prefs.setInt("key_border", if (prefs.int("key_border", 0) == 1) 0 else 1); again() })
        } catch (_: Throwable) { }
        if (look.isNotEmpty()) {
            content.addView(PanelKit.sectionLabel(ctx, prefs, "Look"))
            content.addView(PanelKit.tileGrid(ctx, prefs, host, cols, look))
        }

        content.addView(PanelKit.sectionLabel(ctx, prefs, "PC mode"))
        content.addView(PanelKit.tileGrid(ctx, prefs, host, cols, listOf(
            PanelKit.Tile("Mode", if (prefs.bool("pc_mode", false)) "PC" else "Phone") { prefs.setBool("pc_mode", !prefs.bool("pc_mode", false)); again() },
            tog("Mode key", "mode_key", true), tog("Remember mode", "pc_remember", true))))

        content.addView(PanelKit.sectionLabel(ctx, prefs, "Open"))
        content.addView(PanelKit.tileGrid(ctx, prefs, host, cols, listOf(
            go("Typing options", "typing"), go("Size", "size"), go("Edit toolbar", "toolbar"),
            go("Cursor / select pad", "pad"), go("Theme picker", "themes"), go("Emoji panel", "emoji"),
            PanelKit.Tile("Switch keyboard") { host.pickKeyboard() })))

        content.addView(note("PC mode adds Esc Tab Ctrl Alt F1-F12 arrows Home End; works only in apps that read key events. Theme / One-hand: tap, then ABC (apply)."))
        PanelSearchUi.install(ctx, prefs, host, root, content)   // v0.16.11 (X11 part 2b)
        root.addView(PanelKit.stickyBar(ctx, prefs, host, listOf(Pair("< Settings", { host.showPanel("settings") }), Pair("ABC (apply)", { host.showLetters() }))))
        root
    } catch (_: Throwable) { null }

    private fun make(): View {
        try { if (PanelLayout.isNewStyle(prefs.int("panel_ui", 0))) { val nv = makeNew(); if (nv != null) return nv } } catch (_: Throwable) { }   // v0.16.11 (X11 part 2): any problem / default 0 = the classic page below, untouched
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.1.0   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val r1 = row()
        r1.addView(toggleKey("Hold backspace", "bs_repeat", true))
        r1.addView(toggleKey("Auto caps", "autocap", true))
        root.addView(r1)

        val r2 = row()
        r2.addView(toggleKey("Enter action", "enter_action", true))
        r2.addView(pk("Switch keyboard", 1f) { host.pickKeyboard() })
        root.addView(r2)

        // v0.1.0: theme, one-handed mode, emoji panel
        try {
            val r3 = row()
            val themeBtn = pk("", 1f) { btn ->
                val nv = (JabraThemes.index(prefs) + 1) % JabraThemes.ALL.size
                prefs.setInt("theme", nv)
                btn.text = "Theme: " + JabraThemes.ALL[nv].name
            }
            themeBtn.text = "Theme: " + JabraThemes.cur(prefs).name
            r3.addView(themeBtn)
            val ohNames = arrayOf("Off", "Left", "Right")
            val ohBtn = pk("", 1f) { btn ->
                val nv = (JabraOneHand.mode(prefs) + 1) % 3
                prefs.setInt("onehand", nv)
                btn.text = "One-hand: " + ohNames[nv]
            }
            ohBtn.text = "One-hand: " + ohNames[JabraOneHand.mode(prefs)]
            r3.addView(ohBtn)
            root.addView(r3)
            val r35 = row()
            r35.addView(pk("Emoji panel", 1f) { host.showPanel("emoji") })
            r35.addView(toggleKey("Word delete", "bs_word", true))   // v0.3.2
            root.addView(r35)
        } catch (_: Throwable) { }

        root.addView(note("Enter action: Enter does Send / Search / Go when the app asks for it. Long-press ?123 also opens the keyboard picker. Theme / One-hand: tap, then ABC (apply). Emoji: long-press the , key."))

        try { val rt = row(); rt.addView(pk("Typing options >", 1f) { host.showPanel("typing") }); root.addView(rt) } catch (_: Throwable) { }   // v0.1.1
        try { val rz = row(); rz.addView(pk("Size (height / letters / one-hand width) >", 1f) { host.showPanel("size") }); root.addView(rz) } catch (_: Throwable) { }   // v0.1.4
        try { val rtb = row(); rtb.addView(pk("Edit toolbar >", 1f) { host.showPanel("toolbar") }); root.addView(rtb) } catch (_: Throwable) { }   // v0.2.1
        try { val rpd = row(); rpd.addView(pk("Cursor / select pad >", 1f) { host.showPanel("pad") }); root.addView(rpd) } catch (_: Throwable) { }   // v0.2.3
        try { val rth = row(); rth.addView(pk("Theme picker >", 1f) { host.showPanel("themes") }); root.addView(rth) } catch (_: Throwable) { }   // v0.2.4
        try {   // v0.1.5: Look Classic / Jabra
            val rl = row()
            val lookBtn = pk("", 1f) { btn ->
                val nv = if (prefs.int("look", 0) == 1) 0 else 1
                prefs.setInt("look", nv)
                btn.text = "Look: " + (if (nv == 1) "Jabra" else "Classic")
            }
            lookBtn.text = "Look: " + (if (prefs.int("look", 0) == 1) "Jabra" else "Classic")
            rl.addView(lookBtn)
            root.addView(rl)
        } catch (_: Throwable) { }
        try {   // v0.2.6: Key border Border / Borderless
            val rkb = row()
            val kbBtn = pk("", 1f) { btn ->
                val nv = if (prefs.int("key_border", 0) == 1) 0 else 1
                prefs.setInt("key_border", nv)
                btn.text = "Key border: " + (if (nv == 1) "Borderless" else "Border")
            }
            kbBtn.text = "Key border: " + (if (prefs.int("key_border", 0) == 1) "Borderless" else "Border")
            rkb.addView(kbBtn)
            root.addView(rkb)
        } catch (_: Throwable) { }
        // v0.1.2: PC mode rows (mode key = first key of the edit bar)
        try {
            val rm = row()
            val modeBtn = pk("", 1f) { btn ->
                val nv = !prefs.bool("pc_mode", false)
                prefs.setBool("pc_mode", nv)
                btn.text = "Mode: " + (if (nv) "PC" else "Phone")
            }
            modeBtn.text = "Mode: " + (if (prefs.bool("pc_mode", false)) "PC" else "Phone")
            rm.addView(modeBtn)
            rm.addView(toggleKey("Mode key", "mode_key", true))
            rm.addView(toggleKey("Remember mode", "pc_remember", true))
            root.addView(rm)
            root.addView(note("PC mode adds Esc Tab Ctrl Alt F1-F12 arrows Home End. Ctrl/Alt: 1 tap = next key, 2 taps = lock. Works only in apps that read key events (Termux, browsers, code editors); many plain text fields ignore them. Then ABC (apply)."))
        } catch (_: Throwable) { }

        val rb = row()
        rb.addView(pk("< Settings", 1f) { host.showPanel("settings") })
        rb.addView(pk("ABC  (apply)", 1f) { host.showLetters() })
        root.addView(rb)
        return root
    }
}
