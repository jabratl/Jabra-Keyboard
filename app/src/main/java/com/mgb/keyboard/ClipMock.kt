package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.clip.ClipList
import com.mgb.keyboard.typing.ClipMockPlan

/**
 * v0.50.0 (CM1): the "mock look" of the clipboard (clipboard-compact-redesign-mock.html): underline tabs, one-line pinned chip strip,
 * swipe row (right = pin / unpin, left = delete). Only views here; every action is passed in by ClipPanel, so each tap / swipe runs the
 * SAME code as the old buttons. Pref clip_mock (default ON, needs the tidy look). OFF = exactly v0.49.0. Any problem = the caller keeps the old view.
 */
object ClipMock {
    fun on(p: JabraPrefs?): Boolean = try { ClipLook.tidy(p) && (p?.bool("clip_mock", true) ?: true) } catch (_: Throwable) { false }

    private fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun dark(p: JabraPrefs): Boolean = try { JabraTokens.isDark(p) } catch (_: Throwable) { false }
    fun accent(p: JabraPrefs): Int = try { JabraTokens.cardStroke(p, true, false) } catch (_: Throwable) { Color.parseColor("#2F6FD0") }
    fun gold(p: JabraPrefs): Int = ClipLook.pinColor(dark(p))
    private fun red(p: JabraPrefs): Int = if (dark(p)) Color.parseColor("#EF7A74") else Color.parseColor("#D4453D")

    // ---- v0.53.1 (CM2): the rows / tag / icon buttons drawn like the mock (flat, no stroke) but in the THEME colours

    private fun blend(a: Int, b: Int, t: Float): Int = Color.argb(Color.alpha(a),
        (Color.red(a) * (1f - t) + Color.red(b) * t).toInt(), (Color.green(a) * (1f - t) + Color.green(b) * t).toInt(), (Color.blue(a) * (1f - t) + Color.blue(b) * t).toInt())

    /** Mock row: theme card fill, NO stroke, 10 dp corners. Picked = the accent mixed in at 18 % (mock: color-mix(sel 18%, row)). */
    fun rowBg(ctx: Context, p: JabraPrefs, picked: Boolean): GradientDrawable {
        val fill = try { JabraTokens.cardFill(p, false) } catch (_: Throwable) { Color.LTGRAY }
        val g = GradientDrawable()
        g.setColor(if (picked) blend(fill, accent(p), 0.18f) else fill)
        g.cornerRadius = dp(ctx, 10).toFloat()
        return g
    }

    /** Mock "Aa" tag: 10.5 sp, thin outline, 6 dp corners, 17 dp high. The returned box is the tap target (as high as the row). Caller sets the click. */
    fun fmtTag(ctx: Context, p: JabraPrefs): FrameLayout {
        val c = try { JabraTokens.onCardDim(p) } catch (_: Throwable) { Color.GRAY }
        val t = TextView(ctx)
        t.text = "Aa"
        t.textSize = 10.5f
        t.setTextColor(c)
        t.maxLines = 1
        t.gravity = Gravity.CENTER
        t.setPadding(dp(ctx, 5), 0, dp(ctx, 5), 0)
        val g = GradientDrawable()
        g.setColor(Color.TRANSPARENT)
        g.setStroke(Math.max(1, dp(ctx, 1)), JabraTokens.withAlpha(c, 0x80))
        g.cornerRadius = dp(ctx, 6).toFloat()
        t.background = g
        val box = FrameLayout(ctx)
        box.addView(t, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 17), Gravity.CENTER))
        box.isClickable = true
        box.contentDescription = "Paste the other way (with / without format)"
        return box
    }

    /** Mock icon button (.ib): no box, a soft rounded fill only while pressed. */
    fun flat(ctx: Context, p: JabraPrefs, b: View) {
        try {
            val press = GradientDrawable()
            press.setColor(try { JabraTokens.cardFill(p, false) } catch (_: Throwable) { Color.LTGRAY })
            press.cornerRadius = dp(ctx, 10).toFloat()
            val sl = android.graphics.drawable.StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), press)
            sl.addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            b.background = sl
            b.backgroundTintList = null
            b.stateListAnimator = null
        } catch (_: Throwable) { }
    }

    /** Underline tabs "All 12 | Pinned 3 | ..." (scrolls sideways when too wide). [extra] (the Undo icon) sits at the end, outside the scroll. */
    fun tabs(ctx: Context, p: JabraPrefs, labels: List<String>, counts: List<Int>, selected: Int, textSp: Float, hDp: Int,
             ink: Int, dim: Int, onPick: (Int) -> Unit, extra: View?): View {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val inner = LinearLayout(ctx)
        inner.orientation = LinearLayout.HORIZONTAL
        inner.setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
        val acc = accent(p)
        for (i in labels.indices) {
            val on = i == selected
            val tab = LinearLayout(ctx)
            tab.orientation = LinearLayout.VERTICAL
            tab.gravity = Gravity.CENTER_HORIZONTAL
            val tv = TextView(ctx)
            val label = labels[i]
            val full = ClipMockPlan.tabText(label, counts.getOrElse(i) { -1 })
            val sp = SpannableString(full)
            if (full.length > label.length) sp.setSpan(RelativeSizeSpan(0.85f), label.length, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = sp
            tv.textSize = textSp
            tv.maxLines = 1
            tv.setTextColor(if (on) ink else dim)
            if (on) tv.setTypeface(tv.typeface, Typeface.BOLD)
            tv.gravity = Gravity.CENTER
            tv.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0)
            tab.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f))
            val ul = View(ctx)
            ul.setBackgroundColor(if (on) acc else Color.TRANSPARENT)
            tab.addView(ul, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 2)))
            tab.isClickable = true
            tab.contentDescription = label
            tab.setOnClickListener { try { onPick(i) } catch (_: Throwable) { } }
            inner.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, hDp)))
        }
        hs.addView(inner)
        row.addView(hs, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (extra != null) row.addView(extra)
        return row
    }

    /** One-line strip of pinned clips (small gold dot + text). Tap = paste, hold = select mode with that clip. */
    fun pins(ctx: Context, p: JabraPrefs, items: List<ClipList.Item>, textSp: Float, hDp: Int, holdMs: Int,
             onTap: (ClipList.Item) -> Unit, onHold: (ClipList.Item) -> Unit, feedback: (View) -> Unit): View {
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(ctx, 6), dp(ctx, 3), dp(ctx, 6), dp(ctx, 2))
        val txt = try { JabraTokens.cardText(p) } catch (_: Throwable) { Color.BLACK }
        for (it0 in items) {
            val c = TextView(ctx)
            c.text = it0.text.replace("\n", " ")
            c.textSize = textSp
            c.setTextColor(txt)
            c.maxLines = 1
            c.ellipsize = TextUtils.TruncateAt.END
            c.maxWidth = dp(ctx, 132)
            c.gravity = Gravity.CENTER_VERTICAL
            c.setPadding(dp(ctx, 9), 0, dp(ctx, 12), 0)
            c.compoundDrawablePadding = dp(ctx, 6)
            val dot = GradientDrawable()
            dot.shape = GradientDrawable.OVAL
            dot.setColor(gold(p))
            dot.setSize(dp(ctx, 6), dp(ctx, 6))
            c.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            val bg = GradientDrawable()
            bg.setColor(try { JabraTokens.cardFill(p, false) } catch (_: Throwable) { Color.LTGRAY })
            bg.cornerRadius = dp(ctx, 15).toFloat()
            c.background = bg
            ClipHold.wire(c, holdMs, false, { onHold(it0) }, { onTap(it0) }, { feedback(c) })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, hDp))
            lp.rightMargin = dp(ctx, 6)
            row.addView(c, lp)
        }
        hs.addView(row)
        return hs
    }

    /**
     * Swipe row: [front] sits over a hidden "Pin / Delete" layer. Tap = [onTap], hold = [onHold] (not in select mode),
     * drag right past 80 dp = [onPin], drag left = [onDelete] (not in select mode). Vertical scrolling of the list is never blocked.
     */
    fun swipeRow(ctx: Context, p: JabraPrefs, front: View, pinLabel: String, selectOn: Boolean, holdMs: Int,
                 onTap: () -> Unit, onHold: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit, feedback: () -> Unit): View {
        val w = FrameLayout(ctx)
        val under = FrameLayout(ctx)
        under.visibility = View.INVISIBLE
        val l = TextView(ctx); l.text = pinLabel; l.textSize = 12.5f; l.setTypeface(l.typeface, Typeface.BOLD); l.setTextColor(gold(p))
        val r = TextView(ctx); r.text = "Delete"; r.textSize = 12.5f; r.setTypeface(r.typeface, Typeface.BOLD); r.setTextColor(red(p))
        under.addView(l, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL).also { it.leftMargin = dp(ctx, 14) })
        if (!selectOn) under.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL).also { it.rightMargin = dp(ctx, 14) })
        w.addView(under, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        w.addView(front, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val d = ctx.resources.displayMetrics.density
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        val hold = ClipMockPlan.holdMs(holdMs)
        val h = Handler(Looper.getMainLooper())
        var sx = 0f; var sy = 0f; var dx = 0f
        var down = false; var swiping = false; var moved = false; var held = false
        val tick = Runnable {
            if (down && !swiping && !moved && !selectOn) {
                held = true
                front.isPressed = false
                try { feedback() } catch (_: Throwable) { }
                try { onHold() } catch (_: Throwable) { }
            }
        }
        fun reset() {
            h.removeCallbacks(tick)
            down = false; swiping = false; moved = false; dx = 0f
            front.isPressed = false
            front.translationX = 0f
            under.visibility = View.INVISIBLE
        }
        front.isClickable = true
        front.setOnClickListener { try { feedback() } catch (_: Throwable) { }; try { onTap() } catch (_: Throwable) { } }   // TalkBack / keyboard click; touches are handled below
        front.setOnTouchListener { v, e ->
            try {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        down = true; swiping = false; moved = false; held = false; dx = 0f
                        sx = e.rawX; sy = e.rawY
                        v.isPressed = true
                        h.removeCallbacks(tick)
                        if (!selectOn) h.postDelayed(tick, hold.toLong())
                    }
                    MotionEvent.ACTION_MOVE -> if (down && !held) {
                        val mx = e.rawX - sx
                        val my = e.rawY - sy
                        if (Math.abs(mx) > slop || Math.abs(my) > slop) { moved = true; h.removeCallbacks(tick) }
                        if (!selectOn && !swiping && Math.abs(mx) > slop * 2 && Math.abs(mx) > Math.abs(my) * 1.5f) {
                            swiping = true
                            v.isPressed = false
                            under.visibility = View.VISIBLE
                            try { v.parent?.requestDisallowInterceptTouchEvent(true) } catch (_: Throwable) { }
                        }
                        if (swiping) { dx = mx; v.translationX = ClipMockPlan.drag(mx, d) }
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasHeld = held
                        val wasSwipe = swiping
                        val wasMoved = moved
                        val act = if (wasSwipe) ClipMockPlan.swipe(dx, d) else 0
                        reset()
                        if (!wasHeld) {
                            if (wasSwipe) { if (act > 0) onPin() else if (act < 0) onDelete() }
                            else if (!wasMoved) { try { feedback() } catch (_: Throwable) { }; onTap() }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> reset()
                    else -> { }
                }
            } catch (_: Throwable) { try { reset() } catch (_: Throwable) { } }
            true
        }
        try {   // mock .wrapr { overflow:hidden; border-radius:10px }: the pin / selection bar and the swipe never leave the rounded row
            w.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(ctx, 10).toFloat()) }
            }
            w.clipToOutline = true
        } catch (_: Throwable) { }
        return w
    }
}
