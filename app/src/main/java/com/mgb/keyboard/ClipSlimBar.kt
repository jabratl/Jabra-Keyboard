package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.widget.Button

/**
 * v0.35.0 (CC3-A): one slim select-bar button = small own-drawn icon + ONE short word.
 * Any problem = the plain UiKit button (old look). Icon too wide for the button = icon only (full word stays as spoken name).
 */
object ClipSlimBar {
    private const val ICON_DP = 14

    fun btn(ctx: Context, p: JabraPrefs, word: String, icon: String?, weight: Float, hDp: Int,
            feedback: ((View) -> Unit)?, onClick: (Button) -> Unit): Button {
        val b = UiKit.btn(ctx, p, word, weight, hDp, 0f, feedback, onClick)
        try {
            b.foreground = null            // Glyphs.dress may have replaced the word by an icon: the slim bar wants icon + word
            b.text = word
            b.contentDescription = word
            b.textSize = 11.5f
            b.maxLines = 1
            b.ellipsize = TextUtils.TruncateAt.END
            val d4 = JabraTokens.dp(ctx, 4)
            b.setPadding(d4, 0, d4, 0)
            if (icon != null && p.bool("icons_on", true) && icon in JabraIcons.NAMES) {
                val col = try { JabraThemes.iconColor(p) } catch (_: Throwable) { Color.parseColor("#222222") }
                val d = JabraIcons.IconDrawable(icon, col)
                val s = JabraTokens.dp(ctx, ICON_DP)
                d.setBounds(0, 0, s, s)
                b.setCompoundDrawablesRelative(d, null, null, null)
                b.compoundDrawablePadding = JabraTokens.dp(ctx, 3)
                var done = false
                b.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                    if (!done && r - l > 0) {
                        done = true
                        try {
                            val bt = v as Button
                            val need = bt.paint.measureText(word) + s + JabraTokens.dp(ctx, 3) + 2 * d4
                            if (need > (r - l)) { bt.text = ""; bt.setCompoundDrawablesRelative(null, null, null, null); bt.foreground = JabraIcons.IconDrawable(icon, col) }   // does not fit: icon only
                        } catch (_: Throwable) { }
                    }
                }
            }
        } catch (_: Throwable) { }
        return b
    }
}
