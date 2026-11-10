package com.mgb.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.extras.SpaceDrag

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
    private val onFeedback: (View) -> Unit
) {
    private val drag = SpaceDrag(16f * density)

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

    private fun sym(s: String) {
        host.commitWord()
        host.typeText(s)
    }

    private fun bottomRow(withNumpadKey: Boolean): LinearLayout {
        val r = row()
        r.addView(pk("ABC", 1.5f) { host.showLetters() })
        r.addView(JabraIcons.put(ctx, pk("⚙", 1f) { host.showPanel("settings") }, "gear"))   // v0.0.7
        if (withNumpadKey) r.addView(pk("123", 1f) { host.showPanel("numpad") })
        r.addView(pk(",", 1f) { sym(",") })
        r.addView(pk("space", 3f) { sym(" ") })
        r.addView(pk(".", 1f) { sym(".") })
        r.addView(JabraIcons.put(ctx, pk("⏎", 1.4f) { host.commitWord(); host.enter() }, "enter"))   // v0.0.7
        return r
    }

    private fun symbols(): View {
        val root = rootView()
        val pages = if (symPage == 0)
            arrayOf("1234567890", "@#৳%&-+()/", "*\"':;!?")
        else
            arrayOf("~`|•°^=_{}", "\\[]<>£€¥₹\$", "…©®™§")

        for (i in 0..1) {
            val r = row()
            pages[i].forEach { ch -> r.addView(pk(ch.toString(), 1f) { sym(ch.toString()) }) }
            root.addView(r)
        }
        val r3 = row()
        r3.addView(pk(if (symPage == 0) "=<" else "?123", 1.5f) {
            symPage = 1 - symPage
            host.showPanel("symbols")
        })
        pages[2].forEach { ch -> r3.addView(pk(ch.toString(), 1f) { sym(ch.toString()) }) }
        r3.addView(JabraIcons.put(ctx, pk("⌫", 1.5f) { host.backspace() }, "back"))   // v0.0.7
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
            grid[i].forEach { s -> r.addView(pk(s, 1f) { sym(s) }) }
            if (i == 0) r.addView(JabraIcons.put(ctx, pk(side[i], 1f) { host.backspace() }, "back"))   // v0.0.7
            else r.addView(pk(side[i], 1f) { sym(side[i]) })
            root.addView(r)
        }
        val r4 = row()
        r4.addView(pk("ABC", 1f) { host.showLetters() })
        r4.addView(pk("+", 1f) { sym("+") })
        r4.addView(pk("0", 1f) { sym("0") })
        r4.addView(JabraIcons.put(ctx, pk("⏎", 1f) { host.commitWord(); host.enter() }, "enter"))   // v0.0.7
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
            btn.text = "Bangla: " + (if (nv == "probhat") "Probhat" else "Avro")
        }
        b.text = "Bangla: " + (if (prefs.str("bn_layout", "avro") == "probhat") "Probhat" else "Avro")
        b.textSize = 12f
        return b
    }

    private fun settings(): View {
        val root = rootView()
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
        return root
    }
}
