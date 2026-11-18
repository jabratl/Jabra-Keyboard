package com.mgb.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.PopupWindow
import com.mgb.keyboard.typing.ReplaceFloat

/**
 * v0.39.2 (FLOAT, step F3): the FLOATING HOST of the Find & Replace preview (plan: PLAN_RP_FLOAT.txt). A PopupWindow of the keyboard (same idea as PeekPopup.kt):
 * NO new permission ("draw over other apps" is NOT used). It is touchable but NOT focusable (the app's text field keeps the focus).
 * - open: ReplaceFloatView.window() (drag by the header only, minimize and close buttons); mini: round bubble (tap = open, drag = move); closed: nothing.
 * - show() on every refresh of the panel = the content is SWAPPED inside the same window (no re-create, no flicker). A new window is made only when the state changes.
 * - Position: pref rp_float_x / rp_float_y (numbers, -1 = none) kept on screen by ReplaceFloat.clamp / place (rotation, other phone = fallback place).
 *   First place = just above the keyboard. Prefs: rp_float_state (0 open, 1 mini, 2 closed), rp_float_tab (from ReplaceFloatView). No text is ever stored.
 * - UNKNOWN (needs the phone): how far above the keyboard Android lets the popup go (over the app's text) and whether it can be dragged there.
 *   If it cannot, the window simply stays where the system allows it; the keyboard itself is never affected.
 * show() returns false when the window could not be shown: the caller then uses the old inline preview (FALLBACK).
 * Delete this file + the hooks marked "v0.39.2" / "v0.39.3" = back to v0.39.1.
 */
class ReplaceFloatWin(private val ctx: Context, private val prefs: JabraPrefs, private val feedback: (View) -> Unit) {

    /** Called after a state change (minimize / close / open): the panel redraws itself. */
    var onChange: (() -> Unit)? = null

    /** v0.39.4: called when the window cannot be shown or ends up off screen (reason = short text). The panel then shows the reason + the old inline preview. */
    var onFail: ((String) -> Unit)? = null
    var lastError: String = ""
    private var offX = 0   // v0.39.4: measured difference between the place we ask for and the place the system really uses (coordinate system of the popup)
    private var offY = 0

    private var pw: PopupWindow? = null
    private var box: FrameLayout? = null
    private var shownState = -1
    private var curX = 0
    private var curY = 0
    private var winW = 0
    private var winH = 0

    companion object {
        /** The window of the open Replace panel (one at most). MgbIme calls dismissLive() when the panel is left or the keyboard hides. */
        @JvmStatic var live: ReplaceFloatWin? = null
        @JvmStatic fun dismissLive() { try { live?.hide() } catch (_: Throwable) { } }
    }

    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)

    fun state(): Int = ReplaceFloat.sanitizeState(prefs.int("rp_float_state", ReplaceFloat.OPEN))

    fun setState(s: Int) {
        prefs.setInt("rp_float_state", ReplaceFloat.sanitizeState(s))
        try { onChange?.invoke() } catch (_: Throwable) { }
    }

    fun isShowing(): Boolean = pw != null

    fun hide() {
        try { pw?.dismiss() } catch (_: Throwable) { }
        pw = null; box = null; shownState = -1
    }

    private fun actionsFor(a: ReplaceFloatView.Actions): ReplaceFloatView.Actions =
        ReplaceFloatView.Actions(a.onTab, a.onPrev, a.onNext, a.onReplace, a.onUndo, { setState(ReplaceFloat.MINI) }, null, a.notice, a.onPick)   // v0.39.6: no close button (the Float chip closes / opens)

    private fun content(snap: ReplaceFloat.Snap, tab: Int, a: ReplaceFloatView.Actions, st: Int, w: Int): View {
        val v = ReplaceFloatView(ctx, prefs, feedback)
        return if (st == ReplaceFloat.MINI) {
            val b = v.bubble(snap)
            b.layoutParams = FrameLayout.LayoutParams(dp(46), dp(46))
            b
        } else {
            val c = v.window(snap, tab, actionsFor(a))
            c.layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            c
        }
    }

    private fun bounds(): IntArray {
        val dm = ctx.resources.displayMetrics
        return intArrayOf(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun moveTo(x: Int, y: Int) {
        val b = bounds()
        val h = box?.height?.takeIf { it > 0 } ?: winH
        val c = ReplaceFloat.clamp(x, y, winW, h, b[0], b[1], b[2], b[3], dp(6))
        curX = c[0]; curY = c[1]
        try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { }
    }

    /** Drag on [target]; a short touch (under the touch slop) is a tap. */
    private fun attachDrag(target: View, onTap: (() -> Unit)?) {
        val slop = try { ViewConfiguration.get(ctx).scaledTouchSlop } catch (_: Throwable) { dp(8) }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        target.setOnTouchListener { _, e ->
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = curX; startY = curY; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!moved && (dx * dx + dy * dy) > slop * slop) { moved = true; box?.alpha = 0.85f }   // v0.39.6: a little see-through while dragging
                        if (moved) moveTo(startX + dx.toInt(), startY + dy.toInt())
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        box?.alpha = 1f
                        if (moved && shownState == ReplaceFloat.MINI) snapToEdge()   // v0.39.6: the bubble slides to the nearest side
                        else if (moved) { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) }
                        else { try { feedback(target) } catch (_: Throwable) { }; onTap?.invoke() }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> { box?.alpha = 1f; true }
                    else -> false
                }
            } catch (_: Throwable) { false }
        }
    }

    /** v0.39.6: after the bubble is released it slides (180 ms) to the left or right edge, whichever is nearer, and the place is saved. */
    private fun snapToEdge() {
        try {
            val b = bounds()
            val m = dp(10)
            val tx = if (curX + winW / 2 < (b[0] + b[2]) / 2) b[0] + m else b[2] - winW - m
            val va = ValueAnimator.ofInt(curX, tx)
            va.duration = 180
            va.addUpdateListener { try { moveTo(it.animatedValue as Int, curY) } catch (_: Throwable) { } }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { try { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) } catch (_: Throwable) { } }
            })
            va.start()
        } catch (_: Throwable) { try { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) } catch (_: Throwable) { } }
    }

    private fun wire(c: View, st: Int) {
        if (st == ReplaceFloat.MINI) attachDrag(c) { setState(ReplaceFloat.OPEN) }
        else {
            val h = c.findViewWithTag<View>("rf_header")
            if (h != null) attachDrag(h, null)
        }
    }

    /**
     * Shows or updates the window. true = nothing to do or it is on screen; false = it could not be shown (the caller draws the old inline preview).
     * State closed = the window is removed and true is returned.
     */
    fun show(anchor: View, snap: ReplaceFloat.Snap, tab: Int, a: ReplaceFloatView.Actions): Boolean {
        val st = state()
        if (st == ReplaceFloat.CLOSED) { hide(); return true }
        try {
            if (anchor.windowToken == null) return false
            live = this
            val b = bounds()
            val margin = dp(10)
            val w = if (st == ReplaceFloat.MINI) dp(46) else (b[2] - 2 * margin).coerceAtLeast(dp(200))
            val c = content(snap, tab, a, st, w)
            val win = pw
            val bx = box
            if (win != null && bx != null && shownState == st) {   // same state: swap the content, keep the window (no flicker)
                bx.removeAllViews()
                bx.addView(c)
                wire(c, st)
                return true
            }
            hide()
            c.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val h = c.measuredHeight
            winW = w; winH = h
            val rl = IntArray(2); anchor.rootView.getLocationOnScreen(rl)
            val sx = prefs.int("rp_float_x", -1)
            val sy = prefs.int("rp_float_y", -1)
            val pos: IntArray = if (sx < 0 || sy < 0) {   // first time: just above the keyboard (bubble at the right edge)
                val x = if (st == ReplaceFloat.MINI) b[2] - w - margin else margin
                ReplaceFloat.clamp(x, rl[1] - h - dp(8), w, h, b[0], b[1], b[2], b[3], dp(6))
            } else ReplaceFloat.place(sx, sy, w, h, b[0], b[1], b[2], b[3], dp(6))
            curX = pos[0]; curY = pos[1]
            val f = FrameLayout(ctx)
            f.addView(c)
            val p = PopupWindow(f, w, ViewGroup.LayoutParams.WRAP_CONTENT, false)
            p.isTouchable = true
            p.isFocusable = false   // the app's text field keeps the focus
            try { p.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED } catch (_: Throwable) { }
            try { p.elevation = 0f } catch (_: Throwable) { }   // no shadow: the 1 dp border does the separation
            ToolTip.inScreen(p)
            offX = 0; offY = 0
            p.showAtLocation(anchor, Gravity.NO_GRAVITY, curX, curY)
            pw = p; box = f; shownState = st
            wire(c, st)
            try {   // v0.39.6: soft open (fade + small zoom from the top-left corner, so the measured place does not change)
                f.pivotX = 0f; f.pivotY = 0f
                f.alpha = 0f; f.scaleX = 0.96f; f.scaleY = 0.96f
                f.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
            } catch (_: Throwable) { f.alpha = 1f; f.scaleX = 1f; f.scaleY = 1f }
            f.post { calibrate(f) }
            return true
        } catch (e: Throwable) {
            lastError = e.javaClass.simpleName + (if (e.message != null) ": " + e.message else "")
            hide()
            return false
        }
    }

    /**
     * v0.39.4: the popup may use another coordinate system than we assumed (relative to the keyboard window instead of the screen), which put it OFF screen.
     * After showing, the real screen place is measured; the difference is remembered (offX / offY) and the window is moved to the wanted place.
     * A second look 150 ms later: still not where wanted = onFail with the numbers (shown in the Replace note line).
     */
    private fun calibrate(f: View) {
        try {
            if (pw == null) return
            val l = IntArray(2); f.getLocationOnScreen(l)
            val dx = l[0] - curX
            val dy = l[1] - curY
            if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
                offX += dx; offY += dy
                try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { }
            }
            f.postDelayed({
                try {
                    if (pw == null) return@postDelayed
                    val l2 = IntArray(2); f.getLocationOnScreen(l2)
                    val b = bounds()
                    val bad = Math.abs(l2[0] - curX) > dp(12) || Math.abs(l2[1] - curY) > dp(12) || l2[1] > b[3] - dp(10) || l2[1] + dp(10) < 0
                    if (bad) { lastError = "off screen (want " + curX + "," + curY + " got " + l2[0] + "," + l2[1] + ")"; onFail?.invoke(lastError) }
                } catch (_: Throwable) { }
            }, 150)
        } catch (_: Throwable) { }
    }
}
