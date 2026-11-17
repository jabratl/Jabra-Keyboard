package com.mgb.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.ClipFloat

/**
 * v0.37.0: the FLOATING select bar. A rounded pill (icon + one word per action) that floats over the bottom of the clip list,
 * only while clips are selected. It takes no layout height (negative top margin) - the list gets bottom padding instead (ClipboardFeature hook).
 * Any problem = build() returns null and the caller uses the old slim bar. Pref clip_float (default ON), OFF = exactly v0.36.5.
 */
object ClipFloatBar {
    class Act(val label: String, val icon: String?, val weight: Float, val danger: Boolean, val onTap: () -> Unit)
    class Built(val wrap: View, val pill: View)

    fun build(ctx: Context, p: JabraPrefs, acts: List<Act>, barDp: Int, animate: Boolean, feedback: (View) -> Unit): Built? {
        try {
            if (acts.isEmpty()) return null
            val ink = JabraTokens.cardText(p)
            val iconOn = try { p.bool("icons_on", true) } catch (_: Throwable) { true }
            val iconCol = try { JabraThemes.iconColor(p) } catch (_: Throwable) { ink }
            val pillPx = JabraTokens.dp(ctx, ClipFloat.pillDp(barDp))
            val gapPx = JabraTokens.dp(ctx, ClipFloat.GAP_BOTTOM_DP)

            val pill = LinearLayout(ctx)
            pill.orientation = LinearLayout.HORIZONTAL
            pill.gravity = Gravity.CENTER_VERTICAL
            pill.setPadding(JabraTokens.dp(ctx, 6), 0, JabraTokens.dp(ctx, 6), 0)
            try {
                val g = GradientDrawable()
                g.setColor(JabraTokens.withAlpha(JabraTokens.cardFill(p, false), 0xFF))   // opaque: list text must never show through
                g.setStroke(JabraTokens.dp(ctx, 1).coerceAtLeast(1), JabraTokens.cardStroke(p, true, false))
                g.cornerRadius = pillPx / 2f
                pill.background = g
            } catch (_: Throwable) { pill.setBackgroundColor(Color.WHITE) }
            try { pill.elevation = JabraTokens.dp(ctx, 6).toFloat() } catch (_: Throwable) { }

            for (a in acts) pill.addView(cell(ctx, a, ink, iconCol, iconOn, pillPx, feedback))

            val wrap = LinearLayout(ctx)
            wrap.orientation = LinearLayout.HORIZONTAL
            wrap.gravity = Gravity.CENTER
            val side = JabraTokens.dp(ctx, ClipFloat.SIDE_DP)
            wrap.setPadding(side, 0, side, gapPx)
            wrap.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pillPx))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = -(pillPx + gapPx)   // pulled over the list: net layout height = 0
            wrap.layoutParams = lp
            if (animate) {
                try {
                    wrap.alpha = 0f
                    wrap.translationY = JabraTokens.dp(ctx, 10).toFloat()
                    wrap.animate().alpha(1f).translationY(0f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
                } catch (_: Throwable) { wrap.alpha = 1f; wrap.translationY = 0f }
            }
            return Built(wrap, pill)
        } catch (_: Throwable) { return null }
    }

    private fun cell(ctx: Context, a: Act, ink: Int, iconCol: Int, iconOn: Boolean, hPx: Int, feedback: (View) -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER
        c.layoutParams = LinearLayout.LayoutParams(0, hPx, a.weight)
        val col = if (a.danger) JabraTokens.DANGER else ink
        try {
            val mask = GradientDrawable(); mask.setColor(Color.WHITE); mask.cornerRadius = hPx / 2f
            c.background = RippleDrawable(ColorStateList.valueOf(JabraTokens.withAlpha(ink, 0x30)), ColorDrawable(Color.TRANSPARENT), mask)
        } catch (_: Throwable) { }
        try {
            if (iconOn && a.icon != null && a.icon in JabraIcons.NAMES) {
                val iv = ImageView(ctx)
                iv.setImageDrawable(JabraIcons.IconDrawable(a.icon, if (a.danger) JabraTokens.DANGER else iconCol))
                val s = JabraTokens.dp(ctx, ClipFloat.ICON_DP)
                iv.layoutParams = LinearLayout.LayoutParams(s, s).also { it.rightMargin = JabraTokens.dp(ctx, 4) }
                c.addView(iv)
            }
        } catch (_: Throwable) { }
        val t = TextView(ctx)
        t.text = a.label
        t.textSize = ClipFloat.TEXT_SP.toFloat()
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(col)
        c.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c.contentDescription = a.label
        c.isClickable = true
        c.setOnClickListener { v ->
            try { feedback(v) } catch (_: Throwable) { }
            try { a.onTap() } catch (_: Throwable) { }
        }
        return c
    }
}
