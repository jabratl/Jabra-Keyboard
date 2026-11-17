package com.mgb.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.ClipPop

/**
 * v0.35.1 (CC3-B): the floating menu of the clipboard (select-mode More + the 3-dot header menu).
 * A TOUCHABLE PopupWindow (not focusable), same window pattern as PeekPopup (screen coordinates + ToolTip.inScreen).
 * Rows come from the pure model ClipPop; the caller maps each id to the SAME old handler.
 * show() returns false on ANY problem (no window token, BadTokenException ...): the caller then opens the OLD tray (fallback).
 * Delete this file + the hooks marked "v0.35.1" = back to v0.35.0. NOT tested on a phone (Kotlin not compiled here).
 */
object ClipPopover {
    private var pw: PopupWindow? = null
    private var closedAt = 0L

    fun isOpen(): Boolean = try { pw?.isShowing == true } catch (_: Throwable) { false }
    fun lastDismiss(): Long = closedAt

    fun dismiss() {
        val w = pw ?: return
        pw = null
        closedAt = SystemClock.uptimeMillis()
        try { w.dismiss() } catch (_: Throwable) { }
    }

    /** Shows the menu at the right edge of [anchor]. preferUp = open upward (bottom bar); else downward when it fits. onPick runs AFTER the menu closed (except the Undo row, which keeps it open). */
    fun show(anchor: View, p: JabraPrefs, host: JabraHost?, items: List<ClipPop.Item>, preferUp: Boolean, onPick: (ClipPop.Item) -> Unit): Boolean {
        dismiss()
        try {
            val ctx = anchor.context
            val root = anchor.rootView
            if (anchor.windowToken == null || items.isEmpty()) return false
            val dm = ctx.resources.displayMetrics
            val sw = dm.widthPixels
            val margin = JabraTokens.dp(ctx, 6)
            val w = JabraTokens.dp(ctx, 200).coerceAtMost(sw - 2 * margin)
            val ink = JabraTokens.cardText(p)
            val iconOn = try { p.bool("icons_on", true) } catch (_: Throwable) { true }
            val rowPx = JabraTokens.dp(ctx, 40)
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(0, JabraTokens.dp(ctx, 4), 0, JabraTokens.dp(ctx, 4))
            var lastGroup = items[0].group
            for (it in items) {
                if (it.group != lastGroup) {   // thin divider between groups
                    val d = View(ctx)
                    d.setBackgroundColor(JabraTokens.withAlpha(ink, 0x33))
                    d.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, JabraTokens.dp(ctx, 1).coerceAtLeast(1)).also { lp -> lp.setMargins(JabraTokens.dp(ctx, 10), JabraTokens.dp(ctx, 2), JabraTokens.dp(ctx, 10), JabraTokens.dp(ctx, 2)) }
                    col.addView(d)
                    lastGroup = it.group
                }
                col.addView(row(ctx, p, host, it, ink, iconOn, rowPx, onPick))
            }
            val sv = ScrollView(ctx)
            sv.isVerticalScrollBarEnabled = false
            sv.addView(col)
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            try {
                val g = GradientDrawable()
                g.setColor(JabraTokens.cardFill(p, false))
                g.setStroke(JabraTokens.dp(ctx, 1).coerceAtLeast(1), JabraTokens.cardStroke(p, true, false))
                g.cornerRadius = JabraTokens.dp(ctx, JabraTokens.R_MD).toFloat()
                box.background = g
            } catch (_: Throwable) { box.setBackgroundColor(Color.WHITE) }
            box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            box.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val contentH = box.measuredHeight
            val loc = IntArray(2); anchor.getLocationOnScreen(loc)
            val rl = IntArray(2); root.getLocationOnScreen(rl)
            val gap = JabraTokens.dp(ctx, 4)
            val above = loc[1] - gap
            val below = (rl[1] + root.height) - (loc[1] + anchor.height) - gap
            val up = if (preferUp) true else (below < contentH && above > below)
            val space = if (up) above else below
            val dpPx = JabraTokens.dp(ctx, 1).coerceAtLeast(1)
            val h = ClipPop.maxHeightDp(space / dpPx, contentH / dpPx, 40) * dpPx
            val y = if (up) loc[1] - h - gap else loc[1] + anchor.height + gap
            val x = (loc[0] + anchor.width - w).coerceIn(margin, (sw - w - margin).coerceAtLeast(margin))
            box.alpha = 0f
            box.translationY = (if (up) 6 else -6) * dpPx.toFloat()
            val win = PopupWindow(box, w, h, false)
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.isTouchable = true
            win.isFocusable = false
            win.isOutsideTouchable = true
            win.setTouchInterceptor { _, e -> if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { dismiss(); true } else false }   // tap outside closes
            win.setOnDismissListener { if (pw === win) { pw = null; closedAt = SystemClock.uptimeMillis() } }
            try { win.elevation = JabraTokens.dp(ctx, 8).toFloat() } catch (_: Throwable) { }
            ToolTip.inScreen(win)
            win.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
            pw = win
            try { box.animate().alpha(1f).translationY(0f).setDuration(120).setInterpolator(DecelerateInterpolator()).start() } catch (_: Throwable) { box.alpha = 1f; box.translationY = 0f }
            return true
        } catch (_: Throwable) {
            dismiss()
            return false
        }
    }

    private fun row(ctx: android.content.Context, p: JabraPrefs, host: JabraHost?, it: ClipPop.Item, ink: Int, iconOn: Boolean, rowPx: Int, onPick: (ClipPop.Item) -> Unit): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(JabraTokens.dp(ctx, 14), 0, JabraTokens.dp(ctx, 12), 0)
        r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx)
        val t = TextView(ctx)
        t.text = it.label
        t.textSize = 14f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(if (it.danger) JabraTokens.DANGER else ink)
        if (it.ticked) t.setTypeface(t.typeface, Typeface.BOLD)
        t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        r.addView(t)
        if (it.value.isNotEmpty()) {   // Undo row: the value sits on the right
            val v = TextView(ctx)
            v.text = it.value
            v.textSize = 13f
            v.setTypeface(v.typeface, Typeface.BOLD)
            v.setTextColor(ink)
            v.setPadding(0, 0, JabraTokens.dp(ctx, 6), 0)
            r.addView(v)
        }
        try {
            val name = if (it.ticked) "tick" else it.icon
            if (iconOn && name != null && name in JabraIcons.NAMES) {
                val c = if (it.danger) JabraTokens.DANGER else try { JabraThemes.iconColor(p) } catch (_: Throwable) { ink }
                val iv = ImageView(ctx)
                iv.setImageDrawable(JabraIcons.IconDrawable(name, c))
                val s = JabraTokens.dp(ctx, 18)
                iv.layoutParams = LinearLayout.LayoutParams(s, s)
                r.addView(iv)
            }
        } catch (_: Throwable) { }
        try { r.contentDescription = it.label + (if (it.value.isNotEmpty()) " " + it.value else "") } catch (_: Throwable) { }
        r.isClickable = true
        r.setOnClickListener { v ->
            try { host?.feedback(v) } catch (_: Throwable) { }
            if (it.id != ClipPop.UNDO) dismiss()   // the Undo row keeps the menu open (the caller re-shows it with the new value)
            try { onPick(it) } catch (_: Throwable) { }
        }
        return r
    }
}
