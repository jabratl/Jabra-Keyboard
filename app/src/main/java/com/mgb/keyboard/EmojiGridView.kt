package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller

/**
 * v0.8.1 (E2): ONE View that draws only the emoji rows that are on screen (no View per emoji).
 * onDraw allocates nothing (one Paint set, one RectF, text from the items list). Touch: drag + fling (OverScroller), tap = onPick.
 * Cells are flat (no button look); the tap highlight and the note colour come from setColors().
 */
class EmojiGridView(ctx: Context) : View(ctx) {

    private var items: List<String> = emptyList()
    private var emptyText = ""
    private var off = 0
    private var cols = 8
    private var pressed = -1
    private var dragging = false
    private var downY = 0f
    private var lastY = 0f

    private val density = ctx.resources.displayMetrics.density
    private val cellH = (44 * density).toInt()
    private val vc = ViewConfiguration.get(ctx)
    private val slop = vc.scaledTouchSlop
    private val scroller = OverScroller(ctx)
    private var vt: VelocityTracker? = null

    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var emojiDy = 0f
    private var noteDy = 0f

    /** Called on a tap: the emoji and this view (for host.feedback). */
    var onPick: ((String, View) -> Unit)? = null

    init {
        emojiPaint.textSize = 22f * density
        emojiPaint.textAlign = Paint.Align.CENTER
        notePaint.textSize = 12f * density
        notePaint.textAlign = Paint.Align.LEFT
        val fm = Paint.FontMetrics()
        emojiPaint.getFontMetrics(fm)
        emojiDy = (cellH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        notePaint.getFontMetrics(fm)
        noteDy = (cellH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        pressPaint.style = Paint.Style.FILL
    }

    fun setColors(press: Int, note: Int) {
        pressPaint.color = press
        notePaint.color = note
        invalidate()
    }

    fun setItems(list: List<String>, empty: String) {
        items = list
        emptyText = empty
        scroller.forceFinished(true)
        off = 0
        pressed = -1
        invalidate()
    }

    private fun maxOff(): Int {
        val rows = (items.size + cols - 1) / cols
        val m = rows * cellH - height
        return if (m > 0) m else 0
    }

    private fun clampOff(v: Int): Int {
        val m = maxOff()
        return if (v < 0) 0 else if (v > m) m else v
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val c = (w / (42 * density)).toInt()
        cols = if (c < 5) 5 else c
        off = clampOff(off)
    }

    override fun onDraw(c: Canvas) {
        val n = items.size
        if (n == 0) {
            if (emptyText.isNotEmpty()) c.drawText(emptyText, 8 * density, noteDy, notePaint)
            return
        }
        val cw = width.toFloat() / cols
        val firstRow = off / cellH
        var lastRow = (off + height) / cellH
        val maxRow = (n - 1) / cols
        if (lastRow > maxRow) lastRow = maxRow
        val pad = 2 * density
        val rad = 9 * density
        var r = firstRow
        while (r <= lastRow) {
            val top = (r * cellH - off).toFloat()
            var k = 0
            while (k < cols) {
                val i = r * cols + k
                if (i >= n) break
                if (i == pressed) {
                    rect.set(k * cw + pad, top + pad, (k + 1) * cw - pad, top + cellH - pad)
                    c.drawRoundRect(rect, rad, rad, pressPaint)
                }
                c.drawText(items[i], k * cw + cw / 2f, top + emojiDy, emojiPaint)
                k++
            }
            r++
        }
    }

    private fun hit(x: Float, y: Float): Int {
        if (items.isEmpty() || width <= 0) return -1
        val col = (x / (width.toFloat() / cols)).toInt()
        if (col < 0 || col >= cols) return -1
        val yy = y + off
        if (yy < 0f) return -1
        val i = (yy / cellH).toInt() * cols + col
        return if (i >= 0 && i < items.size) i else -1
    }

    private fun releaseVt() {
        try { vt?.recycle() } catch (_: Throwable) { }
        vt = null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                releaseVt()
                vt = VelocityTracker.obtain()
                vt?.addMovement(e)
                downY = e.y
                lastY = e.y
                dragging = false
                pressed = hit(e.x, e.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                vt?.addMovement(e)
                if (!dragging && Math.abs(e.y - downY) > slop) {
                    dragging = true
                    pressed = -1
                    lastY = e.y
                    invalidate()
                }
                if (dragging) {
                    off = clampOff(off - (e.y - lastY).toInt())
                    lastY = e.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                vt?.addMovement(e)
                if (dragging) {
                    val t = vt
                    if (t != null) {
                        t.computeCurrentVelocity(1000, vc.scaledMaximumFlingVelocity.toFloat())
                        val vy = t.yVelocity
                        if (Math.abs(vy) > vc.scaledMinimumFlingVelocity) {
                            scroller.fling(0, off, 0, (-vy).toInt(), 0, 0, 0, maxOff())
                            postInvalidateOnAnimation()
                        }
                    }
                } else {
                    val i = hit(e.x, e.y)
                    if (i >= 0 && i == pressed) {
                        try { onPick?.invoke(items[i], this) } catch (_: Throwable) { }
                    }
                }
                pressed = -1
                dragging = false
                releaseVt()
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                dragging = false
                releaseVt()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            off = clampOff(scroller.currY)
            postInvalidateOnAnimation()
        }
    }
}
