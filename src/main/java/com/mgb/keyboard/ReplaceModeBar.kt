package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * v0.61.0 (MERGE step 1): the [Replace | Terms] mode switch.
 * Replace and Fix terms are ONE place now: this small bar sits at the top of every Replace view (New, Diff, Mini, Slim)
 * and at the top of the Terms body (FixNamesUi). Tapping the other side just switches the body, nothing is lost.
 * Built in try / catch by the callers; any error = no bar, the panel works as before.
 */
object ReplaceModeBar {
    /** The full-width bar (used on top of the Replace views). */
    fun bar(ctx: Context, prefs: JabraPrefs, host: JabraHost, termsOn: Boolean, onReplace: () -> Unit, onTerms: () -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        c.setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 4))
        c.layoutParams = lp
        c.addView(seg(ctx, prefs, host, "Replace", !termsOn, 1f, onReplace))
        c.addView(seg(ctx, prefs, host, "Terms", termsOn, 1f, onTerms))
        return c
    }

    /** The compact two-button group (used inside the Terms header, next to the novel button). */
    fun compact(ctx: Context, prefs: JabraPrefs, host: JabraHost, termsOn: Boolean, onReplace: () -> Unit, onTerms: () -> Unit): View {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        try { c.background = UiKit.cardBackground(ctx, prefs, false, false) } catch (_: Throwable) { }
        c.setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
        c.addView(seg(ctx, prefs, host, "Replace", !termsOn, 0f, onReplace))
        c.addView(seg(ctx, prefs, host, "Terms", termsOn, 0f, onTerms))
        return c
    }

    private fun seg(ctx: Context, prefs: JabraPrefs, host: JabraHost, text: String, on: Boolean, weight: Float, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 13f
        t.gravity = Gravity.CENTER
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.maxLines = 1
        t.isClickable = true
        t.setPadding(dp(ctx, 10), 0, dp(ctx, 10), 0)
        if (on) {
            val g = GradientDrawable()
            g.setColor(try { JabraTokens.ACCENT } catch (_: Throwable) { 0xFF2574BD.toInt() })
            g.cornerRadius = dp(ctx, 8).toFloat()
            t.background = g
            t.setTextColor(Color.WHITE)
        } else {
            t.setBackgroundColor(Color.TRANSPARENT)
            t.setTextColor(try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY })
        }
        val lp = if (weight > 0f) LinearLayout.LayoutParams(0, dp(ctx, 32), weight) else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(ctx, 32))
        t.layoutParams = lp
        t.setOnClickListener { v -> try { host.feedback(v) } catch (_: Throwable) { }; if (!on) click() }
        return t
    }

    private fun dp(ctx: Context, v: Int): Int = JabraTokens.dp(ctx, v)
}
