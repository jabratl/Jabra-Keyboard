package com.mgb.keyboard

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.ChangedPlan

/**
 * v0.12.0 (M2 of the "App UI" plan): widget factories for the new app pages (pref app_ui = 1). Only the LOOK of a row lives here;
 * prefs, undo, reset, search stay in SettingsGroups (the callbacks come from there). Every factory returns a Built = view + refresh().
 * Nothing here throws out: SettingsGroups calls these inside the group's try / catch, and the old Button rows are the fallback.
 */
object AppRows {

    class Built(val view: View, val refresh: () -> Unit)

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun safe(b: () -> Unit) { try { b() } catch (_: Throwable) { } }

    private fun help(c: Context, text: String): TextView? {
        if (text.isEmpty()) return null
        val t = TextView(c)
        t.text = text
        t.textSize = 12f
        t.setTextColor(AppUi.dim(c))
        t.setPadding(0, 0, 0, dp(c, 6))
        return t
    }

    private fun titleView(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = 15f
        t.setTextColor(AppUi.text(c))
        return t
    }

    private fun chipBg(c: Context, selected: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(if (selected) AppUi.accent(c) else com.mgb.keyboard.typing.AppPalette.card(AppUi.isDark(c)))
        g.cornerRadius = dp(c, AppTokens.S3).toFloat()
        return g
    }

    private fun onAccent(c: Context): Int = if (AppUi.isDark(c)) 0xFF121316.toInt() else 0xFFFFFFFF.toInt()

    private fun flat(c: Context, b: Button, selected: Boolean) {
        b.isAllCaps = false
        b.background = chipBg(c, selected)
        b.setTextColor(if (selected) onAccent(c) else AppUi.text(c))
        b.minWidth = 0; b.minimumWidth = 0
        b.minHeight = dp(c, AppTokens.TOUCH); b.minimumHeight = dp(c, AppTokens.TOUCH)
        b.stateListAnimator = null
    }

    /** Real Switch: title on the left (the Switch's own text, so TalkBack reads it), help text under it, 56 dp tall. */
    fun switchRow(c: Context, title: String, help: String, isOn: () -> Boolean, onSet: (Boolean) -> Unit): Built {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val sw = Switch(c)
        sw.text = title
        sw.textSize = 15f
        sw.setTextColor(AppUi.text(c))
        sw.minHeight = dp(c, 56)
        sw.setPadding(0, dp(c, AppTokens.S1), 0, dp(c, AppTokens.S1))
        var quiet = false
        sw.isChecked = isOn()
        sw.setOnCheckedChangeListener { _, v -> if (!quiet) safe { onSet(v) } }
        box.addView(sw, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        help(c, help)?.let { box.addView(it) }
        return Built(box) { quiet = true; try { sw.isChecked = isOn() } catch (_: Throwable) { }; quiet = false }
    }

    /** 2-4 short options as a segmented control (one tap = that option). */
    fun choiceRow(c: Context, title: String, help: String, names: () -> Array<String>, cur: () -> Int, onPick: (Int) -> Unit): Built {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val t = titleView(c, title)
        t.setPadding(0, dp(c, AppTokens.S2), 0, dp(c, AppTokens.S1))
        box.addView(t)
        val seg = LinearLayout(c)
        seg.orientation = LinearLayout.HORIZONTAL
        val btns = ArrayList<Button>()
        val n0 = names()
        for (i in n0.indices) {
            val b = Button(c)
            b.text = n0[i]
            b.textSize = 13f
            b.maxLines = 2
            b.setOnClickListener { safe { onPick(i); paint(c, btns, names, cur) } }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(c, 2), 0, dp(c, 2), 0)
            seg.addView(b, lp)
            btns.add(b)
        }
        box.addView(seg)
        help(c, help)?.let { it.setPadding(0, dp(c, AppTokens.S1), 0, dp(c, 6)); box.addView(it) }
        paint(c, btns, names, cur)
        return Built(box) { paint(c, btns, names, cur) }
    }

    private fun paint(c: Context, btns: List<Button>, names: () -> Array<String>, cur: () -> Int) {
        safe {
            val n = names()
            val sel = cur().coerceIn(0, n.size - 1)
            for (i in btns.indices) {
                flat(c, btns[i], i == sel)
                btns[i].contentDescription = n[i.coerceIn(0, n.size - 1)] + (if (i == sel) ", selected" else "")
            }
        }
    }

    /** Title left, value chip right; tap = next value (many options, or long names). */
    fun chipRow(c: Context, title: String, help: String, value: () -> String, onNext: () -> Unit): Built {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.minimumHeight = dp(c, 56)
        val t = titleView(c, title)
        line.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val chip = Button(c)
        chip.textSize = 13f
        chip.maxLines = 2
        flat(c, chip, false)
        chip.setPadding(dp(c, AppTokens.S3), 0, dp(c, AppTokens.S3), 0)
        chip.setOnClickListener { safe { onNext(); chip.text = value(); chip.contentDescription = title + ": " + value() } }
        chip.text = value()
        chip.contentDescription = title + ": " + value()
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, AppTokens.TOUCH))
        lp.leftMargin = dp(c, AppTokens.S2)
        line.addView(chip, lp)
        box.addView(line)
        help(c, help)?.let { box.addView(it) }
        return Built(box) { safe { chip.text = value(); chip.contentDescription = title + ": " + value() } }
    }

    /**
     * v0.15.4 (N4): value chip that opens a small text dialog. OK = [onSave] (returns an error text to refuse, null = saved), Cancel = nothing changes.
     * [value] is the text on the chip ("none" when empty is the caller's job).
     */
    fun textRow(c: Context, title: String, help: String, value: () -> String, current: () -> String, hint: String, onSave: (String) -> String?): Built {
        var built: Built? = null
        built = chipRow(c, title, help, value) {
            try {
                val et = android.widget.EditText(c)
                et.setText(current())
                et.setSelection(et.text.length)
                et.hint = hint
                et.setSingleLine(false)
                val pad = dp(c, AppTokens.S4)
                val wrap = FrameLayout(c)
                wrap.setPadding(pad, dp(c, AppTokens.S2), pad, 0)
                wrap.addView(et)
                android.app.AlertDialog.Builder(c)
                    .setTitle(title)
                    .setView(wrap)
                    .setPositiveButton("OK") { _, _ ->
                        try {
                            val err = onSave(et.text.toString())
                            if (err != null) android.widget.Toast.makeText(c, err, android.widget.Toast.LENGTH_SHORT).show()
                            built?.refresh?.invoke()
                        } catch (_: Throwable) { }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (_: Throwable) { }
        }
        return built
    }

    /**
     * Title + [-] value [+] (48 dp). One tap = one step. Press and hold = repeats (slow, then faster).
     * Callbacks: [begin] (remember the old value), [step] (write + redraw, no undo record), [end] (record the change once: undo = value before the hold).
     */
    fun stepperRow(c: Context, title: String, help: String, value: () -> String, begin: () -> Unit, step: (Int) -> Unit, end: () -> Unit): Built {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.minimumHeight = dp(c, 56)
        line.addView(titleView(c, title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val val0 = TextView(c)
        val0.gravity = Gravity.CENTER
        val0.textSize = 14f
        val0.setTypeface(null, Typeface.BOLD)
        val0.setTextColor(AppUi.text(c))
        val0.minWidth = dp(c, 64)
        fun show() { val0.text = value(); val0.contentDescription = title + ": " + value() }
        val h = Handler(Looper.getMainLooper())
        var tick = 0
        var dirNow = 0
        var holding = false
        val rep = object : Runnable {
            override fun run() {
                if (!holding) return
                safe { step(dirNow); show() }
                h.postDelayed(this, ChangedPlan.repeatMs(tick++).toLong())
            }
        }
        fun stopHold() { if (holding) { holding = false; h.removeCallbacks(rep); safe { end() } } }
        fun mk(label: String, dir: Int, desc: String): Button {
            val b = Button(c)
            b.text = label
            b.textSize = 18f
            flat(c, b, false)
            b.contentDescription = desc + " " + title
            b.setOnClickListener { safe { begin(); step(dir); show(); end() } }
            b.setOnLongClickListener {   // hold = repeat; the single click above is not fired after a long press
                safe { begin(); step(dir); show(); dirNow = dir; tick = 0; holding = true; h.postDelayed(rep, ChangedPlan.repeatMs(0).toLong()) }
                true
            }
            b.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) stopHold(); false }
            return b
        }
        line.addView(mk("−", -1, "Decrease"), LinearLayout.LayoutParams(dp(c, AppTokens.TOUCH), dp(c, AppTokens.TOUCH)))
        line.addView(val0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(mk("+", 1, "Increase"), LinearLayout.LayoutParams(dp(c, AppTokens.TOUCH), dp(c, AppTokens.TOUCH)))
        box.addView(line)
        help(c, help)?.let { box.addView(it) }
        show()
        return Built(box) { show() }
    }

    /**
     * Wraps a row: a small dot at the left when the value is changed from default, long-press on the row (not on its buttons) = reset this row.
     * [changed] is asked on every refresh().
     */
    fun decorate(c: Context, content: View, changed: () -> Boolean, onReset: () -> Unit): Built {
        val outer = LinearLayout(c)
        outer.orientation = LinearLayout.HORIZONTAL
        outer.gravity = Gravity.TOP
        val dot = View(c)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(AppUi.accent(c))
        dot.background = g
        val dlp = LinearLayout.LayoutParams(dp(c, AppTokens.S2), dp(c, AppTokens.S2))
        dlp.topMargin = dp(c, 24)
        dlp.rightMargin = dp(c, AppTokens.S2)
        outer.addView(dot, dlp)
        outer.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun upd() { safe { dot.visibility = if (changed()) View.VISIBLE else View.INVISIBLE } }
        upd()
        outer.setOnLongClickListener { safe { onReset() }; true }
        outer.contentDescription = null
        return Built(outer) { upd() }
    }

    /** Small caps title + thin divider. */
    fun sectionHeader(c: Context, title: String): View {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val t = TextView(c)
        t.text = title.uppercase()
        t.textSize = 12f
        t.setTypeface(null, Typeface.BOLD)
        t.letterSpacing = 0.08f
        t.setTextColor(AppUi.dim(c))
        t.setPadding(0, dp(c, AppTokens.S4), 0, dp(c, AppTokens.S1))
        safe { t.isAccessibilityHeading = true }
        box.addView(t)
        val line = View(c)
        line.setBackgroundColor((AppUi.dim(c) and 0x00FFFFFF) or 0x40000000)
        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)))
        return box
    }

    /** Button style for Reset / Undo / presets: 48 dp, rounded, card colour. */
    fun styleAction(c: Context, b: Button): Button {
        safe {
            flat(c, b, false)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(c, AppTokens.S2)
            b.layoutParams = lp
        }
        return b
    }
}
