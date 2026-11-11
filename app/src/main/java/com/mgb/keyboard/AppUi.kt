package com.mgb.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.typing.AppPalette

/**
 * v0.10.9 (D1) app screen look: light / dark / auto. Colours come from typing/AppPalette.java.
 * Pref app_theme (int 0 auto / 1 light / 2 dark, default 1 = exactly today's look). App-only setting: NOT in ProfileCodec, not part of the keyboard themes.
 * Every function is try/catch and falls back to the light look. Light mode changes nothing except the help-text colour (#888888 -> #6B6B6B).
 */
object AppUi {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    fun mode(ctx: Context): Int = try { AppPalette.normalize(sp(ctx).getInt("app_theme", 1)) } catch (_: Throwable) { 1 }

    fun setMode(ctx: Context, m: Int) { try { sp(ctx).edit().putInt("app_theme", AppPalette.normalize(m)).apply() } catch (_: Throwable) { } }

    private fun phoneDark(ctx: Context): Boolean = try {
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    } catch (_: Throwable) { false }

    fun isDark(ctx: Context): Boolean = try { AppPalette.pick(mode(ctx), phoneDark(ctx)) } catch (_: Throwable) { false }

    fun bg(ctx: Context): Int = AppPalette.bg(isDark(ctx))
    fun text(ctx: Context): Int = AppPalette.text(isDark(ctx))
    fun dim(ctx: Context): Int = AppPalette.dim(isDark(ctx))
    fun warn(ctx: Context): Int = AppPalette.warn(isDark(ctx))
    fun ok(ctx: Context): Int = AppPalette.ok(isDark(ctx))

    /** Rounded card background (dp radius). */
    fun cardBg(ctx: Context, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(AppPalette.card(isDark(ctx)))
        g.cornerRadius = radiusDp * ctx.resources.displayMetrics.density
        return g
    }

    /** Settings group header as a card. DARK ONLY (light keeps the system button = looks as before). */
    fun styleHead(ctx: Context, b: Button) {
        try {
            if (!isDark(ctx)) return
            val d = ctx.resources.displayMetrics.density
            b.background = cardBg(ctx, 12)
            b.setTextColor(AppPalette.text(true))
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.minHeight = (48 * d).toInt()
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (12 * d).toInt()
            b.layoutParams = lp
        } catch (_: Throwable) { }
    }
}
