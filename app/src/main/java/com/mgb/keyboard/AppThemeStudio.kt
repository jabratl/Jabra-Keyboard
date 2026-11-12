package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.CustomTheme
import com.mgb.keyboard.typing.CustomThemeForm
import com.mgb.keyboard.typing.SizePct

/**
 * v0.15.7 (N7): custom theme editor for the app screen (Look page, section "Custom theme").
 * Edits the same single custom theme as the keyboard Theme Studio (pref ct_json, shape {"ver":1,...} written by CustomTheme.toJson).
 * Colours by hex fields (invalid = red text + nothing is saved), round, key opacity, readability warning, live mini preview.
 * ct_json is written ONLY by the buttons Save / Save and use / Delete (never per keystroke). Rules come from typing/CustomThemeForm.java.
 * Everything in try / catch: a failure = no editor, the rest of the page stays.
 */
object AppThemeStudio {
    class Handle(val view: View, val reload: () -> Unit)

    private class Mini(c: Context) : View(c) {
        var t: CustomTheme? = null
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(cv: Canvas) {
            try {
                val d = resources.displayMetrics.density
                val w = width.toFloat(); val h = height.toFloat()
                r.set(0f, 0f, w, h)
                val th = t
                if (th == null) {
                    p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0xFFD84315.toInt()
                    cv.drawRoundRect(r, 8f * d, 8f * d, p)
                    p.style = Paint.Style.FILL; p.textSize = 12f * d; p.textAlign = Paint.Align.CENTER
                    cv.drawText("Fix the red colours to see the preview", w / 2f, h / 2f, p)
                    return
                }
                p.style = Paint.Style.FILL
                if (th.hasBg2) p.shader = LinearGradient(0f, 0f, w, h, th.bg, th.bg2, Shader.TileMode.CLAMP) else { p.shader = null; p.color = th.bg }
                cv.drawRoundRect(r, 8f * d, 8f * d, p)
                p.shader = null
                val rad = th.radiusDp() * d
                val pad = 10f * d; val gap = 6f * d
                val kw = (w - 2f * pad - 3f * gap) / 4f
                val kh = h - 2f * pad
                for (i in 0 until 4) {
                    val l = pad + i * (kw + gap)
                    r.set(l, pad, l + kw, pad + kh)
                    p.color = th.withKeyAlpha(if (i == 3) th.func else th.key)
                    cv.drawRoundRect(r, rad, rad, p)
                    p.color = th.text
                    p.textAlign = Paint.Align.CENTER
                    p.textSize = 16f * d
                    cv.drawText(arrayOf("A", "b", "1", "⬆")[i], l + kw / 2f, pad + kh / 2f + 6f * d, p)
                }
            } catch (_: Throwable) { }
        }
    }

    /** [onSaved] is called after ct_json was written; the argument is the theme index BEFORE the write (for Undo). The caller redraws rows / grid / preview. */
    fun build(ctx: Context, prefs: JabraPrefs, onSaved: (Int) -> Unit): Handle {
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL

        var fields = CustomThemeForm.fields(JabraThemes.custom(prefs))
        var round = JabraThemes.custom(prefs)?.round ?: CustomTheme.defaults().round
        var alpha = JabraThemes.custom(prefs)?.alpha ?: CustomTheme.defaults().alpha
        var dirty = false
        var muting = false
        var armed = false
        val edits = ArrayList<EditText>()
        val swatches = ArrayList<View>()

        val mini = Mini(ctx)
        val warn = TextView(ctx)
        warn.textSize = 12f
        warn.setTextColor(AppUi.warn(ctx))
        warn.setPadding(0, dp(AppTokens.S1), 0, dp(AppTokens.S1))
        lateinit var stepRound: AppRows.Built
        lateinit var stepAlpha: AppRows.Built

        fun update() {
            val ok = CustomThemeForm.check(fields)
            val th = CustomThemeForm.build(fields, round, alpha)
            mini.t = th
            mini.invalidate()
            for (i in 0 until 5) {
                edits[i].setTextColor(if (ok[i]) AppUi.text(ctx) else AppUi.warn(ctx))
                val g = GradientDrawable()
                g.cornerRadius = dp(6).toFloat()
                val c = if (ok[i] && fields[i].isNotBlank()) CustomTheme.hex(fields[i]) else null
                if (c != null) g.setColor(c) else g.setColor(0x00000000)
                g.setStroke(dp(1), if (ok[i]) AppUi.dim(ctx) else AppUi.warn(ctx))
                swatches[i].background = g
            }
            val w = CustomThemeForm.warnings(th)
            warn.text = if (th == null) "Some colours are not valid (use 6 digits like #1B3A57). Nothing is saved until they are fixed." else if (w.isEmpty()) "" else "⚠ " + w.joinToString("\n⚠ ")
            warn.visibility = if (warn.text.isEmpty()) View.GONE else View.VISIBLE
        }

        fun setAll(c: CustomTheme?, mark: Boolean) {
            muting = true
            fields = CustomThemeForm.fields(c)
            round = c?.round ?: CustomTheme.defaults().round
            alpha = c?.alpha ?: CustomTheme.defaults().alpha
            for (i in 0 until 5) edits[i].setText(fields[i])
            muting = false
            dirty = mark
            try { stepRound.refresh(); stepAlpha.refresh() } catch (_: Throwable) { }
            update()
        }

        val note = TextView(ctx)
        note.text = "Your own theme (theme slot 21 in the picker). Edit, then Save. Round and opacity show with Look = Jabra."
        note.textSize = 12f
        note.setTextColor(AppUi.dim(ctx))
        note.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
        box.addView(note)
        box.addView(mini, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))

        for (i in 0 until 5) {
            val line = LinearLayout(ctx)
            line.orientation = LinearLayout.HORIZONTAL
            line.gravity = Gravity.CENTER_VERTICAL
            line.minimumHeight = dp(AppTokens.TOUCH)
            val lab = TextView(ctx)
            lab.text = CustomThemeForm.NAMES[i]
            lab.textSize = 15f
            lab.setTextColor(AppUi.text(ctx))
            line.addView(lab, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val sw = View(ctx)
            swatches.add(sw)
            val slp = LinearLayout.LayoutParams(dp(32), dp(32))
            slp.rightMargin = dp(AppTokens.S2)
            line.addView(sw, slp)
            val et = EditText(ctx)
            et.setSingleLine(true)
            et.typeface = Typeface.MONOSPACE
            et.textSize = 15f
            et.hint = if (i == 1) "none" else "#RRGGBB"
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            et.filters = arrayOf(InputFilter.LengthFilter(9))
            et.setText(fields[i])
            et.contentDescription = CustomThemeForm.NAMES[i] + " colour, hex"
            et.minHeight = dp(AppTokens.TOUCH)
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                override fun afterTextChanged(s: Editable?) {
                    if (muting) return
                    try { fields[i] = s?.toString() ?: ""; dirty = true; update() } catch (_: Throwable) { }
                }
            })
            edits.add(et)
            line.addView(et, LinearLayout.LayoutParams(dp(124), ViewGroup.LayoutParams.WRAP_CONTENT))
            box.addView(line)
        }

        stepRound = AppRows.stepperRow(ctx, "Corner roundness", "0 = square, 100 = very round (Jabra look keys).", { round.toString() }, { }, { dir ->
            round = SizePct.step(round, dir, CustomTheme.ROUND_MIN, CustomTheme.ROUND_MAX, 5); dirty = true; update()
        }, { })
        stepAlpha = AppRows.stepperRow(ctx, "Key opacity", "How solid the keys are, in percent.", { alpha.toString() + "%" }, { }, { dir ->
            alpha = SizePct.step(alpha, dir, CustomTheme.ALPHA_MIN, CustomTheme.ALPHA_MAX, 5); dirty = true; update()
        }, { })
        box.addView(stepRound.view)
        box.addView(stepAlpha.view)
        box.addView(warn)

        fun save(use: Boolean) {
            val th = CustomThemeForm.build(fields, round, alpha)
            if (th == null) { Toast.makeText(ctx, "Fix the red colours first. Nothing was saved.", Toast.LENGTH_SHORT).show(); return }
            val old = JabraThemes.index(prefs)
            prefs.setStr("ct_json", th.toJson())
            if (use) prefs.setInt("theme", JabraThemes.ALL.size)
            dirty = false
            Toast.makeText(ctx, if (use) "Saved. Custom theme is ON." else "Saved.", Toast.LENGTH_SHORT).show()
            try { onSaved(old) } catch (_: Throwable) { }
        }

        fun action(label: String, click: (Button) -> Unit): Button {
            val b = Button(ctx)
            b.text = label
            b.isAllCaps = false
            AppRows.styleAction(ctx, b)
            b.setOnClickListener { try { click(b) } catch (_: Throwable) { } }
            return b
        }

        val pr = LinearLayout(ctx)
        pr.orientation = LinearLayout.HORIZONTAL
        val head = TextView(ctx)
        head.text = "Fill from a colour set (not saved until you press Save)"
        head.textSize = 12f
        head.setTextColor(AppUi.dim(ctx))
        head.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
        box.addView(head)
        for (i in CustomTheme.PRESET_NAMES.indices) {
            val b = action(CustomTheme.PRESET_NAMES[i]) { setAll(CustomTheme.preset(i), true) }
            b.textSize = 12f
            val lp = LinearLayout.LayoutParams(0, dp(AppTokens.TOUCH), 1f)
            lp.setMargins(dp(AppTokens.S1), 0, dp(AppTokens.S1), 0)
            b.layoutParams = lp
            b.setPadding(dp(2), 0, dp(2), 0)
            pr.addView(b)
        }
        box.addView(pr)
        box.addView(action("Save") { save(false) })
        box.addView(action("Save and use this theme") { save(true) })
        box.addView(action("Delete custom theme") { b ->
            if (!armed) {
                armed = true
                b.text = "Tap again: delete the custom theme"
                b.setTextColor(AppUi.warn(ctx))
                b.postDelayed({ armed = false; b.text = "Delete custom theme"; b.setTextColor(AppUi.text(ctx)) }, 3000)
            } else {
                armed = false
                b.text = "Delete custom theme"
                b.setTextColor(AppUi.text(ctx))
                val old = JabraThemes.index(prefs)
                prefs.setStr("ct_json", "")
                if (prefs.int("theme", 0) == JabraThemes.ALL.size) prefs.setInt("theme", 0)
                setAll(null, false)
                Toast.makeText(ctx, "Custom theme deleted", Toast.LENGTH_SHORT).show()
                try { onSaved(old) } catch (_: Throwable) { }
            }
        })

        update()
        fun reload() { if (!dirty) setAll(JabraThemes.custom(prefs), false) }   // pick up a change made in the keyboard Studio (only when nothing is being edited here)
        box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { try { reload() } catch (_: Throwable) { } }
            override fun onViewDetachedFromWindow(v: View) { }
        })
        return Handle(box) { try { reload() } catch (_: Throwable) { } }
    }
}
