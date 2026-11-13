package com.mgb.keyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.widget.Button

/**
 * v0.17.2 (K2): the space bar when "Label slides while swiping" is ON. Same Button, same background / shape;
 * only the label (drawn in onDraw, AFTER the background) is moved sideways by labelDx px. No relayout, no allocation per move.
 * Any problem = the label is simply drawn in place.
 */
class SlideButton(ctx: Context) : Button(ctx) {
    private var anim: ValueAnimator? = null
    var labelDx: Float = 0f
        private set

    /** Finger is moving: put the label at px (no animation). */
    fun follow(px: Float) {
        try { stopAnim(); if (px != labelDx) { labelDx = px; invalidate() } } catch (_: Throwable) { }
    }

    /** Finger is up (or the swipe was cancelled): glide the label back to the middle. */
    fun release(animate: Boolean) {
        try {
            stopAnim()
            if (labelDx == 0f) return
            if (!animate) { labelDx = 0f; invalidate(); return }
            val a = ValueAnimator.ofFloat(labelDx, 0f)
            a.duration = 110L
            a.addUpdateListener { labelDx = it.animatedValue as Float; invalidate() }
            anim = a
            a.start()
        } catch (_: Throwable) { labelDx = 0f }
    }

    private fun stopAnim() { try { anim?.cancel() } catch (_: Throwable) { }; anim = null }

    override fun onDraw(canvas: Canvas) {
        val dx = labelDx
        if (dx == 0f) { super.onDraw(canvas); return }
        val saved = canvas.save()
        try { canvas.translate(dx, 0f); super.onDraw(canvas) } finally { canvas.restoreToCount(saved) }
    }

    override fun onDetachedFromWindow() {
        stopAnim(); labelDx = 0f
        super.onDetachedFromWindow()
    }
}
