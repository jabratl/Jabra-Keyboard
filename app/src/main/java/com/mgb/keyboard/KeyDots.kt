package com.mgb.keyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.Button

/**
 * v0.10.27 (P2): three small dots in the bottom-right corner of a key that really has a long-press action (like Ridmik's "..." marks).
 * Pref key_dots (default OFF = nothing attached). Drawn as the button's foreground (API 23+, minSdk is 24), so touch, hints and the
 * key background are not touched. Any problem = no dots.
 */
object KeyDots {
    fun attach(b: Button, prefs: JabraPrefs) {
        try {
            if (!prefs.bool("key_dots", false)) return
            val col = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { 0xFF222222.toInt() }
            b.foreground = DotsDrawable(b.resources.displayMetrics.density, col)
        } catch (_: Throwable) { }
    }
}

private class DotsDrawable(private val d: Float, dotColor: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.color = dotColor
        paint.alpha = 130
    }

    override fun draw(c: Canvas) {
        val b = bounds
        val r = 1.1f * d
        val step = 3.6f * d
        val cy = b.bottom - 5f * d
        val cx = b.right - 6f * d
        for (i in 0 until 3) c.drawCircle(cx - i * step, cy, r, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
