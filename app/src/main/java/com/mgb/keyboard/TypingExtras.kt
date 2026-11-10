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
    private val onFeedback: (View) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var count = 0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
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

    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
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

    private fun row(): LinearLayout {
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

    private fun note(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })   // v0.1.0: readable on Dark
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { root.setBackgroundColor(JabraThemes.bg(prefs)) } catch (_: Throwable) { }   // v0.1.0
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
            root.addView(r35)
        } catch (_: Throwable) { }

        root.addView(note("Enter action: Enter does Send / Search / Go when the app asks for it. Long-press ?123 also opens the keyboard picker. Theme / One-hand: tap, then ABC (apply). Emoji: long-press the , key."))

        try { val rt = row(); rt.addView(pk("Typing options >", 1f) { host.showPanel("typing") }); root.addView(rt) } catch (_: Throwable) { }   // v0.1.1
        try { val rz = row(); rz.addView(pk("Size (height / one-hand width) >", 1f) { host.showPanel("size") }); root.addView(rz) } catch (_: Throwable) { }   // v0.1.4
        try { val rtb = row(); rtb.addView(pk("Edit toolbar >", 1f) { host.showPanel("toolbar") }); root.addView(rtb) } catch (_: Throwable) { }   // v0.2.1
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
