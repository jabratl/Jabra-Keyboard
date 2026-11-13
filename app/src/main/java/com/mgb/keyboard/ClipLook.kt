package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.mgb.keyboard.clip.ClipKind

/**
 * v0.10.24 (C1) the small rounded kind tag (Link / Email / Code / Phone / Bangla) on a clipboard row or card. Label only; plain text = no tag.
 * Colours: the existing accent blue (Code = the existing warning orange). Any problem = null = no tag (the row looks as before).
 */
object ClipLook {
    /** v0.16.15 (E3): colour of PINNED clips = warm gold. Blue is only for SELECTED clips (before: pinned and selected were both blue). */
    fun pinColor(dark: Boolean): Int = if (dark) 0xFFFFD54F.toInt() else 0xFFB7791F.toInt()

    fun tag(ctx: Context, p: JabraPrefs?, text: String): TextView? = try {
        val k = ClipKind.kind(text)
        if (k == ClipKind.NONE) null else {
            val d = ctx.resources.displayMetrics.density
            val dark = try { p != null && JabraTokens.isDark(p) } catch (_: Throwable) { false }
            val c = if (k == ClipKind.CODE) Color.parseColor(if (dark) "#FFAB91" else "#BF360C")
                    else if (dark) JabraTokens.ACCENT_SOFT else JabraTokens.ACCENT
            val t = TextView(ctx)
            t.text = ClipKind.name(k)
            t.textSize = 10f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setTextColor(c)
            t.maxLines = 1
            t.setPadding((5 * d).toInt(), (1 * d).toInt(), (5 * d).toInt(), (1 * d).toInt())
            val g = GradientDrawable()
            g.setColor(JabraTokens.withAlpha(c, 0x2A))
            g.cornerRadius = 8f * d
            t.background = g
            t
        }
    } catch (_: Throwable) { null }
}
