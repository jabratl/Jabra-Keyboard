package com.mgb.keyboard

import android.content.Context
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/**
 * v0.9.10.4: gap-tolerant key row.
 * The gap between keys is the key button's own margin, so a finger-down in the gap hits NO button and the tap was lost
 * (fast typing). Here a finger-down that lands in no key is moved to the NEAREST key of this row (same idea as Ridmik).
 * The same shift is kept for that finger until it lifts, so MOVE / UP stay consistent. A touch inside a key is untouched.
 */
class GapRow(ctx: Context) : LinearLayout(ctx) {

    private val shift = SparseArray<FloatArray>()   // pointerId -> {dx, dy}

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        val ev: MotionEvent = try { fix(e) } catch (_: Throwable) { e }
        val r = super.dispatchTouchEvent(ev)
        if (ev !== e) ev.recycle()
        try {
            when (e.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> shift.clear()
                MotionEvent.ACTION_POINTER_UP -> shift.remove(e.getPointerId(e.actionIndex))
            }
        } catch (_: Throwable) { }
        return r
    }

    private fun fix(e: MotionEvent): MotionEvent {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN) shift.clear()
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val i = e.actionIndex
            val s = snap(e.getX(i), e.getY(i))
            if (s != null) shift.put(e.getPointerId(i), s)
        }
        if (shift.size() == 0) return e
        val n = e.pointerCount
        val props = Array(n) { MotionEvent.PointerProperties() }
        val coords = Array(n) { MotionEvent.PointerCoords() }
        for (i in 0 until n) {
            e.getPointerProperties(i, props[i]); e.getPointerCoords(i, coords[i])
            val s = shift.get(props[i].id)
            if (s != null) { coords[i].x += s[0]; coords[i].y += s[1] }
        }
        return MotionEvent.obtain(e.downTime, e.eventTime, e.action, n, props, coords, e.metaState, e.buttonState,
            e.xPrecision, e.yPrecision, e.deviceId, e.edgeFlags, e.source, e.flags)
    }

    /** null = the point is inside a key (or there is no key) -> leave it. Else the shift that moves it just inside the nearest key. */
    private fun snap(x: Float, y: Float): FloatArray? {
        var best: View? = null
        var bestD = Float.MAX_VALUE
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != View.VISIBLE || !c.isEnabled || !c.isClickable) continue
            val dx = maxOf(c.left - x, 0f, x - c.right)
            val dy = maxOf(c.top - y, 0f, y - c.bottom)
            if (dx == 0f && dy == 0f) return null   // inside a key: normal touch
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = c }
        }
        val b = best ?: return null
        val nx = x.coerceIn(b.left + 1f, b.right - 1f)
        val ny = y.coerceIn(b.top + 1f, b.bottom - 1f)
        return floatArrayOf(nx - x, ny - y)
    }
}
