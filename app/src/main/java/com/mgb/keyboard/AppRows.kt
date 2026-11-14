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
import com.mgb.keyboard.typing.RowPlan

/**
 * v0.12.0 (M2 of the "App UI" plan): widget factories for the new app pages (pref app_ui = 1). Only the LOOK of a row lives here;
 * prefs, undo, reset, search stay in SettingsGroups (the callbacks come from there). Every factory returns a Built = view + refresh().
 * Nothing here throws out: SettingsGroups calls these inside the group's try / catch, and the old Button rows are the fallback.
 */
object AppRows {

    class Built(val view: View, val refresh: () -> Unit)

    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()
    private fun safe(b: () -> Unit) { try { b() } catch (_: Throwable) { } }

    private fun titleView(c: Context, text: String): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = AppTokens.T_ROW.toFloat()
        t.setTextColor(AppUi.text(c))
        return t
    }

    private fun chipBg(c: Context, selected: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(if (selected) AppUi.accent(c) else AppUi.surface2(c))   // v0.17.76 (U2): selected = accent, unselected = surface2 + hairline (clear in light and dark)
        g.cornerRadius = dp(c, AppTokens.R_BTN).toFloat()
        if (!selected) { try { g.setStroke(dp(c, AppTokens.STROKE).coerceAtLeast(1), AppUi.outline(c)) } catch (_: Throwable) { } }
        return g
    }

    private fun onAccent(c: Context): Int = AppUi.onAccent(c)

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
        sw.textSize = AppTokens.T_ROW.toFloat()
        sw.setTextColor(AppUi.text(c))
        sw.minHeight = dp(c, AppTokens.ROW_MIN)
        safe {   // v0.17.76 (U2): accent coloured switch (thumb + track follow the app palette)
            val on = AppUi.accent(c); val off = AppUi.dim(c)
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            sw.thumbTintList = android.content.res.ColorStateList(st, intArrayOf(on, off))
            sw.trackTintList = android.content.res.ColorStateList(st, intArrayOf((on and 0x00FFFFFF) or 0x66000000, (off and 0x00FFFFFF) or 0x33000000))
        }
        sw.setPadding(0, dp(c, AppTokens.S1), 0, dp(c, AppTokens.S1))
        var quiet = false
        sw.isChecked = isOn()
        sw.setOnCheckedChangeListener { _, v -> if (!quiet) safe { onSet(v) } }
        val hp = AppHelp.make(c, title, help)   // v0.17.78 (U3): (i) icon + help (smart)
        val swLine = LinearLayout(c)
        swLine.orientation = LinearLayout.HORIZONTAL
        swLine.gravity = Gravity.CENTER_VERTICAL
        swLine.addView(sw, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hp.info?.let { swLine.addView(it, AppHelp.infoLp(c)) }
        box.addView(swLine, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        hp.text?.let { box.addView(it) }
        return Built(box) { quiet = true; try { sw.isChecked = isOn() } catch (_: Throwable) { }; quiet = false }
    }

    /** 2-4 short options as a segmented control (one tap = that option). */
    fun choiceRow(c: Context, title: String, help: String, names: () -> Array<String>, cur: () -> Int, onPick: (Int) -> Unit): Built {
        val big = try { !RowPlan.segmented(names()) } catch (_: Throwable) { false }   // v0.17.76 (U2): many / long options = value chip + pick list
        if (big) { val r = try { choiceList(c, title, help, names, cur, onPick) } catch (_: Throwable) { null }; if (r != null) return r }
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val t = titleView(c, title)
        t.setPadding(0, dp(c, AppTokens.S2), 0, dp(c, AppTokens.S1))
        val hp = AppHelp.make(c, title, help)   // v0.17.78 (U3)
        val tline = LinearLayout(c)
        tline.orientation = LinearLayout.HORIZONTAL
        tline.gravity = Gravity.CENTER_VERTICAL
        tline.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hp.info?.let { tline.addView(it, AppHelp.infoLp(c)) }
        box.addView(tline)
        val seg = LinearLayout(c)
        seg.orientation = LinearLayout.HORIZONTAL
        val btns = ArrayList<Button>()
        val n0 = names()
        for (i in n0.indices) {
            val b = Button(c)
            b.text = n0[i]
            b.textSize = AppTokens.T_LABEL.toFloat()
            b.maxLines = 2
            b.setOnClickListener { safe { onPick(i); paint(c, btns, names, cur) } }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(c, 2), 0, dp(c, 2), 0)
            seg.addView(b, lp)
            btns.add(b)
        }
        box.addView(seg)
        hp.text?.let { it.setPadding(0, dp(c, AppTokens.S1), 0, dp(c, AppTokens.S2)); box.addView(it) }
        paint(c, btns, names, cur)
        return Built(box) { paint(c, btns, names, cur) }
    }

    /** v0.17.76 (U2): title left, value chip (with a drawn caret) right; tap = a pick list with every option and the current one marked. */
    private fun choiceList(c: Context, title: String, help: String, names: () -> Array<String>, cur: () -> Int, onPick: (Int) -> Unit): Built {
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.minimumHeight = dp(c, AppTokens.ROW_MIN)
        line.addView(titleView(c, title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val hp = AppHelp.make(c, title, help)   // v0.17.78 (U3)
        hp.info?.let { line.addView(it, AppHelp.infoLp(c)) }
        val chip = Button(c)
        chip.textSize = AppTokens.T_LABEL.toFloat()
        chip.maxLines = 2
        flat(c, chip, false)
        chip.setPadding(dp(c, AppTokens.S3), 0, dp(c, AppTokens.S3), 0)
        fun show() {
            safe {
                val n = names(); val i = RowPlan.clamp(cur(), n.size)
                val v = if (n.isEmpty()) "" else n[i]
                Glyphs.relabel(chip, v + " " + "▾")
                chip.contentDescription = title + ": " + v
            }
        }
        chip.setOnClickListener {
            safe {
                val n = names()
                android.app.AlertDialog.Builder(c)
                    .setTitle(title)
                    .setSingleChoiceItems(n, RowPlan.clamp(cur(), n.size)) { d, which -> safe { onPick(which); show() }; d.dismiss() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        show()
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, AppTokens.TOUCH))
        lp.leftMargin = dp(c, AppTokens.S2)
        line.addView(chip, lp)
        box.addView(line)
        hp.text?.let { box.addView(it) }
        return Built(box) { show() }
    }

    private fun paint(c: Context, btns: List<Button>, names: () -> Array<String>, cur: () -> Int) {
        safe {
            val n = names()
            val sel = cur().coerceIn(0, n.size - 1)
            for (i in btns.indices) {
                flat(c, btns[i], i == sel)
                btns[i].contentDescription = RowPlan.optionDesc(n[RowPlan.clamp(i, n.size)], i == sel)
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
        line.minimumHeight = dp(c, AppTokens.ROW_MIN)
        val t = titleView(c, title)
        line.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val hp = AppHelp.make(c, title, help)   // v0.17.78 (U3)
        hp.info?.let { line.addView(it, AppHelp.infoLp(c)) }
        val chip = Button(c)
        chip.textSize = AppTokens.T_LABEL.toFloat()
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
        hp.text?.let { box.addView(it) }
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
     * v0.15.8 (N8): when [onType] is given, a tap on the value opens a number dialog (hint = [typeHint], e.g. "40 - 140"). OK = [onType] gets the typed text and
     * returns a short message to show as a toast (null = nothing to say); Cancel = nothing changes.
     */
    fun stepperRow(c: Context, title: String, help: String, value: () -> String, begin: () -> Unit, step: (Int) -> Unit, end: () -> Unit,
                  typeHint: String = "", onType: ((String) -> String?)? = null): Built {   // v0.15.8 (N8): 2 trailing params with defaults (old callers unchanged)
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        val line = LinearLayout(c)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER_VERTICAL
        line.minimumHeight = dp(c, AppTokens.ROW_MIN)
        line.addView(titleView(c, title), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val hp = AppHelp.make(c, title, help)   // v0.17.78 (U3)
        hp.info?.let { line.addView(it, AppHelp.infoLp(c)) }
        val val0 = TextView(c)
        val0.gravity = Gravity.CENTER
        val0.textSize = AppTokens.T_BODY.toFloat()
        val0.setTypeface(null, Typeface.BOLD)
        val0.setTextColor(AppUi.text(c))
        val0.minWidth = dp(c, 64)
        fun show() { val0.text = value(); val0.contentDescription = title + ": " + value() + (if (onType != null) ". Tap to type a value" else "") }
        if (onType != null) safe {   // v0.15.8 (N8): tap the number = type the exact value (dialog)
            val0.minHeight = dp(c, AppTokens.TOUCH)
            val0.paintFlags = val0.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG   // small cue that the number can be tapped
            val0.setOnClickListener {
                safe {
                    val et = android.widget.EditText(c)
                    et.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    et.setText(value().filter { it.isDigit() || it == '-' })
                    et.selectAll()
                    et.hint = typeHint
                    et.setSingleLine(true)
                    val pad = dp(c, AppTokens.S4)
                    val wrap = FrameLayout(c)
                    wrap.setPadding(pad, dp(c, AppTokens.S2), pad, 0)
                    wrap.addView(et)
                    val dlg = android.app.AlertDialog.Builder(c)
                        .setTitle(title)
                        .setMessage(if (typeHint.isEmpty()) null else "Allowed: " + typeHint)
                        .setView(wrap)
                        .setPositiveButton("OK") { _, _ ->
                            safe {
                                val err = onType(et.text.toString())
                                if (err != null) android.widget.Toast.makeText(c, err, android.widget.Toast.LENGTH_SHORT).show()
                                show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .create()
                    dlg.show()
                }
            }
        }
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
            b.textSize = AppTokens.T_HEAD.toFloat()
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
        hp.text?.let { box.addView(it) }
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
        dlp.topMargin = dp(c, AppTokens.ROW_MIN / 2 - AppTokens.S1)   // v0.17.76 (U2): centred on a 56 dp row (was 24)
        dlp.rightMargin = dp(c, AppTokens.S2)
        outer.addView(dot, dlp)
        outer.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun upd() { safe { dot.visibility = if (changed()) View.VISIBLE else View.INVISIBLE } }
        upd()
        outer.setOnLongClickListener { safe { onReset() }; true }
        outer.contentDescription = null
        val wrap = LinearLayout(c)   // v0.17.76 (U2): a hairline under every row (the wrapper is the row view for search / flash)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(outer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        safe { wrap.addView(AppUi.divider(c)) }
        return Built(wrap) { upd() }
    }

    /** Small caps title + thin divider (v0.17.76 (U2): the look lives in AppUi.sectionHeader now, same result). */
    fun sectionHeader(c: Context, title: String): View = AppUi.sectionHeader(c, title)

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

    /**
     * v0.15.8 (N8): two-tap button for "Reset this section / page". Normal look (styleAction); the first tap changes the text to [confirm] in the
     * warning colour for 3 s, a second tap inside that time runs [onDo]. Waiting out the 3 s puts the button back (nothing was done).
     */
    fun confirmAction(c: Context, label: String, confirm: String, onDo: () -> Unit): Button {
        val b = Button(c)
        styleAction(c, b)
        var armed = false
        val idle = Runnable { armed = false; b.text = label; b.setTextColor(AppUi.text(c)) }
        b.text = label
        b.setOnClickListener {
            safe {
                if (!armed) {
                    armed = true
                    b.text = confirm
                    b.setTextColor(AppUi.warn(c))
                    b.postDelayed(idle, 3000)
                } else {
                    b.removeCallbacks(idle)
                    armed = false
                    b.text = label
                    b.setTextColor(AppUi.text(c))
                    onDo()
                }
            }
        }
        return b
    }
}
