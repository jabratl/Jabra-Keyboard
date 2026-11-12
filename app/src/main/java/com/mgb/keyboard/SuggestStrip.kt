package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.mgb.keyboard.typing.SuggestPlan

/**
 * v0.10.1 (G2) suggestion row, same idea as the reference keyboard video: it sits at the TOP of the keyboard (replaces the toolbar row):
 *   [tools icon] [suggestion 1] [word you typed + "..." dots] [suggestion 2] [mic]
 * - tap a word = it replaces the word you are typing (+ space); tap the centre = keep your word + space.
 * - hold a word (0.35 s) or pull it down = a 3-column grid with up to 18 more words covers the keys; slide the finger, release on a word = pick it (release outside = nothing).
 * - tools icon = the toolbar row (the toolbar's first key brings the suggestions back); mic = voice typing.
 * Built in code, theme colours, fixed 40 dp row. Every call is in try/catch.
 */
class SuggestStrip(
    private val ctx: Context,
    private val prefs: JabraPrefs,
    private val onPick: (String) -> Unit,
    onTools: () -> Unit,
    onMic: () -> Unit
) {
    val view: LinearLayout = LinearLayout(ctx)
    private val slots = ArrayList<TextView>()
    private var enabled = true
    private var actOn = false                      // v0.10.23 (W9): the 3 slots show selection quick actions (tap = onAct(slot index), no grid)
    private var onAct: ((Int) -> Unit)? = null
    private var grid: List<String> = emptyList()
    private val d = ctx.resources.displayMetrics.density
    private val col: Int = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
    private val h = Handler(Looper.getMainLooper())

    // grid popup state
    private var pw: PopupWindow? = null
    private var cells: List<TextView> = emptyList()
    private var gl = 0f; private var gt = 0f; private var gcw = 1f; private var gch = 1f
    private var sel = -1

    private class ToolsIcon(private val c: Int) : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { p.color = c; p.alpha = 200 }
        override fun draw(canvas: Canvas) {
            val r = bounds
            val s = minOf(r.width(), r.height()) * 0.17f
            val g = s * 0.45f
            val cx = r.exactCenterX(); val cy = r.exactCenterY()
            for (i in 0..1) for (j in 0..1) {
                val x = cx + (if (i == 0) -s - g / 2f else g / 2f)
                val y = cy + (if (j == 0) -s - g / 2f else g / 2f)
                canvas.drawRoundRect(RectF(x, y, x + s, y + s), s * 0.25f, s * 0.25f, p)
            }
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private inner class DotsMark : Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        init { p.color = col; p.alpha = 190 }
        override fun draw(canvas: Canvas) {
            if (!enabled || grid.isEmpty() || slots.size < 2 || slots[1].text.isNullOrEmpty()) return
            val r = bounds
            val rad = 1.6f * d
            val cy = r.bottom - 4f * d
            for (i in -1..1) canvas.drawCircle(r.exactCenterX() + i * rad * 3.2f, cy, rad, p)
        }
        override fun setAlpha(alpha: Int) { }
        override fun setColorFilter(cf: ColorFilter?) { }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun pressBg(): StateListDrawable {
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable((col and 0x00FFFFFF) or 0x30000000))
        s.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        return s
    }

    private fun iconBtn(name: String, label: String, custom: Drawable?, act: () -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0; b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.background = pressBg()
        b.setTextColor(col)
        b.layoutParams = LinearLayout.LayoutParams((44f * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
        if (custom != null) { b.text = ""; b.foreground = custom; b.contentDescription = label }
        else try { JabraIcons.apply(b, name, prefs.bool("icons_on", true), col) } catch (_: Throwable) { }
        b.setOnClickListener { try { act() } catch (_: Throwable) { } }
        return b
    }

    init {
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (40f * d).toInt())
        view.addView(iconBtn("", "Tools", ToolsIcon(col)) { onTools() })
        for (i in 0 until SuggestPlan.SLOTS) {
            if (i > 0) {
                val dv = View(ctx)
                dv.setBackgroundColor((col and 0x00FFFFFF) or 0x40000000)
                dv.layoutParams = LinearLayout.LayoutParams(maxOf(1, d.toInt()), (22f * d).toInt())
                view.addView(dv)
            }
            val t = TextView(ctx)
            t.gravity = Gravity.CENTER
            t.textSize = 16f
            t.setTextColor(col)
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.setPadding((4f * d).toInt(), 0, (4f * d).toInt(), 0)
            t.background = pressBg()
            if (i == 1) t.foreground = DotsMark()
            t.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            attachTouch(t)
            slots.add(t)
            view.addView(t)
        }
        view.addView(iconBtn("mic", "Mic", null) { onMic() })
    }

    // ---------- touch: tap = pick, hold / pull down = grid, slide + release = pick from grid ----------

    private fun attachTouch(t: TextView) {
        var downX = 0f; var downY = 0f
        var expanded = false
        val expand = Runnable { try { expanded = openGrid(t) } catch (_: Throwable) { expanded = false } }
        t.setOnTouchListener { v, ev ->
            try {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = ev.rawX; downY = ev.rawY; expanded = false
                        v.isPressed = true
                        if (enabled && grid.isNotEmpty()) h.postDelayed(expand, SuggestPlan.LONG_MS)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (expanded) {
                            val n = SuggestPlan.gridIndex(ev.rawX, ev.rawY, gl, gt, gcw, gch, cells.size)
                            if (n != sel) { sel = n; mark() }
                        } else {
                            if (ev.rawY - downY > 12f * d && enabled && grid.isNotEmpty()) { h.removeCallbacks(expand); expand.run() }
                            else if (Math.abs(ev.rawX - downX) > 24f * d) { h.removeCallbacks(expand); v.isPressed = false }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        h.removeCallbacks(expand)
                        v.isPressed = false
                        if (expanded) {
                            val i = sel
                            val w = if (i >= 0 && i < grid.size) grid[i] else ""
                            closeGrid(); expanded = false
                            if (w.isNotEmpty()) onPick(w)
                        } else if (Math.abs(ev.rawX - downX) <= 24f * d && Math.abs(ev.rawY - downY) <= 24f * d) {
                            val w = t.text?.toString() ?: ""
                            if (actOn) { if (w.isNotEmpty()) onAct?.invoke(slots.indexOf(t)) }   // v0.10.23 (W9)
                            else if (enabled && w.isNotEmpty()) onPick(w)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        h.removeCallbacks(expand); v.isPressed = false
                        if (expanded) { closeGrid(); expanded = false }
                        true
                    }
                    else -> true
                }
            } catch (_: Throwable) { closeGrid(); false }
        }
    }

    private fun mark() {
        for (i in cells.indices) {
            val c = cells[i]
            if (i == sel) { c.setBackgroundColor(Color.parseColor("#2271B1")); c.setTextColor(Color.WHITE) }
            else { c.setBackgroundColor(Color.TRANSPARENT); c.setTextColor(col) }
        }
    }

    private fun closeGrid() {
        try { pw?.dismiss() } catch (_: Throwable) { }
        pw = null; cells = emptyList(); sel = -1
    }

    /** Covers the keys with the 3-column grid (top = top of this row). Returns true when it is on screen. */
    private fun openGrid(anchor: View): Boolean {
        closeGrid()
        val words = grid
        if (words.isEmpty() || !view.isAttachedToWindow) return false
        val loc = IntArray(2); view.getLocationOnScreen(loc)
        val w = view.width
        val rows = SuggestPlan.gridRows(words.size)
        val parentH = (view.parent as? View)?.height ?: (300f * d).toInt()
        val ch = (parentH / SuggestPlan.GRID_ROWS_MAX).coerceIn((36f * d).toInt(), (56f * d).toInt())
        val cw = w / SuggestPlan.COLS
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val shape = GradientDrawable()
        shape.setColor(try { JabraThemes.cur(prefs).bg } catch (_: Throwable) { Color.parseColor("#DDDDDD") })
        shape.cornerRadius = 8f * d
        shape.setStroke(maxOf(1, d.toInt()), (col and 0x00FFFFFF) or 0x60000000)
        box.background = shape
        val list = ArrayList<TextView>()
        for (r in 0 until rows) {
            val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until SuggestPlan.COLS) {
                val i = r * SuggestPlan.COLS + c
                val tv = TextView(ctx)
                tv.gravity = Gravity.CENTER; tv.textSize = 16f; tv.maxLines = 1; tv.ellipsize = TextUtils.TruncateAt.END
                tv.setTextColor(col)
                tv.text = if (i < words.size) words[i] else ""
                tv.layoutParams = LinearLayout.LayoutParams(cw, ch)
                row.addView(tv)
                if (i < words.size) list.add(tv)
            }
            box.addView(row)
        }
        cells = list
        val p = PopupWindow(box, cw * SuggestPlan.COLS, ch * rows)
        p.isTouchable = false; p.isFocusable = false; p.isClippingEnabled = false
        ToolTip.inScreen(p)   // v0.10.4: screen coordinates (same fix as the toolbar tip)
        pw = p
        gl = loc[0].toFloat(); gt = loc[1].toFloat(); gcw = cw.toFloat(); gch = ch.toFloat(); sel = -1
        p.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0], loc[1])
        try { anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) { }
        return true
    }

    /** Show the 3 strip texts + the grid words. [on] false = blank, taps ignored (password / email / URL / number fields). */
    fun set(words: List<String>, more: List<String> = emptyList(), on: Boolean = true) {
        try {
            enabled = on
            actOn = false; onAct = null   // v0.10.23 (W9): back to word suggestions
            grid = if (on) more else emptyList()
            val l = SuggestPlan.slots(if (on) words else emptyList())
            for (i in slots.indices) { val w = l[i]; if (slots[i].text.toString() != w) slots[i].text = w }
            slots.getOrNull(1)?.invalidate()
        } catch (_: Throwable) { }
    }

    /** v0.10.23 (W9): selection quick actions: [labels] (max 3) fill the slots, a tap calls [on] with the slot number. No grid. Any later set() brings the word suggestions back. */
    fun setActions(labels: List<String>, on: (Int) -> Unit) {
        try {
            enabled = true; actOn = true; onAct = on; grid = emptyList()
            for (i in slots.indices) { val w = labels.getOrNull(i) ?: ""; if (slots[i].text.toString() != w) slots[i].text = w }
            slots.getOrNull(1)?.invalidate()
        } catch (_: Throwable) { }
    }
}
