package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import com.mgb.keyboard.layout.LpBarPlan

/**
 * v0.17.20 (L5a): long-press OPTIONS bar of custom keys (KeyDef.lpList). Hold a key: a bar with the options shows above it; slide the finger
 * over a cell (the cell under the finger turns blue), lift = that text is typed. Lift without moving = the first option (= the old single long-press).
 * ONE small custom View, built only when a long-press fires (nothing on the normal key press path). The window never takes touches: the finger
 * stays on the key, CustomRows passes the raw x to [move]. Any problem = no bar (the caller types the first option like before).
 */
object LpBar {
    private var pw: PopupWindow? = null
    private var bv: BarView? = null
    private var items: List<String> = emptyList()
    private var left = 0
    private var cell = 1

    /** Shows the bar above [anchor]. false = not shown (the caller falls back to the old single long-press). */
    fun show(anchor: View, opts: List<String>, prefs: JabraPrefs): Boolean {
        try {
            dismiss()
            if (opts.size < LpBarPlan.MIN_OPTIONS || anchor.width <= 0 || anchor.height <= 0) return false
            val dm = anchor.resources.displayMetrics
            val c = LpBarPlan.cell(anchor.width, dm.density, opts.size, dm.widthPixels)
            val h = Math.round(anchor.height * 1.1f).coerceAtLeast((44 * dm.density).toInt())
            val loc = IntArray(2)
            anchor.getLocationOnScreen(loc)
            val l = LpBarPlan.left(loc[0], anchor.width, c, opts.size, dm.widthPixels)
            val v = BarView(anchor.context)
            v.setup(opts.map { LpBarPlan.shown(it) }, c, prefs, dm.density, dm.scaledDensity)
            val np = PopupWindow(v, c * opts.size, h, false)
            np.isTouchable = false
            np.isFocusable = false
            try { ToolTip.inScreen(np) } catch (_: Throwable) { }
            val y = (loc[1] - h - (6 * dm.density).toInt()).coerceAtLeast(0)
            np.showAtLocation(anchor, Gravity.NO_GRAVITY, l, y)
            pw = np; bv = v; items = opts; left = l; cell = c
            return true
        } catch (_: Throwable) {
            dismiss()
            return false
        }
    }

    /** Finger moved: [rawX] = screen x. Highlights the cell under the finger. */
    fun move(rawX: Float) {
        try { bv?.select(LpBarPlan.indexAt(rawX, left, cell, items.size)) } catch (_: Throwable) { }
    }

    /** The option under the finger now (the first one when the finger never moved), null when no bar is showing. */
    fun picked(): String? = try { items.getOrNull(bv?.sel ?: 0) } catch (_: Throwable) { null }

    fun dismiss() {
        try { pw?.dismiss() } catch (_: Throwable) { }
        pw = null; bv = null; items = emptyList()
    }

    private class BarView(ctx: Context) : View(ctx) {
        var sel = 0
        private var texts: List<String> = emptyList()
        private var cellPx = 1
        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val selP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val selTextP = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        private var radius = 0f

        init {
            isClickable = false; isFocusable = false
            try { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO } catch (_: Throwable) { }
        }

        fun setup(t: List<String>, c: Int, prefs: JabraPrefs, d: Float, sd: Float) {
            texts = t; cellPx = c; sel = 0
            val th = try { JabraThemes.cur(prefs) } catch (_: Throwable) { null }
            val key: Int? = th?.key
            val fill = com.mgb.keyboard.typing.PopupStyle.fill(key)
            fillP.style = Paint.Style.FILL; fillP.color = fill
            strokeP.style = Paint.Style.STROKE; strokeP.strokeWidth = (1 * d).coerceAtLeast(1f); strokeP.color = com.mgb.keyboard.typing.PopupStyle.stroke(key, fill)
            selP.style = Paint.Style.FILL; selP.color = Color.parseColor("#2271B1")   // BLUE = selected (design rule)
            textP.textAlign = Paint.Align.CENTER; textP.color = com.mgb.keyboard.typing.PopupStyle.text(th?.text, fill); textP.textSize = 22f * sd
            selTextP.textAlign = Paint.Align.CENTER; selTextP.color = Color.WHITE; selTextP.textSize = 22f * sd
            radius = com.mgb.keyboard.typing.PopupStyle.radiusPx(d)
        }

        fun select(i: Int) { if (i != sel && i >= 0 && i < texts.size) { sel = i; invalidate() } }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            box.set(1f, 1f, w - 1f, h - 1f)
            c.drawRoundRect(box, radius, radius, fillP)
            if (sel >= 0 && sel < texts.size) {
                box.set(sel * cellPx + 3f, 3f, (sel + 1) * cellPx - 3f, h - 3f)
                c.drawRoundRect(box, radius, radius, selP)
            }
            box.set(1f, 1f, w - 1f, h - 1f)
            c.drawRoundRect(box, radius, radius, strokeP)
            for (i in texts.indices) {
                val p = if (i == sel) selTextP else textP
                val y = h / 2f - (p.descent() + p.ascent()) / 2f
                c.drawText(texts[i], i * cellPx + cellPx / 2f, y, p)
            }
        }
    }
}
