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

    /** Rounded card background (radius in dp). */
    fun cardBg(c: Context, radiusDp: Int): Drawable {
        val g = GradientDrawable()
        g.setColor(AppPalette.card(isDark(c)))
        g.cornerRadius = radiusDp * c.resources.displayMetrics.density
        return g
    }
}
