package com.mgb.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.mgb.keyboard.typing.ReplacePeek
import com.mgb.keyboard.typing.TipPlace

/**
 * v0.32.0 (PEEK): the long-press card of Find & Replace. A small PopupWindow ABOVE the pressed button (Before / After with the changed part
 * coloured, rule counts, a hint line). It is NOT touchable and NOT focusable, so the finger keeps its gesture; releasing the finger closes it
 * (watchRelease). Same window pattern as KeyPopup / ToolTip (screen coordinates + ToolTip.inScreen). Nothing is written, no permission, no pref.
 * show() returns false when the card could not be shown (no window token, BadTokenException ...): the caller then uses its FALLBACK (inline preview box).
 * Delete this file + the hooks marked "v0.32.0" = back to v0.31.x.
 */
object PeekPopup {
    private var pw: PopupWindow? = null
    private val OK_TINT: Int = 0xFF2E7D32.toInt()

    fun dismiss() { try { pw?.dismiss() } catch (_: Throwable) { }; pw = null }

    /** Finger lifted: the card fades out quickly (90 ms), then closes. Any problem = closes at once. */
    private fun dismissSoft() {
        val w = pw ?: return
        pw = null
        try {
            val c = w.contentView
            if (c == null) { w.dismiss(); return }
            c.animate().cancel()
            c.animate().alpha(0f).setDuration(90).withEndAction { try { w.dismiss() } catch (_: Throwable) { } }.start()
        } catch (_: Throwable) { try { w.dismiss() } catch (_: Throwable) { } }
    }

    /** Touch UP / CANCEL on [v] closes the card. The listener never consumes the touch (returns false). */
    fun watchRelease(v: View) {
        try {
            v.setOnTouchListener { _, e ->
                try { if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) dismissSoft() } catch (_: Throwable) { }
                false
            }
        } catch (_: Throwable) { }
    }

    /** Shows the card above [anchor]. true = it is on screen. Any problem = false (and nothing left open). */
    fun show(anchor: View, p: JabraPrefs, peek: ReplacePeek.Peek, host: JabraHost?): Boolean {
        dismiss()
        try {
            val ctx = anchor.context
            val root = anchor.rootView
            if (anchor.windowToken == null) return false
            val sw = ctx.resources.displayMetrics.widthPixels
            val margin = JabraTokens.dp(ctx, 6)
            val rootW = if (root.width > 0) root.width else sw
            val w = (rootW - 2 * margin).coerceAtLeast(JabraTokens.dp(ctx, 200))
            val content = build(anchor, p, peek)
            content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val h = content.measuredHeight
            val loc = IntArray(2); anchor.getLocationOnScreen(loc)
            val rl = IntArray(2); root.getLocationOnScreen(rl)
            var y = loc[1] - h - JabraTokens.dp(ctx, 8)
            if (y < 0) y = rl[1] + JabraTokens.dp(ctx, 4)   // no room above: over the top of the keyboard
            val x = TipPlace.x(rl[0] + margin, w, w, sw)
            content.alpha = 0f   // smooth open: fade + small slide up (140 ms), see below
            content.translationY = JabraTokens.dp(ctx, 6).toFloat()
            val win = PopupWindow(content, w, ViewGroup.LayoutParams.WRAP_CONTENT, false)
            win.isTouchable = false
            win.isFocusable = false
            try { win.elevation = JabraTokens.dp(ctx, 8).toFloat() } catch (_: Throwable) { }
            ToolTip.inScreen(win)
            win.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            pw = win
            try { content.animate().alpha(1f).translationY(0f).setDuration(140).setInterpolator(DecelerateInterpolator()).start() } catch (_: Throwable) { content.alpha = 1f; content.translationY = 0f }
            try { host?.feedback(anchor) } catch (_: Throwable) { }
            return true
        } catch (_: Throwable) {
            dismiss()
            return false
        }
    }

    private fun tv(a: View, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(a.context)
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    /** Row text with the changed part {start, end} coloured (tint) and bold. Marks are index ranges, so Arabic / RTL lines work the same. */
    private fun span(text: String, m: IntArray, tint: Int): CharSequence {
        val sb = SpannableStringBuilder(text)
        try {
            val a = m[0].coerceIn(0, text.length)
            val b = m[1].coerceIn(a, text.length)
            if (b > a) {
                sb.setSpan(BackgroundColorSpan(JabraTokens.withAlpha(tint, 0x66)), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } catch (_: Throwable) { }
        return sb
    }

    private fun build(a: View, p: JabraPrefs, peek: ReplacePeek.Peek): View {
        val ctx = a.context
        val ink = JabraTokens.cardText(p)
        val dim = JabraTokens.cardNote(p)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val pad = JabraTokens.dp(ctx, 10)
        box.setPadding(pad, JabraTokens.dp(ctx, 8), pad, JabraTokens.dp(ctx, 8))
        try {
            val g = GradientDrawable()
            g.setColor(JabraTokens.cardFill(p, false))
            g.setStroke(JabraTokens.dp(ctx, 1).coerceAtLeast(1), JabraTokens.cardStroke(p, true, false))
            g.cornerRadius = JabraTokens.dp(ctx, JabraTokens.R_MD).toFloat()
            box.background = g
        } catch (_: Throwable) { box.setBackgroundColor(Color.WHITE) }
        if (peek.title.isNotEmpty()) { val t = tv(a, 13f, ink, true); t.text = peek.title; t.maxLines = 1; t.ellipsize = TextUtils.TruncateAt.END; box.addView(t) }
        if (peek.summary.isNotEmpty()) { val t = tv(a, 12f, dim); t.text = peek.summary; t.maxLines = 2; t.ellipsize = TextUtils.TruncateAt.END; box.addView(t) }
        if (peek.rows.isNotEmpty()) {
            box.addView(tv(a, 11f, dim, true).also { it.text = "Before"; it.setPadding(0, JabraTokens.dp(ctx, 6), 0, 0) })
            for (r in peek.rows) { val t = tv(a, 12f, ink); t.text = span(r.before, r.bm, JabraTokens.DANGER); t.maxLines = 4; t.ellipsize = TextUtils.TruncateAt.END; box.addView(t) }
            box.addView(tv(a, 11f, dim, true).also { it.text = "After"; it.setPadding(0, JabraTokens.dp(ctx, 6), 0, 0) })
            for (r in peek.rows) { val t = tv(a, 12f, ink); t.text = span(r.after, r.am, OK_TINT); t.maxLines = 4; t.ellipsize = TextUtils.TruncateAt.END; box.addView(t) }
        }
        if (peek.more.isNotEmpty()) { val t = tv(a, 11f, dim); t.text = peek.more; box.addView(t) }
        if (peek.rules.isNotEmpty()) {
            val t = tv(a, 11f, ink)
            t.text = peek.rules.joinToString("\n")
            t.setPadding(0, JabraTokens.dp(ctx, 6), 0, 0)
            box.addView(t)
        }
        if (peek.hint.isNotEmpty()) { val t = tv(a, 11f, dim); t.text = peek.hint; t.setPadding(0, JabraTokens.dp(ctx, 6), 0, 0); box.addView(t) }
        return box
    }
}
