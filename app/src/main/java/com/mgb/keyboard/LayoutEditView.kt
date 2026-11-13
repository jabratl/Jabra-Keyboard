package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import com.mgb.keyboard.layout.KeyDef
import com.mgb.keyboard.layout.LayoutDef
import com.mgb.keyboard.layout.LayoutDrag
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
    /** v0.17.10 (K7): keys that belong to a warning get the orange (warn) outline; the selected key keeps the accent outline. */
    var warnIds: Set<String> = emptySet()
        set(v) { field = v; invalidate() }

    /** v0.17.16 (K11a): a key was dropped: (dragged key id, target key id, LayoutDrag.SWAP / BEFORE / AFTER). The page applies it (and can refuse). */
    var onDrop: ((String, String, Int) -> Unit)? = null

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

    // v0.17.16 (K11a) hold a key, drag it, drop it on another key (swap / insert before / insert after)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var downId = ""
    private var dragging = false
    private var dragId = ""
    private var dragX = 0f
    private var dragY = 0f
    private var rawX = 0f
    private var rawY = 0f
    private var targetId = ""
    private var targetMode = LayoutDrag.SWAP
    private var ghostW = 0f
    private var ghostH = 0f
    private var ghostLabel = ""
    private val longPress = Runnable { startDrag() }
    private val scrollTick = object : Runnable {
        override fun run() {
            if (!dragging) return
            try { scrollStep() } catch (_: Throwable) { }
            uiHandler.postDelayed(this, 16L)
        }
    }

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
                    if (dragging && k.id == dragId) { ghostW = rect.width(); ghostH = rect.height(); ghostLabel = LayoutEditPlan.shownLabel(k, shift) }
                    if (k.kind == KeyDef.SPACER) {
                        line.style = Paint.Style.STROKE; line.strokeWidth = (if (sel) 2f else 1f) * d
                        line.color = if (sel) acc else edge
                        line.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
                        c.drawRoundRect(rect, 6f * d, 6f * d, line)
                        line.pathEffect = null
                        if (dragging) dropMarks(c, k.id, acc)
                        continue
                    }
                    fill.style = Paint.Style.FILL
                    fill.color = if (k.kind == KeyDef.ACTION) fnFill else keyFill
                    c.drawRoundRect(rect, 6f * d, 6f * d, fill)
                    val wn = !sel && warnIds.contains(k.id)
                    line.style = Paint.Style.STROKE; line.strokeWidth = (if (sel) 2.5f else if (wn) 2f else 1f) * d
                    line.color = if (sel) acc else if (wn) AppUi.warn(context) else edge
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
                    if (dragging) dropMarks(c, k.id, acc)
                }
                y += heights[i] + gap
            }
            if (dragging) drawGhost(c, acc)
        } catch (_: Throwable) { }
    }

    /** v0.17.16: while dragging, the picked-up key gets a dashed outline; the target gets a bar on its left / right edge (insert) or a thick outline (swap). Uses [rect] of the key just drawn. */
    private fun dropMarks(c: Canvas, id: String, acc: Int) {
        if (id == dragId) {
            line.style = Paint.Style.STROKE; line.strokeWidth = 2f * d; line.color = acc
            line.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
            c.drawRoundRect(rect, 6f * d, 6f * d, line)
            line.pathEffect = null
        } else if (id == targetId) {
            line.color = acc
            if (targetMode == LayoutDrag.SWAP) {
                line.style = Paint.Style.STROKE; line.strokeWidth = 3.5f * d
                c.drawRoundRect(rect, 6f * d, 6f * d, line)
            } else {
                fill.style = Paint.Style.FILL; fill.color = acc
                val x = if (targetMode == LayoutDrag.BEFORE) rect.left - gap / 2f else rect.right + gap / 2f
                c.drawRect(x - 2f * d, rect.top, x + 2f * d, rect.bottom, fill)
            }
        }
    }

    private fun drawGhost(c: Canvas, acc: Int) {
        val w = if (ghostW > 0f) ghostW else 44f * d
        val h = if (ghostH > 0f) ghostH else baseH
        rect.set(dragX - w / 2f, dragY - h / 2f, dragX + w / 2f, dragY + h / 2f)
        fill.style = Paint.Style.FILL; fill.color = acc; fill.alpha = 190
        c.drawRoundRect(rect, 6f * d, 6f * d, fill)
        fill.alpha = 255
        if (ghostLabel.isNotEmpty()) {
            txt.color = 0xFFFFFFFF.toInt(); txt.textSize = 16f * sp
            c.drawText(ghostLabel, rect.centerX(), rect.centerY() - (txt.ascent() + txt.descent()) / 2f, txt)
        }
    }

    private fun keyAt(x: Float, y: Float): String {
        val rs = rows()
        val ri = LayoutEditPlan.rowAt(heights, gap, y - pad)
        if (ri !in rs.indices) return ""
        val ki = LayoutEditPlan.hit(LayoutEditPlan.edges(rs[ri], width - pad * 2, gap), x - pad)
        return if (ki >= 0) rs[ri].keys[ki].id else ""
    }

    private fun startDrag() {
        try {
            val m = model
            if (downId.isEmpty() || m == null) return
            dragging = true
            dragId = downId
            targetId = ""
            selectedId = downId
            onSelect?.invoke(downId)
            try { parent?.requestDisallowInterceptTouchEvent(true) } catch (_: Throwable) { }
            try { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) { }
            syncLocal()
            updateTarget()
            uiHandler.postDelayed(scrollTick, 16L)
            invalidate()
        } catch (_: Throwable) { endDrag() }
    }

    private fun syncLocal() {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        dragX = rawX - loc[0]
        dragY = rawY - loc[1]
    }

    private fun updateTarget() {
        val m = model
        var nt = ""
        var nm = LayoutDrag.SWAP
        val rs = rows()
        val ri = LayoutEditPlan.rowAt(heights, gap, dragY - pad)
        if (m != null && ri in rs.indices) {
            val ed = LayoutEditPlan.edges(rs[ri], width - pad * 2, gap)
            val ki = LayoutEditPlan.hit(ed, dragX - pad)
            if (ki >= 0) {
                val id = rs[ri].keys[ki].id
                if (id != dragId) {
                    val md = LayoutDrag.zone(pad + ed[ki * 2], pad + ed[ki * 2 + 1], dragX)
                    if (LayoutDrag.canDrop(m, dragId, id, md)) { nt = id; nm = md }
                }
            }
        }
        if (nt != targetId || nm != targetMode) {
            targetId = nt; targetMode = nm
            if (nt.isNotEmpty()) try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Throwable) { }
        }
    }

    /** Auto-scroll the page (ScrollView around the editor) while the finger is near its top / bottom edge. */
    private fun scrollStep() {
        var p = parent
        var sv: ScrollView? = null
        while (p != null) { if (p is ScrollView) { sv = p; break }; p = p.parent }
        val s = sv ?: return
        val loc = IntArray(2)
        s.getLocationOnScreen(loc)
        val dy = LayoutDrag.autoScrollDy(rawY, loc[1].toFloat(), (loc[1] + s.height).toFloat(), 56f * d, 18f * d)
        if (dy != 0) {
            s.scrollBy(0, dy)
            syncLocal()
            updateTarget()
            invalidate()
        }
    }

    private fun endDrag() {
        uiHandler.removeCallbacks(longPress)
        uiHandler.removeCallbacks(scrollTick)
        dragging = false; dragId = ""; targetId = ""; downId = ""
        try { parent?.requestDisallowInterceptTouchEvent(false) } catch (_: Throwable) { }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        uiHandler.removeCallbacks(longPress)
        uiHandler.removeCallbacks(scrollTick)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        try {
            rawX = e.rawX; rawY = e.rawY
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x; downY = e.y
                    downId = keyAt(e.x, e.y)
                    uiHandler.removeCallbacks(longPress)
                    if (downId.isNotEmpty() && onDrop != null) uiHandler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        syncLocal()
                        updateTarget()
                        invalidate()
                    } else if (Math.abs(e.x - downX) > slop || Math.abs(e.y - downY) > slop) {
                        uiHandler.removeCallbacks(longPress)
                    }
                }
                MotionEvent.ACTION_CANCEL -> endDrag()
                MotionEvent.ACTION_UP -> {
                    uiHandler.removeCallbacks(longPress)
                    if (dragging) {
                        val id = dragId; val t = targetId; val md = targetMode
                        endDrag()
                        if (t.isNotEmpty()) onDrop?.invoke(id, t, md)
                    } else {
                        val id = keyAt(e.x, e.y)
                        if (id.isNotEmpty()) {
                            selectedId = id
                            onSelect?.invoke(id)
                            performClick()
                        }
                    }
                }
            }
        } catch (_: Throwable) { endDrag() }
        return true
    }

    override fun performClick(): Boolean { return super.performClick() }
}
