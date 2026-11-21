package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.ReplaceFloat

/**
 * v0.45.7: the FLOATING RESULT WINDOW of the Formatter (same idea and same tricks as ReplaceFloatWin.kt, a smaller window).
 * A PopupWindow of the keyboard: NO new permission, touchable but NOT focusable (the app's text field keeps the focus).
 * It shows the EXACT text the Formatter would output (Show: Result) or the original text (Show: Original), in a scrolling box, and it is swapped in place
 * (no flicker, the scroll position is kept) every time the Formatter panel redraws, so it follows every option you change.
 * Header = drag handle (move the window; the place is kept in prefs fmt_float_x / fmt_float_y, numbers only), buttons: Original / Result, Copy, close.
 * First place = just above the keyboard. After showing, the real place is measured (calibrate, like the Replace window); off screen = onFail.
 * show() returns false when the window could not be shown: the caller then uses the small box inside the panel (FALLBACK).
 * No text is ever stored. Delete this file + the hooks marked "v0.45.7" = back to v0.45.6.
 */
class FormatFloatWin(private val ctx: Context, private val prefs: JabraPrefs, private val feedback: (View) -> Unit) {

    class Actions(val onCopy: () -> Unit, val onToggle: () -> Unit, val onClose: () -> Unit)

    /** Called when the window ends up off screen (reason = short text). */
    var onFail: ((String) -> Unit)? = null
    var lastError: String = ""

    private var pw: PopupWindow? = null
    private var box: FrameLayout? = null
    private var curX = 0
    private var curY = 0
    private var winW = 0
    private var offX = 0
    private var offY = 0
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0

    companion object {
        /** The window of the open Formatter panel (one at most). MgbIme calls dismissLive() when the panel is left or the keyboard hides. */
        @JvmStatic var live: FormatFloatWin? = null
        @JvmStatic fun dismissLive() { try { live?.hide() } catch (_: Throwable) { } }
        private const val MAX_SHOWN = 60000   // chars drawn in the window (Copy always copies everything)
    }

    private fun dp(v: Int): Int = JabraTokens.dp(ctx, v)

    fun isShowing(): Boolean = pw != null

    fun hide() {
        try { pw?.dismiss() } catch (_: Throwable) { }
        pw = null
        box = null
    }

    private fun bounds(): IntArray {
        val dm = ctx.resources.displayMetrics
        return intArrayOf(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun defaultWidth(b: IntArray): Int {
        val room = (b[2] - 2 * dp(10)).coerceAtLeast(dp(200))
        return (b[2] * 0.84f).toInt().coerceIn(dp(260), dp(340)).coerceAtMost(room)
    }

    /** Height of the text area: 240 dp, at most about a third of the screen (landscape gets a smaller box). */
    private fun bodyHeight(b: IntArray): Int = minOf(dp(240), (b[3] * 0.32f).toInt()).coerceAtLeast(dp(90))

    private fun chip(label: String, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label
        t.textSize = 12f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.gravity = Gravity.CENTER
        t.minWidth = dp(36)
        t.setPadding(dp(8), dp(7), dp(8), dp(7))
        t.setTextColor(JabraTokens.ACCENT)
        t.isClickable = true
        t.setOnClickListener { try { feedback(t) } catch (_: Throwable) { }; try { onClick() } catch (_: Throwable) { } }
        return t
    }

    private fun content(title: String, text: String, a: Actions, w: Int, bodyH: Int): View {
        val ink = try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(6), dp(2), dp(6), dp(6))
        try { col.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { col.setBackgroundColor(Color.WHITE) }
        col.layoutParams = FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.minimumHeight = dp(34)
        val grip = TextView(ctx)
        grip.text = "\u2261"
        grip.textSize = 18f
        grip.setTextColor(try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY })
        grip.setPadding(dp(4), 0, dp(6), 0)
        head.addView(grip)
        val tt = TextView(ctx)
        tt.text = title
        tt.textSize = 13f
        tt.setTypeface(tt.typeface, Typeface.BOLD)
        tt.setTextColor(ink)
        tt.maxLines = 1
        tt.ellipsize = android.text.TextUtils.TruncateAt.END
        tt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        head.addView(tt)
        head.addView(chip(if (title.startsWith("Original")) "Result" else "Original", a.onToggle))
        head.addView(chip("Copy", a.onCopy))
        head.addView(chip("\u2715", a.onClose))
        head.setOnTouchListener { _, e -> dragTouch(e) }   // the header moves the window (the buttons keep their own taps)
        col.addView(head)

        val sv = ScrollView(ctx)
        sv.tag = "ff_body"
        sv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, bodyH)
        val tv = TextView(ctx)
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 12f
        tv.setTextColor(ink)
        tv.setPadding(dp(4), dp(2), dp(4), dp(2))
        tv.text = if (text.isEmpty()) "(empty)"
            else if (text.length > MAX_SHOWN) text.substring(0, MAX_SHOWN) + "\n\u2026 " + (text.length - MAX_SHOWN) + " more characters (Copy gets all of it)"
            else text
        sv.addView(tv)
        col.addView(sv)
        return col
    }

    private fun dragTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = curX; startY = curY }
            MotionEvent.ACTION_MOVE -> moveTo(startX + (e.rawX - downX).toInt(), startY + (e.rawY - downY).toInt())
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { try { prefs.setInt("fmt_float_x", curX); prefs.setInt("fmt_float_y", curY) } catch (_: Throwable) { } }
        }
        return true
    }

    private fun moveTo(x: Int, y: Int) {
        val b = bounds()
        val h = box?.height?.takeIf { it > 0 } ?: dp(280)
        val c = ReplaceFloat.clamp(x, y, winW, h, b[0], b[1], b[2], b[3], dp(6))
        curX = c[0]; curY = c[1]
        try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { }
    }

    /**
     * Shows or updates the window. true = it is on screen; false = it could not be shown (the caller uses the small box inside the panel).
     * [title] e.g. "Result . 1234 chars", [text] the exact text.
     */
    fun show(anchor: View, title: String, text: String, a: Actions): Boolean {
        try {
            if (anchor.windowToken == null) return false
            live = this
            val b = bounds()
            val w = defaultWidth(b)
            val bodyH = bodyHeight(b)
            val c = content(title, text, a, w, bodyH)
            val win = pw
            val bx = box
            if (win != null && bx != null) {   // same window: swap the content, keep the place and the scroll position
                val oldY = try { bx.findViewWithTag<ScrollView>("ff_body")?.scrollY ?: 0 } catch (_: Throwable) { 0 }
                bx.removeAllViews()
                bx.addView(c)
                winW = w
                try { c.findViewWithTag<ScrollView>("ff_body")?.let { s -> s.post { try { s.scrollTo(0, oldY) } catch (_: Throwable) { } } } } catch (_: Throwable) { }
                return true
            }
            hide()
            c.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val h = c.measuredHeight
            winW = w
            val rl = IntArray(2)
            anchor.rootView.getLocationOnScreen(rl)
            val sx = prefs.int("fmt_float_x", -1)
            val sy = prefs.int("fmt_float_y", -1)
            val pos: IntArray = if (sx < 0 || sy < 0) ReplaceFloat.clamp((b[2] - w) / 2, rl[1] - h - dp(8), w, h, b[0], b[1], b[2], b[3], dp(6))   // first time: just above the keyboard
                else ReplaceFloat.place(sx, sy, w, h, b[0], b[1], b[2], b[3], dp(6))
            curX = pos[0]; curY = pos[1]
            val f = FrameLayout(ctx)
            f.addView(c)
            val p = PopupWindow(f, w, ViewGroup.LayoutParams.WRAP_CONTENT, false)
            p.isTouchable = true
            p.isFocusable = false   // the app's text field keeps the focus
            try { p.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED } catch (_: Throwable) { }
            try { p.elevation = 0f } catch (_: Throwable) { }
            ToolTip.inScreen(p)
            offX = 0; offY = 0
            p.showAtLocation(anchor, Gravity.NO_GRAVITY, curX, curY)
            pw = p; box = f
            f.post { calibrate(f) }
            return true
        } catch (e: Throwable) {
            lastError = e.javaClass.simpleName + (if (e.message != null) ": " + e.message else "")
            hide()
            return false
        }
    }

    /** Same idea as ReplaceFloatWin.calibrate: measure where the popup really is, correct the difference, look again after 150 ms and report onFail when it is still off screen. */
    private fun calibrate(f: View) {
        try {
            if (pw == null) return
            val l = IntArray(2)
            f.getLocationOnScreen(l)
            val dx = l[0] - curX
            val dy = l[1] - curY
            if (Math.abs(dx) > 2 || Math.abs(dy) > 2) {
                offX += dx; offY += dy
                try { pw?.update(curX - offX, curY - offY, -1, -1) } catch (_: Throwable) { }
            }
            f.postDelayed({
                try {
                    if (pw == null) return@postDelayed
                    val l2 = IntArray(2)
                    f.getLocationOnScreen(l2)
                    val b = bounds()
                    val bad = Math.abs(l2[0] - curX) > dp(12) || Math.abs(l2[1] - curY) > dp(12) || l2[1] > b[3] - dp(10) || l2[1] + dp(10) < 0
                    if (bad) { lastError = "off screen (want " + curX + "," + curY + " got " + l2[0] + "," + l2[1] + ")"; onFail?.invoke(lastError) }
                } catch (_: Throwable) { }
            }, 150)
        } catch (_: Throwable) { }
    }
}
