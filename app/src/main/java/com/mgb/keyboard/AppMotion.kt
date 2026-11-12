package com.mgb.keyboard

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.mgb.keyboard.typing.AppTokens

/**
 * v0.14.0 (M4): the only place that moves things in the NEW app screen. A short fade (+ small rise) when a page or the search results appear.
 * Respects the phone's "Animator duration scale": 0 = no motion at all (the view just shows). Any error = the view is simply visible.
 */
object AppMotion {
    fun scale(c: Context): Float = try {
        Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: Throwable) { 1f }

    private fun rest(v: View) { try { v.alpha = 1f; v.translationY = 0f } catch (_: Throwable) { } }

    fun fadeIn(v: View, baseMs: Int = AppTokens.PAGE_MS, rise: Boolean = true) {
        try {
            v.animate().cancel()
            val ms = AppTokens.ms(baseMs, scale(v.context))
            if (ms <= 0) { rest(v); return }
            v.alpha = 0f
            v.translationY = if (rise) AppTokens.slidePx(v.resources.displayMetrics.density).toFloat() else 0f
            v.animate().alpha(1f).translationY(0f).setDuration(ms.toLong())
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { rest(v) }
                .start()
        } catch (_: Throwable) { rest(v) }
    }
}
