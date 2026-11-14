package com.mgb.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.mgb.keyboard.typing.AppPalette

/** v0.10.9 (D1) thin Android side of AppPalette: reads pref "app_theme" (0 Auto, 1 Light = default, 2 Dark). Any error = Light. */
object AppUi {
    private fun sp(c: Context) = c.getSharedPreferences("jabra", Context.MODE_PRIVATE)

    fun mode(c: Context): Int = try { AppPalette.sanitize(sp(c).getInt("app_theme", AppPalette.LIGHT)) } catch (_: Throwable) { AppPalette.LIGHT }

    fun setMode(c: Context, m: Int) { try { sp(c).edit().putInt("app_theme", AppPalette.sanitize(m)).apply() } catch (_: Throwable) { } }

    fun isDark(c: Context): Boolean = try {
        val night = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        AppPalette.isDark(mode(c), night)
    } catch (_: Throwable) { false }

    fun bg(c: Context): Int = AppPalette.bg(isDark(c))
    fun text(c: Context): Int = AppPalette.text(isDark(c))
    fun dim(c: Context): Int = AppPalette.dim(isDark(c))
    fun warn(c: Context): Int = AppPalette.warn(isDark(c))
    fun ok(c: Context): Int = AppPalette.ok(isDark(c))
    fun accent(c: Context): Int = AppPalette.accent(isDark(c))
    fun outline(c: Context): Int = AppPalette.outline(isDark(c))         // v0.17.76 (U1)
    fun surface2(c: Context): Int = AppPalette.surface2(isDark(c))
    fun accentSoft(c: Context): Int = AppPalette.accentSoft(isDark(c))
    fun onAccent(c: Context): Int = AppPalette.onAccent(isDark(c))
    fun dp(c: Context, v: Int): Int = try { (v * c.resources.displayMetrics.density).toInt() } catch (_: Throwable) { v * 3 }

    /** Rounded card background (radius in dp). */
    fun cardBg(c: Context, radiusDp: Int): Drawable {
        val g = GradientDrawable()
        g.setColor(AppPalette.card(isDark(c)))
        g.cornerRadius = radiusDp * c.resources.displayMetrics.density
        return g
    }

    // ---- v0.17.76 (U1): shared building blocks (the screens use these instead of their own literals; every one has a plain fallback) ----

    /** Card with a 1 dp outline (card colour fill). */
    fun card(c: Context, radiusDp: Int = com.mgb.keyboard.typing.AppTokens.R_CARD): Drawable {
        val g = GradientDrawable()
        g.setColor(AppPalette.card(isDark(c)))
        g.cornerRadius = dp(c, radiusDp).toFloat()
        try { g.setStroke(dp(c, com.mgb.keyboard.typing.AppTokens.STROKE).coerceAtLeast(1), outline(c)) } catch (_: Throwable) { }
        return g
    }

    /** Thin line in the outline colour (horizontal, 1 dp). */
    fun divider(c: Context): android.view.View {
        val v = android.view.View(c)
        v.setBackgroundColor(outline(c))
        v.layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(c, com.mgb.keyboard.typing.AppTokens.STROKE).coerceAtLeast(1))
        return v
    }

    /** Small-caps section title + hairline (the look of the page sections and of the Home headings). */
    fun sectionHeader(c: Context, title: String): android.view.View {
        val box = android.widget.LinearLayout(c)
        box.orientation = android.widget.LinearLayout.VERTICAL
        val t = android.widget.TextView(c)
        t.text = title.uppercase()
        t.textSize = com.mgb.keyboard.typing.AppTokens.T_CAPTION.toFloat()
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.letterSpacing = 0.08f
        t.setTextColor(dim(c))
        t.setPadding(0, dp(c, com.mgb.keyboard.typing.AppTokens.S4), 0, dp(c, com.mgb.keyboard.typing.AppTokens.S1))
        try { t.isAccessibilityHeading = true } catch (_: Throwable) { }
        box.addView(t)
        val line = android.view.View(c)
        line.setBackgroundColor((dim(c) and 0x00FFFFFF) or (com.mgb.keyboard.typing.AppTokens.HAIRLINE_ALPHA shl 24))
        box.addView(line, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp(c, com.mgb.keyboard.typing.AppTokens.STROKE).coerceAtLeast(1)))
        return box
    }

    /** Pill / chip label: selected = accent fill + onAccent text, else surface2 + text colour (readable in light and dark). */
    fun pill(c: Context, label: String, selected: Boolean): android.widget.TextView {
        val t = android.widget.TextView(c)
        t.text = label
        t.textSize = com.mgb.keyboard.typing.AppTokens.T_LABEL.toFloat()
        t.gravity = android.view.Gravity.CENTER
        t.setTextColor(if (selected) onAccent(c) else text(c))
        val g = GradientDrawable()
        g.setColor(if (selected) accent(c) else surface2(c))
        g.cornerRadius = dp(c, com.mgb.keyboard.typing.AppTokens.R_PILL).toFloat()
        t.background = g
        val px = dp(c, com.mgb.keyboard.typing.AppTokens.S3)
        t.setPadding(px, dp(c, com.mgb.keyboard.typing.AppTokens.S1), px, dp(c, com.mgb.keyboard.typing.AppTokens.S1))
        return t
    }
}
