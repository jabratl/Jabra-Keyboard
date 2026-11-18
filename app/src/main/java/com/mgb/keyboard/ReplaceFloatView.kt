package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.LookColors
import com.mgb.keyboard.typing.ReplaceFloat

/**
 * v0.39.1 (FLOAT, step F2): the VIEW of the floating preview window of Find & Replace (plan: PLAN_RP_FLOAT.txt). View only: no popup, no drag, no pref of its own.
 * window(): header (grip, "Preview", count pill, minimize, close) + tabs Before | After | Changes + body (max 3 rows, the current match first and bold)
 *           + footer (< n/N >, Undo, Replace this). bubble(): 46 dp round view with the match count.
 * All texts / rows come from the pure class typing/ReplaceFloat.java (snap). Colours: JabraTokens / UiKit (found = amber, replacement = green, fixed accents like PeekPopup).
 * A button whose callback is null is NOT drawn (e.g. minimize / close while the window sits inside the Replace panel).
 * No shadows; a 1 dp stronger border separates the window from the text. Touch targets >= 36 dp, fonts >= 11 sp.
 * Every part has try / catch; build() throws only when nothing at all could be drawn, so the caller can show the old inline preview (FALLBACK).
 * Delete this file + the hooks marked "v0.39.1" = back to v0.38.1.
 */
class ReplaceFloatView(private val ctx: Context, private val prefs: JabraPrefs, private val feedback: (View) -> Unit) {

    class Actions(
        val onTab: (Int) -> Unit,
        val onPrev: () -> Unit,
        val onNext: () -> Unit,
        val onReplace: () -> Unit,
        val onUndo: (() -> Unit)? = null,
        val onMinimize: (() -> Unit)? = null,
        val onClose: (() -> Unit)? = null,
        val notice: String = "",         // e.g. "Replaced 3": shown in the pill instead of the count
        val onPick: ((Int) -> Unit)? = null   // v0.39.6: tap a row = that match (0-based) becomes the current one (simple window only)
    )

    private val AMBER: Int = 0xFFF9A825.toInt()
    private val GREEN: Int = 0xFF2E7D32.toInt()

    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)
    private fun ink(): Int = try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }
    private fun dim(): Int = try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }

    private fun tv(sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        return t
    }

    /** Rounded fill + border, drawn the same in every theme. [strong] = the 1 dp stronger border of the window. */
    private fun shape(fill: Int, stroke: Int?, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) g.setStroke(dp(1).coerceAtLeast(1), stroke)
        return g
    }

    /** Count pill (soft blue; warning colour for an error). */
    private fun pill(text: String, warn: Boolean): TextView {
        val t = tv(JabraTokens.T_BTN, if (warn) Color.WHITE else JabraTokens.ON_ACCENT_SOFT, true)
        t.text = text
        t.gravity = Gravity.CENTER
        t.setPadding(dp(10), 0, dp(10), 0)
        t.background = shape(if (warn) JabraTokens.WARN else JabraTokens.ACCENT_SOFT, null, 20)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(24))
        lp.setMargins(dp(8), 0, dp(4), 0)
        t.layoutParams = lp
        return t
    }

    /** Small round icon button (36 x 36 dp touch target). */
    private fun iconBtn(label: String, desc: String, onClick: () -> Unit): TextView {
        val t = tv(16f, ink(), true)
        t.text = label
        t.gravity = Gravity.CENTER
        t.contentDescription = desc
        t.isClickable = true
        t.layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        t.setOnClickListener { try { feedback(t) } catch (_: Throwable) { }; try { onClick() } catch (_: Throwable) { } }
        return t
    }

    /** Grip: 2 x 3 dots, drawn in code (no image file). The drag handle of the window (F3 attaches the touch listener to the whole header). */
    private fun grip(): View {
        val c = dim()
        val v = object : View(ctx) {
            override fun onDraw(cv: Canvas) {
                try {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG)
                    p.color = c
                    val r = dp(2).toFloat()
                    val cx = width / 2f
                    val cy = height / 2f
                    val gap = dp(6).toFloat()
                    for (i in -1..1) { cv.drawCircle(cx - gap / 2f, cy + i * gap, r, p); cv.drawCircle(cx + gap / 2f, cy + i * gap, r, p) }
                } catch (_: Throwable) { }
            }
        }
        v.layoutParams = LinearLayout.LayoutParams(dp(24), dp(36))
        v.contentDescription = "Drag"
        return v
    }

    /** Filled primary button (Replace this). Same drawing idea as ReplacePanel.accentButton. */
    private fun primary(label: String, onClick: () -> Unit): Button {
        val b = UiKit.btn(ctx, prefs, label, 0f, 36, 12f, feedback) { onClick() }
        try {
            val r = dp(JabraTokens.R_SM).toFloat()
            val up = GradientDrawable(); up.setColor(JabraTokens.ACCENT); up.cornerRadius = r
            val down = GradientDrawable(); down.setColor(LookColors.darken(JabraTokens.ACCENT, 0.18f)); down.cornerRadius = r
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            b.background = sl
            b.backgroundTintList = null
            b.stateListAnimator = null
            b.setTextColor(Color.WHITE)
            b.setTypeface(b.typeface, Typeface.BOLD)
            b.setPadding(dp(12), 0, dp(12), 0)
        } catch (_: Throwable) { }
        return b
    }

    private fun mark(sb: SpannableStringBuilder, a: Int, b: Int, tint: Int, strike: Boolean) {
        try {
            val s = a.coerceIn(0, sb.length)
            val e = b.coerceIn(s, sb.length)
            if (e > s) {
                sb.setSpan(BackgroundColorSpan(JabraTokens.withAlpha(tint, 0x66)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (strike) sb.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } catch (_: Throwable) { }
    }

    /** One body line for a tab. Before: found amber. After: replacement green. Changes: found (struck out) -> replacement  #n. */
    private fun rowText(r: ReplaceFloat.Row, tab: Int): CharSequence {
        val sb = SpannableStringBuilder(r.text(tab))
        try {
            when (ReplaceFloat.sanitizeTab(tab)) {
                ReplaceFloat.TAB_AFTER -> { val m = r.mark(tab); mark(sb, m[0], m[1], GREEN, false) }
                ReplaceFloat.TAB_CHANGES -> {
                    val f = r.found.length
                    mark(sb, 0, f, AMBER, true)
                    val a = f + 3   // " \u2192 " is 3 chars
                    mark(sb, a, a + r.repl.length, GREEN, false)
                }
                else -> { val m = r.mark(tab); mark(sb, m[0], m[1], AMBER, false) }
            }
            if (r.current) sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } catch (_: Throwable) { }
        return sb
    }

    /** v0.39.6 (simple window): ONE line per match: left + found (amber, struck out) + replacement (green) + right. Current row bold. */
    private fun rowCombined(r: ReplaceFloat.Row): CharSequence {
        val sb = SpannableStringBuilder()
        try {
            sb.append(r.left)
            val a = sb.length
            sb.append(r.found)
            val b = sb.length
            sb.append(r.repl)
            val c = sb.length
            sb.append(r.right)
            mark(sb, a, b, AMBER, true)
            mark(sb, b, c, GREEN, false)
            if (r.current) sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } catch (_: Throwable) { }
        return sb
    }

    private fun body(snap: ReplaceFloat.Snap, tab: Int, simple: Boolean = false, onPick: ((Int) -> Unit)? = null): View {
        val maxRows = if (simple) (if (snap.setMode && snap.setLines.size > 2) minOf(snap.setLines.size, 4) else 2) else ReplaceFloat.MAX_ROWS   // v0.40.0: a pair list (3-4 pairs) shows one line per pair
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(8), dp(2), dp(8), dp(2))
        box.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_DP * maxRows + 4))   // fixed height: the window does not jump
        val lines = ArrayList<CharSequence>()
        val picks = ArrayList<Int>()   // 0-based match of each line, -1 = none
        if (snap.setMode && snap.setLines.isNotEmpty()) {
            for (l in snap.setLines.take(maxRows)) { lines.add(l); picks.add(-1) }
        } else {
            for (r in snap.rows.take(maxRows)) { lines.add(if (simple) rowCombined(r) else rowText(r, tab)); picks.add(r.n - 1) }
        }
        if (lines.isEmpty()) {
            val e = tv(12f, dim())
            e.text = if (snap.empty.isNotEmpty()) snap.empty else "No match"
            e.gravity = Gravity.CENTER_VERTICAL
            e.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_DP))
            box.addView(e)
            return box
        }
        for ((li, ln) in lines.withIndex()) {
            val t = tv(12f, ink())
            t.text = ln
            val pk = picks.getOrElse(li) { -1 }
            if (onPick != null && pk >= 0) {
                t.isClickable = true
                t.setOnClickListener { try { feedback(t) } catch (_: Throwable) { }; try { onPick(pk) } catch (_: Throwable) { } }
            }
            t.gravity = Gravity.CENTER_VERTICAL
            t.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ROW_DP))
            box.addView(t)
        }
        return box
    }

    private fun tabs(tab: Int, a: Actions): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val cur = ReplaceFloat.sanitizeTab(tab)
        for (i in 0..2) {
            val c = UiKit.chip(ctx, prefs, ReplaceFloat.TAB_NAMES[i], cur == i, 32, feedback) { a.onTab(i) }
            c.layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).also { it.setMargins(dp(2), dp(2), dp(2), dp(2)) }
            row.addView(c)
        }
        return row
    }

    private fun footer(snap: ReplaceFloat.Snap, a: Actions): View? {
        val canStep = !snap.setMode && snap.rows.isNotEmpty() && snap.err == false
        if (!canStep && a.onUndo == null) return null
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        if (canStep) {
            row.addView(UiKit.btn(ctx, prefs, "<", 0f, 36, 12f, feedback) { a.onPrev() }.also { it.minWidth = dp(40) })
            val n = tv(12f, ink(), true)
            n.text = snap.cur.toString() + "/" + snap.total
            n.gravity = Gravity.CENTER
            n.minWidth = dp(44)
            n.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
            row.addView(n)
            row.addView(UiKit.btn(ctx, prefs, ">", 0f, 36, 12f, feedback) { a.onNext() }.also { it.minWidth = dp(40) })
        }
        val sp = View(ctx)
        sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        row.addView(sp)
        val u = a.onUndo
        if (u != null) row.addView(UiKit.btn(ctx, prefs, "\u21B6 Undo", 0f, 36, 12f, feedback) { u() }.also { it.setPadding(dp(10), 0, dp(10), 0) })
        if (canStep) row.addView(primary("Replace this") { a.onReplace() })
        return row
    }

    /** The window. [tab] = 0 Before, 1 After, 2 Changes (sanitized). Throws only when nothing could be built (caller = old inline preview). */
    fun window(snap: ReplaceFloat.Snap, tab: Int, a: Actions): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(6), dp(2), dp(6), dp(4))
        try { box.background = shape(JabraTokens.cardFill(prefs, false), JabraTokens.cardStroke(prefs, true, false), JabraTokens.R_MD) } catch (_: Throwable) { box.setBackgroundColor(Color.WHITE) }

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.tag = "rf_header"   // F3: the drag listener goes on this view
        head.addView(grip())
        val title = tv(JabraTokens.T_BTN, ink(), true)
        title.text = "Preview"
        head.addView(title)
        val warn = snap.err && a.notice.isEmpty()
        head.addView(pill(if (a.notice.isNotEmpty()) a.notice else snap.count, warn))
        val sp = View(ctx)
        sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        head.addView(sp)
        val mn = a.onMinimize
        if (mn != null) head.addView(iconBtn("\u2013", "Minimize") { mn() })
        val cl = a.onClose
        if (cl != null) head.addView(iconBtn("\u2715", "Close") { cl() })
        box.addView(head)

        val simple = mn != null   // v0.39.6: the FLOATING window is the simple one (no tabs, 2 rows, one line per match); the inline test card keeps the tabs
        if (!simple) box.addView(tabs(tab, a))
        box.addView(body(snap, tab, simple, a.onPick))
        val f = footer(snap, a)
        if (f != null) box.addView(f)
        return box
    }

    /** The minimized bubble: 46 dp round, the match count inside (99+ above 99, ! on an error). */
    fun bubble(snap: ReplaceFloat.Snap): View {
        val t = TextView(ctx)
        t.text = ReplaceFloat.bubbleText(snap.err, snap.total)
        t.textSize = 14f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.maxLines = 1
        t.setTextColor(Color.WHITE)
        t.contentDescription = "Preview: " + snap.count
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (snap.err) JabraTokens.WARN else JabraTokens.ACCENT)
        try { g.setStroke(dp(1).coerceAtLeast(1), JabraTokens.cardStroke(prefs, true, false)) } catch (_: Throwable) { }
        t.background = g
        t.layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        return t
    }

    companion object {
        const val ROW_DP = 24
    }
}
