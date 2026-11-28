package com.mgb.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceSlimPlan

/**
 * v0.65.0 (CLEAN 2): the pieces of the Clean Replace screen, drawn like docs/replace-tool-mock-v3.html:
 *   top line [ABC] [Project: A v] [Fix N] [...]  |  one rounded card: FIND (+ options pill) / WITH (Sel, swap)
 *   collapsible options [Text | Word | Regex] [Aa] [Smart]  |  Recent pair chips  |  [< k/N >] [This] [Rest] [Replace all (N)]
 *   one meta line: status, scope, Undo.
 * Colours come from the keyboard theme (JabraTokens), shapes are rounder than the old panel (12 to 16 dp), nothing here touches the engine.
 * Every function only builds Views; the CALLER (ReplacePanel.mainViewClean) wraps the whole screen in try / catch, any exception = the older look.
 * NOT tested on a phone (Kotlin not compiled here).
 */
object ReplaceClean {
    private fun dp(ctx: android.content.Context, v: Int): Int = JabraTokens.dp(ctx, v)

    private fun lp(w: Int, h: Int, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
        val x = LinearLayout.LayoutParams(w, h, weight)
        x.setMargins(l, t, r, b)
        return x
    }

    private fun fb(host: JabraHost): (View) -> Unit = { v -> try { host.feedback(v) } catch (_: Throwable) { } }

    private fun shape(fill: Int, stroke: Int, radiusPx: Float, strokePx: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = radiusPx
        if (strokePx > 0) g.setStroke(strokePx, stroke)
        return g
    }

    /** Filled shape that gets darker while pressed. */
    private fun pressable(fill: Int, stroke: Int, radiusPx: Float, strokePx: Int): StateListDrawable {
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), shape(LookColors.darken(fill, 0.14f), stroke, radiusPx, strokePx))
        sl.addState(intArrayOf(), shape(fill, stroke, radiusPx, strokePx))
        return sl
    }

    private fun narrow(ctx: android.content.Context): Boolean =
        try { ctx.resources.displayMetrics.let { it.widthPixels / it.density } < 400f } catch (_: Throwable) { false }

    /** A rounded button. primaryColor != 0 = filled accent button with white bold text; 0 = quiet card button. weight > 0 = equal share, 0 = wrap. */
    fun button(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, label: CharSequence, hDp: Int, weight: Float, primaryColor: Int,
        textSp: Float = 0f, onClick: (Button) -> Unit
    ): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
        b.stateListAnimator = null
        b.maxLines = 1
        b.ellipsize = TextUtils.TruncateAt.END
        val side = dp(ctx, if (narrow(ctx)) 6 else 10)
        b.setPadding(side, 0, side, 0)
        b.textSize = if (textSp > 0f) textSp else if (narrow(ctx)) 13f else 14f
        val r = dp(ctx, 12).toFloat()
        if (primaryColor != 0) {
            b.background = pressable(primaryColor, Color.TRANSPARENT, r, 0)
            b.setTextColor(Color.WHITE)
            b.setTypeface(b.typeface, Typeface.BOLD)
        } else {
            b.background = pressable(JabraTokens.cardFill(p, false), JabraTokens.cardStroke(p, false, false), r, dp(ctx, 1))
            b.setTextColor(JabraTokens.cardText(p))
        }
        val f = fb(host)
        b.setOnClickListener { v -> f(v); try { onClick(b) } catch (_: Throwable) { } }
        val m = dp(ctx, 3)
        b.layoutParams = if (weight > 0f) lp(0, dp(ctx, hDp), weight, m, m, m, m) else lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, hDp), 0f, m, m, m, m)
        return b
    }

    fun dim(b: Button, on: Boolean) { b.alpha = if (on) 1f else 0.45f; if (!on) b.isClickable = false }

    // ------------------------------------------------------------------ line 1
    fun topRow(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, project: String, fixCount: Int, menuOpen: Boolean,
        onAbc: () -> Unit, onProject: () -> Unit, onFix: () -> Unit, onMore: () -> Unit
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(button(ctx, p, host, "ABC", 38, 0f, 0) { onAbc() })
        val nm = if (project.length > 14) project.substring(0, 13) + "\u2026" else project
        val sb = SpannableStringBuilder()
        sb.append(if (project.isEmpty()) "Project" else "Project ")
        sb.setSpan(ForegroundColorSpan(JabraTokens.cardNote(p)), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (project.isNotEmpty()) {
            val s = sb.length
            sb.append(nm)
            sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        sb.append("  \u25BE")
        val pb = button(ctx, p, host, sb, 38, 1f, 0) { onProject() }
        pb.contentDescription = if (project.isEmpty()) "Projects, none active" else "Project " + project
        r.addView(pb)
        if (project.isNotEmpty()) {
            val fx = button(ctx, p, host, if (fixCount > 0) "Fix " + fixCount else "Fix", 38, 0f, JabraTokens.ACCENT) { onFix() }
            fx.contentDescription = "Fix with project" + (if (fixCount > 0) ", " + fixCount + " in the text" else "")
            fx.minWidth = dp(ctx, 52)
            if (fixCount == 0) fx.alpha = 0.7f
            r.addView(fx)
        }
        val mb = button(ctx, p, host, if (menuOpen) "\u2715" else "\u22EF", 38, 0f, 0, 18f) { onMore() }
        mb.minWidth = dp(ctx, 40)
        mb.contentDescription = "More"
        r.addView(mb)
        return r
    }

    // ------------------------------------------------------------------ line 2: Find / With card
    fun card(ctx: android.content.Context, p: JabraPrefs): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = shape(JabraTokens.cardFill(p, false), JabraTokens.cardStroke(p, false, false), dp(ctx, 14).toFloat(), dp(ctx, 1))
        c.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(ctx, 3), dp(ctx, 4), dp(ctx, 3), dp(ctx, 3))
        return c
    }

    fun divider(ctx: android.content.Context, p: JabraPrefs): View {
        val d = View(ctx)
        d.setBackgroundColor(JabraTokens.cardStroke(p, false, false))
        d.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        return d
    }

    /** FIND / WITH line: small upper-case label, value (tap = type), views on the right (pill, Sel, swap). */
    fun field(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, label: String, value: String, placeholder: String, shown: String,
        onTap: () -> Unit, right: List<View>
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(ctx, 14), 0, dp(ctx, 6), 0)
        val note = JabraTokens.cardNote(p)
        val lb = TextView(ctx)
        lb.text = label.uppercase()
        lb.textSize = 10.5f
        lb.letterSpacing = 0.06f
        lb.setTextColor(note)
        r.addView(lb, lp(dp(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT))
        val vv = TextView(ctx)
        vv.textSize = 16f
        vv.maxLines = 1
        vv.ellipsize = TextUtils.TruncateAt.END
        vv.gravity = Gravity.CENTER_VERTICAL
        if (value.isEmpty()) { vv.text = placeholder; vv.setTextColor(note) } else { vv.text = shown; vv.setTextColor(JabraTokens.cardText(p)) }
        vv.isClickable = true
        val f = fb(host)
        vv.setOnClickListener { v -> f(v); try { onTap() } catch (_: Throwable) { } }
        r.addView(vv, lp(0, dp(ctx, 44), 1f))
        for (v in right) r.addView(v)
        return r
    }

    /** The mini options pill: "Text v", "Word . Aa v"; a small accent dot on its left while any option differs from the default. */
    fun pill(ctx: android.content.Context, p: JabraPrefs, host: JabraHost, mode: Int, matchCase: Boolean, smart: Boolean, open: Boolean, onClick: () -> Unit): Button {
        val b = Button(ctx)
        val modified = mode != ReplaceEngine.MODE_TEXT || matchCase || smart
        var t = ReplaceEngine.MODE_NAMES[mode.coerceIn(0, 2)]
        if (matchCase) t += " \u00B7 Aa"
        if (smart && mode != ReplaceEngine.MODE_REGEX) t += " \u00B7 \u2248"
        t += if (open) "  \u25B2" else "  \u25BC"
        b.text = t
        b.isAllCaps = false
        b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
        b.stateListAnimator = null
        b.maxLines = 1
        b.textSize = 12.5f
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.setTextColor(JabraTokens.cardText(p))
        b.setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0)
        b.background = pressable(JabraTokens.withAlpha(JabraTokens.panelText(p), 0x14), JabraTokens.cardStroke(p, false, false), dp(ctx, 16).toFloat(), dp(ctx, 1))
        if (modified) {
            try {
                val d = GradientDrawable()
                d.shape = GradientDrawable.OVAL
                d.setColor(JabraTokens.ACCENT)
                d.setBounds(0, 0, dp(ctx, 7), dp(ctx, 7))
                b.setCompoundDrawables(d, null, null, null)
                b.compoundDrawablePadding = dp(ctx, 5)
            } catch (_: Throwable) { }
        }
        b.contentDescription = "Search options: " + ReplaceEngine.MODE_NAMES[mode.coerceIn(0, 2)] + (if (matchCase) ", match case" else "") + (if (smart && mode != ReplaceEngine.MODE_REGEX) ", smart spaces" else "") + (if (open) ", open" else ", closed")
        val f = fb(host)
        b.setOnClickListener { v -> f(v); try { onClick() } catch (_: Throwable) { } }
        b.layoutParams = lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 32), 0f, dp(ctx, 2), 0, dp(ctx, 2), 0)
        return b
    }

    // ------------------------------------------------------------------ line 3 (collapsible): [Text | Word | Regex] [Aa] [Smart]
    fun options(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, mode: Int, matchCase: Boolean, smart: Boolean,
        onMode: (Int) -> Unit, onCase: () -> Unit, onSmart: () -> Unit
    ): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(ctx, 5), dp(ctx, 4), dp(ctx, 5), dp(ctx, 4))
        box.background = shape(JabraTokens.withAlpha(JabraTokens.panelText(p), 0x10), JabraTokens.cardStroke(p, false, false), dp(ctx, 14).toFloat(), dp(ctx, 1))
        box.addView(ReplaceSlim.segmented(ctx, p, host, mode) { i -> onMode(i) }, lp(0, dp(ctx, 36), 1f, 0, 0, dp(ctx, 4), 0))
        val f = fb(host)
        box.addView(UiKit.chip(ctx, p, "Aa", matchCase, 36, f) { onCase() })
        val regex = mode == ReplaceEngine.MODE_REGEX
        val sm = UiKit.chip(ctx, p, "Smart", smart && !regex, 36, f) { if (!regex) onSmart() }
        if (regex) sm.alpha = 0.4f
        box.addView(sm)
        box.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f, dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3))
        return box
    }

    // ------------------------------------------------------------------ recent pairs: one scrolling line of chips
    fun recent(ctx: android.content.Context, p: JabraPrefs, host: JabraHost, labels: List<String>, onPick: (Int) -> Unit): View {
        val sv = HorizontalScrollView(ctx)
        sv.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val f = fb(host)
        for (i in labels.indices) {
            val b = Button(ctx)
            b.text = labels[i]
            b.isAllCaps = false
            b.minWidth = 0; b.minimumWidth = 0; b.minHeight = 0; b.minimumHeight = 0
            b.stateListAnimator = null
            b.maxLines = 1
            b.textSize = 12.5f
            b.setTextColor(JabraTokens.cardNote(p))
            b.setPadding(dp(ctx, 11), 0, dp(ctx, 11), 0)
            b.background = pressable(JabraTokens.cardFill(p, false), JabraTokens.cardStroke(p, false, false), dp(ctx, 15).toFloat(), dp(ctx, 1))
            b.setOnClickListener { v -> f(v); try { onPick(i) } catch (_: Throwable) { } }
            row.addView(b, lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 30), 0f, dp(ctx, 3), dp(ctx, 2), dp(ctx, 3), dp(ctx, 2)))
        }
        sv.addView(row)
        sv.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return sv
    }

    // ------------------------------------------------------------------ line 4: [< k/N >] [This] [Rest] [Replace all (N)]
    private class BadgeDrawable(private val ctx: android.content.Context, private val n: Int) : android.graphics.drawable.Drawable() {
        private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val ink = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val label = if (n > 999) "999+" else n.toString()
        private val h = JabraTokens.dp(ctx, 18)
        private val w: Int
        init {
            fill.color = JabraTokens.withAlpha(Color.WHITE, 0x44)
            ink.color = Color.WHITE
            ink.textSize = 11f * ctx.resources.displayMetrics.scaledDensity
            ink.typeface = Typeface.DEFAULT_BOLD
            ink.textAlign = android.graphics.Paint.Align.CENTER
            w = Math.max(h, ink.measureText(label).toInt() + JabraTokens.dp(ctx, 10))
        }
        override fun getIntrinsicWidth(): Int = w
        override fun getIntrinsicHeight(): Int = h
        override fun draw(c: android.graphics.Canvas) {
            val b = bounds
            c.drawRoundRect(android.graphics.RectF(b), h / 2f, h / 2f, fill)
            val y = b.exactCenterY() - (ink.descent() + ink.ascent()) / 2f
            c.drawText(label, b.exactCenterX(), y, ink)
        }
        override fun setAlpha(a: Int) { }
        override fun setColorFilter(f: android.graphics.ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    fun actions(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, plan: ReplaceSlimPlan.Step, nHits: Int, ask: Boolean, allColor: Int,
        onPrev: () -> Unit, onNext: () -> Unit, onCounter: () -> Unit, onRest: () -> Unit, onThis: () -> Unit, onAll: () -> Unit, styleAll: (Button) -> Unit
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val f = fb(host)
        // stepper box
        val st = LinearLayout(ctx)
        st.orientation = LinearLayout.HORIZONTAL
        st.gravity = Gravity.CENTER_VERTICAL
        st.background = shape(JabraTokens.cardFill(p, false), JabraTokens.cardStroke(p, false, false), dp(ctx, 12).toFloat(), dp(ctx, 1))
        fun arrow(sign: String, desc: String, go: () -> Unit): TextView {
            val t = TextView(ctx)
            t.text = sign
            t.textSize = 19f
            t.gravity = Gravity.CENTER
            t.setTextColor(JabraTokens.cardText(p))
            t.contentDescription = desc
            if (plan.navEnabled) { t.isClickable = true; t.setOnClickListener { v -> f(v); try { go() } catch (_: Throwable) { } } } else t.alpha = 0.45f
            return t
        }
        st.addView(arrow("\u2039", "Previous match", onPrev), lp(dp(ctx, 28), LinearLayout.LayoutParams.MATCH_PARENT))
        val c = TextView(ctx)
        c.text = plan.counter.replace(" / ", "/")
        c.textSize = 13f
        c.gravity = Gravity.CENTER
        c.minWidth = dp(ctx, 34)
        c.setTextColor(JabraTokens.cardNote(p))
        c.contentDescription = "Match " + plan.counter
        if (plan.navEnabled) { c.isClickable = true; c.setOnClickListener { v -> f(v); try { onCounter() } catch (_: Throwable) { } } }
        st.addView(c, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        st.addView(arrow("\u203A", "Next match", onNext), lp(dp(ctx, 28), LinearLayout.LayoutParams.MATCH_PARENT))
        r.addView(st, lp(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 38), 0f, dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3)))
        val one = button(ctx, p, host, "This", 38, 1f, 0) { onThis() }
        dim(one, plan.navEnabled)
        r.addView(one)
        val rest = button(ctx, p, host, "Rest", 38, 1f, 0) { onRest() }
        dim(rest, plan.navEnabled)
        r.addView(rest)
        val all = button(ctx, p, host, plan.allLabel, 38, 2.3f, allColor, if (ask) 12f else 0f) { onAll() }
        if (!ask && plan.allEnabled && nHits > 0) {   // the count is a round badge inside the button, not part of the text
            try {
                val d = BadgeDrawable(ctx, nHits)
                d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
                all.setCompoundDrawables(null, null, d, null)
                all.compoundDrawablePadding = dp(ctx, 5)
            } catch (_: Throwable) { }
        }
        if (!plan.allEnabled) all.alpha = 0.55f
        styleAll(all)   // long-press = peek (the panel's own code)
        r.addView(all)
        return r
    }

    // ------------------------------------------------------------------ line 5: status, scope, undo
    fun meta(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, left: String, scopeLabel: String, onScope: () -> Unit,
        rightLabel: String, onRight: () -> Unit
    ): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.minimumHeight = dp(ctx, 28)
        val t = TextView(ctx)
        t.text = left
        t.textSize = 12.5f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(JabraTokens.note(p))
        t.setPadding(dp(ctx, 8), 0, dp(ctx, 4), 0)
        r.addView(t, lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(ReplaceSlim.textButton(ctx, host, scopeLabel + " \u25BE", 30, JabraTokens.note(p)) { onScope() })
        r.addView(ReplaceSlim.textButton(ctx, host, rightLabel, 30, JabraTokens.note(p)) { onRight() })
        r.layoutParams = lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return r
    }
}
