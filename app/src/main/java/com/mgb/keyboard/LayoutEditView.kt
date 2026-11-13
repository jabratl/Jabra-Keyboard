package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.typing.LayoutEditPlan

/**
 * v0.17.8 (K6a) the editable preview of ONE layer of the layout: draws the rows of the model on a canvas (ONE view, not 80 buttons, plan 6.10),
 * a tap selects a key (accent outline). Colours come from AppUi only (light / dark app). Pure geometry = typing/LayoutEditPlan (tested).
 */
class LayoutEditView(ctx: Context) : View(ctx) {
    var model: LayoutDef? = null
        set(v) { field = v; requestLayout(); invalidate() }
    var layer: String = "en"
        set(v) { field = v; requestLayout(); invalidate() }
    var shift: Boolean = false
        set(v) { field = v; invalidate() }
    var selectedId: String = ""
        set(v) { field = v; announce(); invalidate() }
    var onSelect: ((String) -> Unit)? = null

    private val d = ctx.resources.displayMetrics.density
    private val sp = ctx.resources.displayMetrics.scaledDensity
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val gap = 3f * d
    private val pad = 4f * d
    private val baseH = 44f * d
    private var heights = FloatArray(0)

    init {
        line.style = Paint.Style.STROKE
        txt.textAlign = Paint.Align.CENTER
        isClickable = true
        isFocusable = true
    }

    private fun rows() = try { model?.effectiveRows(layer) ?: emptyList() } catch (_: Throwable) { emptyList() }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val r = rows()
        heights = FloatArray(r.size) { baseH * r[it].heightPct / 100f }
        var h = pad * 2
        for (x in heights) h += x
        if (r.size > 1) h += gap * (r.size - 1)
        setMeasuredDimension(MeasureSpec.getSize(wSpec), h.toInt().coerceAtLeast((60 * d).toInt()))
    }

    private fun announce() {
        try { contentDescription = LayoutEditPlan.describe(model, layer, selectedId, true).ifEmpty { "Keyboard layout, tap a key to select it" } } catch (_: Throwable) { }
    }

    override fun onDraw(c: Canvas) {
        try {
            val dark = AppUi.isDark(context)
            val keyFill = if (dark) 0xFF33353A.toInt() else 0xFFFFFFFF.toInt()
            val fnFill = if (dark) 0xFF2A2C30.toInt() else 0xFFE4E4E4.toInt()
            val edge = AppUi.dim(context)
            val tcol = AppUi.text(context)
            val acc = AppUi.accent(context)
            var y = pad
            val rs = rows()
            for (i in rs.indices) {
                val row = rs[i]
                val e = LayoutEditPlan.edges(row, width - pad * 2, gap)
                for (j in row.keys.indices) {
                    val k = row.keys[j]
                    rect.set(pad + e[j * 2], y, pad + e[j * 2 + 1], y + heights[i])
                    val sel = k.id == selectedId
                    if (k.kind == KeyDef.SPACER) {
                        line.style = Paint.Style.STROKE; line.strokeWidth = (if (sel) 2f else 1f) * d
                        line.color = if (sel) acc else edge
                        line.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
                        c.drawRoundRect(rect, 6f * d, 6f * d, line)
                        line.pathEffect = null
                        continue
                    }
                    fill.style = Paint.Style.FILL
                    fill.color = if (k.kind == KeyDef.ACTION) fnFill else keyFill
                    c.drawRoundRect(rect, 6f * d, 6f * d, fill)
                    line.style = Paint.Style.STROKE; line.strokeWidth = (if (sel) 2.5f else 1f) * d
                    line.color = if (sel) acc else edge
                    c.drawRoundRect(rect, 6f * d, 6f * d, line)
                    val label = LayoutEditPlan.shownLabel(k, shift)
                    if (label.isNotEmpty()) {
                        txt.color = tcol
                        var size = 16f * sp
                        txt.textSize = size
                        val avail = rect.width() - 6f * d
                        val w = txt.measureText(label)
                        if (w > avail && w > 0f) { size = size * avail / w; txt.textSize = size.coerceAtLeast(7f * sp) }
                        c.drawText(label, rect.centerX(), rect.centerY() - (txt.ascent() + txt.descent()) / 2f, txt)
                    }
                    if (k.kind == KeyDef.CHAR && k.lp.isNotEmpty() && !k.lp.startsWith("@")) {   // tiny hint like the keyboard
                        txt.textSize = 9f * sp; txt.color = AppUi.dim(context)
                        val h = k.lp.take(2)
                        c.drawText(h, rect.right - 7f * d, rect.top + 11f * d, txt)
                    }
                }
                y += heights[i] + gap
            }
        } catch (_: Throwable) { }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        try {
            if (e.actionMasked == MotionEvent.ACTION_UP) {
                val rs = rows()
                val ri = LayoutEditPlan.rowAt(heights, gap, e.y - pad)
                if (ri in rs.indices) {
                    val ki = LayoutEditPlan.hit(LayoutEditPlan.edges(rs[ri], width - pad * 2, gap), e.x - pad)
                    if (ki >= 0) {
                        val id = rs[ri].keys[ki].id
                        selectedId = id
                        onSelect?.invoke(id)
                        performClick()
                    }
                }
            }
        } catch (_: Throwable) { }
        return true
    }

    override fun performClick(): Boolean { return super.performClick() }
}
