package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
    private val stem = Path()   // v0.17.44 (P1): Ridmik-style neck between the bubble and its key
    private var kl = 0
    private var kw = 0
    private var gp = 0

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        strokeP.style = Paint.Style.STROKE
        fillP.style = Paint.Style.FILL
        textP.textAlign = Paint.Align.CENTER
        try { textP.typeface = Typeface.DEFAULT_BOLD } catch (_: Throwable) { }   // v0.17.44 (P1): bold letter like Ridmik
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
    fun show(label: String, left: Int, top: Int, w: Int, h: Int, textPx: Float, keyLeft: Int = 0, keyW: Int = 0, gapPx: Int = 0) {
        if (shown) invalidate(bl, bt, bl + bw, bt + bh + gp)   // old place (+ stem)
        text = label
        bl = left; bt = top; bw = w; bh = h
        kl = keyLeft; kw = keyW; gp = if (keyW > 0 && gapPx > 0) gapPx else 0
        textP.textSize = textPx
        shown = true
        invalidate(bl, bt, bl + bw, bt + bh + gp)         // new place (+ stem)
    }

    fun hideNow() {
        if (!shown) return
        shown = false
        invalidate(bl, bt, bl + bw, bt + bh + gp)
    }

    fun isShown2(): Boolean = shown

    override fun onDraw(canvas: Canvas) {
        if (!shown || bw <= 0 || bh <= 0) return
        val half = strokeW / 2f
        box.set(bl + half, bt + half, bl + bw - half, bt + bh - half)
        canvas.drawRoundRect(box, radius, radius, fillP)
        canvas.drawRoundRect(box, radius, radius, strokeP)
        if (gp > 0) {   // v0.17.44 (P1): neck from the bubble bottom to the key top; covers the bottom stroke, only the 2 sides get a line
            val yb = (bt + bh).toFloat()
            val yTop = yb - strokeW - 0.5f
            val yKey = yb + gp
            val kc = (kl + kw / 2f).coerceIn(bl + radius, bl + bw - radius)
            val topHalf = Math.min(bw / 2f - radius, kw * 0.35f).coerceAtLeast(strokeW * 3f)
            val botHalf = (kw / 2f - strokeW * 3f).coerceAtLeast(topHalf)
            stem.reset()
            stem.moveTo(kc - topHalf, yTop); stem.lineTo(kc + topHalf, yTop)
            stem.lineTo(kl + kw / 2f + botHalf, yKey); stem.lineTo(kl + kw / 2f - botHalf, yKey); stem.close()
            canvas.drawPath(stem, fillP)
            canvas.drawLine(kc - topHalf, yb - strokeW, kl + kw / 2f - botHalf, yKey, strokeP)
            canvas.drawLine(kc + topHalf, yb - strokeW, kl + kw / 2f + botHalf, yKey, strokeP)
        }
        val fm = textP.fontMetrics
        val cy = bt + bh / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, bl + bw / 2f, cy, textP)
    }

    // v0.9.10.1 FIX: a plain View with MATCH_PARENT inside a WRAP_CONTENT FrameLayout measures to the FULL free height
    // (View.getDefaultSize returns the spec size for AT_MOST) -> the keyboard window grew to the whole screen after
    // ?123 > ABC (symbols panel has no overlay = normal, letters have it = full screen). Report height 0 unless the
    // parent gives an EXACT height: FrameLayout then re-measures this MATCH_PARENT child with the keyboard's real height.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean = false
}
