package com.mgb.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
 * v0.44.0: RESIZE. Hold (about 0.25 s) on one of the 4 corners (small diagonal marks) and drag = the window gets wider / narrower (pref rp_float_w, dp) and shows more / fewer
 * match rows (pref rp_float_rows, 1..8; 0 = default). The corner opposite to the one you hold stays where it is. Limits: width 260 dp .. screen - 20 dp (max 560 dp), height at most
 * about 80% of the screen. Without the hold a touch is a normal drag / tap, so rows and buttons are not confused with resizing. Double-tap on the header = back to the default
 * size, or (when already default) a bigger size. [reset] button in the header = default size AND default place. Nothing but numbers is stored.
 * v0.45.0: header gear = TOOLS row: [Lock] (no drag; resize / buttons work), [100% / 80% / 60%] see-through (pref rp_float_alpha), [A- / A+] row font (rp_float_font),
 * [Text] (pair list: real text rows). Edge snap: a drag released within 28 dp of the left / right / top edge slides to the edge. Swipe left / right inside the rows = next / previous match.
 * Prefs: rp_float_tools, rp_float_lock, rp_float_alpha, rp_float_font, rp_float_ctx (numbers / switches only).
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
    private var lastSnap: ReplaceFloat.Snap? = null   // v0.44.0: kept so a resize can redraw the rows at once
    private var lastTab = 0
    private var lastA: ReplaceFloatView.Actions? = null
    private var rowsNow = 2
    private var resizeActive = false

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
        ReplaceFloatView.Actions(a.onTab, a.onPrev, a.onNext, a.onReplace, a.onUndo, { setState(ReplaceFloat.MINI) }, null, a.notice, a.onPick, a.onReplaceAll, a.allLabel, a.onCopy, { resetAll() }, a.onPaste, { flip("rp_float_tools") }, { flip("rp_float_lock") }, { cycleAlpha() }, { d -> bumpFont(d) }, { flip("rp_float_ctx") })   // v0.39.6: no close button (the Float chip closes / opens)

    private fun content(snap: ReplaceFloat.Snap, tab: Int, a: ReplaceFloatView.Actions, st: Int, w: Int, rows: Int): View {
        val v = ReplaceFloatView(ctx, prefs, feedback)
        return if (st == ReplaceFloat.MINI) {
            val b = v.bubble(snap)
            b.layoutParams = FrameLayout.LayoutParams(dp(46), dp(46))
            b
        } else {
            val c = v.window(snap, tab, actionsFor(a), rows)
            c.layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            c
        }
    }

    private fun bounds(): IntArray {
        val dm = ctx.resources.displayMetrics
        return intArrayOf(0, 0, dm.widthPixels, dm.heightPixels)
    }

    // ------------------------------------------------------------------ v0.44.0: size helpers

    private fun rowPx(): Int = dp(ReplaceFloatView.rowDp(ReplaceFloatView.fontOffset(prefs)))

    private fun locked(): Boolean = try { shownState == ReplaceFloat.OPEN && prefs.bool("rp_float_lock", false) } catch (_: Throwable) { false }

    /** v0.45.0: 100% / 80% / 60% (pref rp_float_alpha 0..2). */
    private fun baseAlpha(): Float = when (try { prefs.int("rp_float_alpha", 0) } catch (_: Throwable) { 0 }) { 1 -> 0.8f; 2 -> 0.6f; else -> 1f }

    private fun flip(k: String) {
        try { prefs.setBool(k, !prefs.bool(k, false)) } catch (_: Throwable) { }
        try { onChange?.invoke() } catch (_: Throwable) { }
    }

    private fun cycleAlpha() {
        try { prefs.setInt("rp_float_alpha", (prefs.int("rp_float_alpha", 0).coerceIn(0, 2) + 1) % 3) } catch (_: Throwable) { }
        try { box?.alpha = baseAlpha() } catch (_: Throwable) { }
        try { onChange?.invoke() } catch (_: Throwable) { }
    }

    private fun bumpFont(d: Int) {
        try { prefs.setInt("rp_float_font", (ReplaceFloatView.fontOffset(prefs) + d).coerceIn(-1, 4)) } catch (_: Throwable) { }
        try { onChange?.invoke() } catch (_: Throwable) { }
    }

    private fun roomW(b: IntArray): Int = (b[2] - 2 * dp(10)).coerceAtLeast(dp(200))

    /** {min, max} width in px: 260 dp .. the screen less 10 dp each side (at most 560 dp). */
    private fun widthBounds(b: IntArray): IntArray {
        val room = roomW(b)
        val mn = dp(260).coerceAtMost(room)
        return intArrayOf(mn, dp(560).coerceAtMost(room).coerceAtLeast(mn))
    }

    private fun defaultWidth(b: IntArray): Int = (b[2] * 0.84f).toInt().coerceIn(dp(260), dp(340)).coerceAtMost(roomW(b))

    private fun widthFor(b: IntArray): Int {
        val pref = prefs.int("rp_float_w", 0)
        if (pref <= 0) return defaultWidth(b)
        val wb = widthBounds(b)
        return dp(pref).coerceIn(wb[0], wb[1])
    }

    /** Rows that fit: the window (about 130 dp of header + footer + rows) stays under 80% of the screen height (landscape / low screens get fewer rows). */
    private fun fitRows(b: IntArray, rows: Int): Int {
        val room = ((b[3] - 2 * dp(10)) * 0.8f).toInt() - dp(130 + (if (try { prefs.bool("rp_float_tools", false) } catch (_: Throwable) { false }) 36 else 0))
        val maxR = (room / rowPx()).coerceIn(1, ReplaceFloat.WIN_MAX_ROWS)
        return rows.coerceIn(1, maxR)
    }

    /** Redraws the open window with a new width / row count (the content is rebuilt only when the row count changes). */
    private fun applySize(w: Int, rows: Int, h: Int) {
        val f = box ?: return
        val s = lastSnap ?: return
        val a = lastA ?: return
        if (rows != rowsNow) {
            rowsNow = rows
            f.removeAllViews()
            f.addView(content(s, lastTab, a, shownState, w, rows))
        } else {
            val c = f.getChildAt(0)
            val lp = c?.layoutParams
            if (c != null && lp != null) { lp.width = w; c.layoutParams = lp }
        }
        winW = w; winH = h
        try { pw?.update(curX - offX, curY - offY, w, -1) } catch (_: Throwable) { }
        f.post { try { if (box === f && f.height > 0) winH = f.height } catch (_: Throwable) { } }
    }

    private fun saveSize() {
        try {
            val d = ctx.resources.displayMetrics.density
            prefs.setInt("rp_float_w", Math.round(winW / d))
            prefs.setInt("rp_float_rows", rowsNow)
            prefs.setInt("rp_float_x", curX)
            prefs.setInt("rp_float_y", curY)
        } catch (_: Throwable) { }
    }

    /** Double-tap on the header: not default size = back to default; already default = a bigger window (wide, 5 rows). */
    private fun toggleSize() {
        try {
            if (pw == null || shownState != ReplaceFloat.OPEN) return
            val s = lastSnap ?: return
            val b = bounds()
            val wb = widthBounds(b)
            val isDefault = prefs.int("rp_float_w", 0) <= 0 && prefs.int("rp_float_rows", 0) <= 0
            val nw = if (isDefault) dp(420).coerceIn(wb[0], wb[1]) else defaultWidth(b)
            val nr = fitRows(b, if (isDefault) 5 else ReplaceFloatView.rowsFor(s, 0))
            val h0 = box?.height?.takeIf { it > 0 } ?: winH
            val nh = h0 + (nr - rowsNow) * rowPx()
            val c = ReplaceFloat.clamp(curX, curY, nw, nh, b[0], b[1], b[2], b[3], dp(6))
            curX = c[0]; curY = c[1]
            applySize(nw, nr, nh)
            if (isDefault) saveSize()
            else { try { prefs.setInt("rp_float_w", 0); prefs.setInt("rp_float_rows", 0); prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) } catch (_: Throwable) { } }
        } catch (_: Throwable) { }
    }

    /** [reset] button: default size AND default place (the first-time place just above the keyboard). */
    fun resetAll() {
        try {
            prefs.setInt("rp_float_x", -1); prefs.setInt("rp_float_y", -1)
            prefs.setInt("rp_float_w", 0); prefs.setInt("rp_float_rows", 0)
        } catch (_: Throwable) { }
        hide()
        try { onChange?.invoke() } catch (_: Throwable) { }
    }

    private fun moveTo(x: Int, y: Int) {
        val b = bounds()
        val h = box?.height?.takeIf { it > 0 } ?: winH
        val c = ReplaceFloat.clamp(x, y, winW, h, b[0], b[1], b[2], b[3], dp(6))
        curX = c[0]; curY = c[1]
        try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { }
    }

    /**
     * v0.39.7: the WHOLE window (header, rows, footer, bubble) can be dragged. A touch that moves more than the touch slop is taken over from the child (button / row),
     * so a tap still works and a drag works from anywhere. alpha 0.85 while dragging. On release: bubble slides to the nearest edge, the place is saved.
     */
    private inner class DragFrame : FrameLayout(ctx) {
        private val slop = try { ViewConfiguration.get(ctx).scaledTouchSlop } catch (_: Throwable) { dp(8) }
        private var downX = 0f
        private var downY = 0f
        private var downLocalY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        // v0.44.0: resize = hold ~0.25 s on a corner, then drag
        private var corner: IntArray? = null   // {-1 left / 1 right, -1 top / 1 bottom} of the corner under the finger at DOWN
        private var resizing = false
        private var sW = 0
        private var sH = 0
        private var sRows = 2
        private var lastTap = 0L
        private var swipeOk = false   // v0.45.0: the touch started inside the rows
        private var downT = 0L
        private val holdRun = Runnable { try { startResize() } catch (_: Throwable) { } }
        private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init { setWillNotDraw(false) }

        private fun begin(e: MotionEvent) {
            downX = e.rawX; downY = e.rawY; downLocalY = e.y; downT = e.eventTime; swipeOk = inBody(e.y); startX = curX; startY = curY; dragging = false
            removeCallbacks(holdRun)
            corner = if (shownState == ReplaceFloat.OPEN && !resizing) cornerAt(e.x, e.y) else null
            if (corner != null) postDelayed(holdRun, 250)
        }
        private fun far(e: MotionEvent): Boolean { val dx = e.rawX - downX; val dy = e.rawY - downY; return dx * dx + dy * dy > slop * slop }
        private fun cancelHold() { removeCallbacks(holdRun); corner = null }
        private fun start() { cancelHold(); dragging = true; if (!locked()) alpha = minOf(baseAlpha(), 0.85f); try { parent?.requestDisallowInterceptTouchEvent(true) } catch (_: Throwable) { } }
        private fun end() {
            cancelHold()
            alpha = baseAlpha()
            if (resizing) {
                resizing = false; resizeActive = false
                saveSize()
                invalidate()
                return
            }
            if (!dragging) return
            dragging = false
            if (shownState == ReplaceFloat.MINI) snapToEdge()
            else if (!locked()) snapEdges()
        }

        private fun cornerAt(x: Float, y: Float): IntArray? {
            val z = dp(34)
            val l = x < z; val r = x > width - z; val t = y < z; val bt = y > height - z
            return if ((l || r) && (t || bt)) intArrayOf(if (r) 1 else -1, if (bt) 1 else -1) else null
        }

        private fun startResize() {
            val cd = corner ?: return
            if (dragging || resizing || pw == null || shownState != ReplaceFloat.OPEN) return
            resizing = true; resizeActive = true
            sW = winW; sH = if (height > 0) height else winH; sRows = rowsNow; startX = curX; startY = curY
            alpha = minOf(baseAlpha(), 0.92f)
            try { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) { }
            try { parent?.requestDisallowInterceptTouchEvent(true) } catch (_: Throwable) { }
            invalidate()
        }

        private fun resizeMove(e: MotionEvent) {
            val cd = corner ?: return
            val b = bounds()
            val wb = widthBounds(b)
            val dx = e.rawX - downX
            val dy = e.rawY - downY
            val m = dp(6)
            // the corner opposite to the held one stays fixed
            val avail = if (cd[0] > 0) b[2] - m - startX else startX + sW - m - b[0]
            val nw = (if (cd[0] > 0) sW + dx else sW - dx).toInt().coerceIn(wb[0], maxOf(wb[0], minOf(wb[1], avail)))
            val dRows = Math.round((if (cd[1] > 0) dy else -dy) / rowPx())
            val nr = fitRows(b, sRows + dRows)
            val nh = sH + (nr - sRows) * rowPx()
            val x = if (cd[0] > 0) startX else startX + sW - nw
            val y = if (cd[1] > 0) startY else startY + sH - nh
            val c = ReplaceFloat.clamp(x, y, nw, nh, b[0], b[1], b[2], b[3], m)
            curX = c[0]; curY = c[1]
            applySize(nw, nr, nh)
        }

        private fun inBody(y: Float): Boolean = try {
            val bv = getChildAt(0)?.findViewWithTag<View>("rf_body")
            shownState == ReplaceFloat.OPEN && bv != null && y >= bv.top && y <= bv.bottom
        } catch (_: Throwable) { false }

        private fun headerBottom(): Int = try { (getChildAt(0)?.findViewWithTag<View>("rf_header")?.bottom ?: 0) } catch (_: Throwable) { 0 }

        /** A touch on an empty part of the header that did not move: two of them within 300 ms = double-tap. */
        private fun tapped() {
            val now = System.currentTimeMillis()
            if (now - lastTap < 300) { lastTap = 0L; toggleSize() } else lastTap = now
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            var take = false
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> begin(e)
                    MotionEvent.ACTION_MOVE -> {
                        if (resizing) return true
                        if (!dragging && far(e)) { start(); return true }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { take = dragging || resizing; end() }   // a hold without moving must not click the button under the finger
                }
            } catch (_: Throwable) { }
            return dragging || resizing || take
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { begin(e); return true }   // touch on an empty part of the window
                    MotionEvent.ACTION_MOVE -> {
                        if (resizing) resizeMove(e)
                        else {
                            if (!dragging && far(e)) start()
                            if (dragging && !locked()) moveTo(startX + (e.rawX - downX).toInt(), startY + (e.rawY - downY).toInt())
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        val swipe = swipeOk && !resizing && e.eventTime - downT < 350 && Math.abs(dx) > dp(56) && Math.abs(dx) > 2 * Math.abs(dy)
                        val tap = !dragging && !resizing && shownState == ReplaceFloat.OPEN && downLocalY < headerBottom()
                        if (swipe) {   // v0.45.0: a quick sideways flick in the rows = next / previous match; the window goes back to where it was
                            if (dragging && !locked()) moveTo(startX, startY)
                            end()
                            val a = lastA
                            try { if (dx < 0) a?.onNext?.invoke() else a?.onPrev?.invoke() } catch (_: Throwable) { }
                        } else {
                            end()
                            if (tap) tapped()
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> end()
                }
            } catch (_: Throwable) { }
            return true
        }

        /** v0.44.0: 3 small diagonal marks in each corner (the one being resized is drawn in the accent colour). */
        override fun dispatchDraw(cv: Canvas) {
            super.dispatchDraw(cv)
            try {
                if (shownState != ReplaceFloat.OPEN || width <= 0 || height <= 0) return
                val dim = try { JabraTokens.withAlpha(JabraTokens.cardNote(prefs), 0xB0) } catch (_: Throwable) { 0x80808080.toInt() }
                val m = dp(3).toFloat()
                val d = dp(3).toFloat()
                markPaint.strokeWidth = dp(1).coerceAtLeast(1).toFloat()
                markPaint.strokeCap = Paint.Cap.ROUND
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    val active = resizing && corner?.let { it[0] == sx && it[1] == sy } == true
                    markPaint.color = if (active) JabraTokens.ACCENT else dim
                    val ax = if (sx > 0) width - m else m
                    val ay = if (sy > 0) height - m else m
                    val ix = -sx.toFloat()   // inward direction
                    val iy = -sy.toFloat()
                    for (k in 1..3) cv.drawLine(ax, ay + iy * k * d, ax + ix * k * d, ay, markPaint)
                }
            } catch (_: Throwable) { }
        }
    }

    /** v0.45.0: a window released within 28 dp of the left / right / top edge slides to that edge (6 dp margin); the place is saved. */
    private fun snapEdges() {
        try {
            val b = bounds()
            val th = dp(28)
            val m = dp(6)
            var tx = curX
            var ty = curY
            if (curX - b[0] < th) tx = b[0] + m else if (b[2] - (curX + winW) < th) tx = b[2] - winW - m
            if (curY - b[1] < th) ty = b[1] + m
            val x0 = curX
            val y0 = curY
            if (tx == x0 && ty == y0) { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY); return }
            val va = ValueAnimator.ofFloat(0f, 1f)
            va.duration = 150
            va.addUpdateListener { try { val t = it.animatedValue as Float; moveTo(x0 + ((tx - x0) * t).toInt(), y0 + ((ty - y0) * t).toInt()) } catch (_: Throwable) { } }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { try { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) } catch (_: Throwable) { } }
            })
            va.start()
        } catch (_: Throwable) { try { prefs.setInt("rp_float_x", curX); prefs.setInt("rp_float_y", curY) } catch (_: Throwable) { } }
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
        if (st == ReplaceFloat.MINI) {   // bubble: tap = open (drag is done by DragFrame)
            c.isClickable = true
            c.setOnClickListener { try { feedback(c) } catch (_: Throwable) { }; setState(ReplaceFloat.OPEN) }
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
            val w = if (st == ReplaceFloat.MINI) dp(46) else if (resizeActive && winW > 0) winW else widthFor(b)   // v0.39.7: narrower than the screen, so it can also move left / right; v0.44.0: or the size the person dragged (rp_float_w)
            lastSnap = snap; lastTab = tab; lastA = a
            val rows = if (resizeActive) rowsNow else fitRows(b, ReplaceFloatView.rowsFor(snap, prefs.int("rp_float_rows", 0)))
            rowsNow = rows
            val c = content(snap, tab, a, st, w, rows)
            val win = pw
            val bx = box
            if (win != null && bx != null && shownState == st) {   // same state: swap the content, keep the window (no flicker)
                bx.removeAllViews()
                bx.addView(c)
                wire(c, st)
                winW = w
                bx.post {   // v0.45.0: the content may be taller / shorter now (tools row, font): keep the window on the screen
                    try {
                        val h = bx.height
                        if (box === bx && h > 0) {
                            winH = h
                            val bb = bounds()
                            val cc = ReplaceFloat.clamp(curX, curY, winW, h, bb[0], bb[1], bb[2], bb[3], dp(6))
                            if (cc[0] != curX || cc[1] != curY) { curX = cc[0]; curY = cc[1]; try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { } }
                        }
                    } catch (_: Throwable) { }
                }
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
                val x = if (st == ReplaceFloat.MINI) b[2] - w - margin else (b[2] - w) / 2
                ReplaceFloat.clamp(x, rl[1] - h - dp(8), w, h, b[0], b[1], b[2], b[3], dp(6))
            } else ReplaceFloat.place(sx, sy, w, h, b[0], b[1], b[2], b[3], dp(6))
            curX = pos[0]; curY = pos[1]
            val f = DragFrame()   // v0.39.7: drag from anywhere
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
                f.animate().alpha(baseAlpha()).scaleX(1f).scaleY(1f).setDuration(140).start()
            } catch (_: Throwable) { f.alpha = baseAlpha(); f.scaleX = 1f; f.scaleY = 1f }
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
