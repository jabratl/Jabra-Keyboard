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

    /** v0.17.58 (C3): clipboard new look ON (pref clip_new_look, default ON). OFF = every look below is exactly as before. */
    fun newLook(p: JabraPrefs?): Boolean = try { p?.bool("clip_new_look", true) ?: true } catch (_: Throwable) { true }

    /** v0.18.4: tidy clipboard (pref clip_tidy, default ON; needs the new look): list rows become cards with pin LEFT and x RIGHT, no repeated "Pinned" word on cards, the status line keeps its height. OFF = exactly v0.18.3. */
    fun tidy(p: JabraPrefs?): Boolean = try { newLook(p) && (p?.bool("clip_tidy", true) ?: true) } catch (_: Throwable) { false }

    /** v0.17.58 (C3): gold for the WORD "Pinned" (text needs 4.5:1; #B7791F gave only 3.4:1 on the classic grey). New look: darker gold in light themes. OFF = pinColor(). */
    fun pinText(p: JabraPrefs?, dark: Boolean): Int = if (newLook(p) && !dark) 0xFF7A5000.toInt() else pinColor(dark)

    /** v0.17.40 (M7): small "Aa" tag on a card whose clip has a stored format. Theme colours (dim text, NOT blue), tap target >= 28 dp. */
    fun fmtTag(ctx: Context, p: JabraPrefs?): TextView? = try {
        val d = ctx.resources.displayMetrics.density
        val c = try { JabraTokens.onCardDim(p!!) } catch (_: Throwable) { Color.DKGRAY }
        val t = TextView(ctx)
        t.text = "Aa"
        t.textSize = 11f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(c)
        t.maxLines = 1
        t.gravity = android.view.Gravity.CENTER
        t.minimumHeight = (28 * d).toInt()
        t.minimumWidth = (28 * d).toInt()
        t.setPadding((8 * d).toInt(), (2 * d).toInt(), (8 * d).toInt(), (2 * d).toInt())
        val g = GradientDrawable()
        g.setColor(JabraTokens.withAlpha(c, 0x2A))
        g.cornerRadius = JabraTokens.R_SM * d
        t.background = g
        t
    } catch (_: Throwable) { null }

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
