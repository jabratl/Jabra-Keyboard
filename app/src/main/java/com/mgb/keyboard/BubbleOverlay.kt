package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * v0.9.9 (plan V4): the key bubble drawn INSIDE the keyboard container (no PopupWindow, no window call).
 * One transparent full-size view on top of the keyboard. A press only stores text + position and invalidates the old and
 * the new bubble rectangle: no layout pass, no WindowManager call. It never takes touches (onTouchEvent = false, not clickable),
 * so the keys below get every event exactly as before. Look (fill / stroke / text colour / radius) is set by KeyPopup.
 */
class BubbleOverlay(ctx: Context) : View(ctx) {

    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var text = ""
    private var shown = false
    private var bl = 0
    private var bt = 0
    private var bw = 0
    private var bh = 0
    private var radius = 0f
    private var strokeW = 1f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        strokeP.style = Paint.Style.STROKE
        fillP.style = Paint.Style.FILL
        textP.textAlign = Paint.Align.CENTER
    }

    fun setLook(fill: Int, stroke: Int, strokeWidth: Float, textColor: Int, cornerRadius: Float) {
        fillP.color = fill
        strokeP.color = stroke
        strokeW = if (strokeWidth < 1f) 1f else strokeWidth
        strokeP.strokeWidth = strokeW
        textP.color = textColor
        radius = cornerRadius
        if (shown) invalidate(bl, bt, bl + bw, bt + bh)
    }

    /** Show [label] in a bubble of [w] x [h] px at [left] / [top] (container pixels). */
    fun show(label: String, left: Int, top: Int, w: Int, h: Int, textPx: Float) {
        if (shown) invalidate(bl, bt, bl + bw, bt + bh)   // old place
        text = label
        bl = left; bt = top; bw = w; bh = h
        textP.textSize = textPx
        shown = true
        invalidate(bl, bt, bl + bw, bt + bh)              // new place
    }

    fun hideNow() {
        if (!shown) return
        shown = false
        invalidate(bl, bt, bl + bw, bt + bh)
    }

    fun isShown2(): Boolean = shown

    override fun onDraw(canvas: Canvas) {
        if (!shown || bw <= 0 || bh <= 0) return
        val half = strokeW / 2f
        box.set(bl + half, bt + half, bl + bw - half, bt + bh - half)
        canvas.drawRoundRect(box, radius, radius, fillP)
        canvas.drawRoundRect(box, radius, radius, strokeP)
        val fm = textP.fontMetrics
        val cy = bt + bh / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, bl + bw / 2f, cy, textP)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}
