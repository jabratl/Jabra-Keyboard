package com.mgb.keyboard

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import com.mgb.keyboard.typing.LookColors

/**
 * v0.1.5 Jabra look: round-corner keys, own colour for function keys, pill space key, own popup bubble.
 * Pref look (int): 0 = Classic = the old look, nothing is changed (default); 1 = Jabra.
 * shape() is called from JabraThemes.style() (all keys that use the themes) and for the function / space keys in MgbIme.
 * Never throws out; on any problem the key keeps its old look.
 */
object JabraLook {
    const val NORMAL = 0
    const val FUNC = 1
    const val PILL = 2

    fun on(p: JabraPrefs): Boolean = p.int("look", 0) == 1

    /** Normal key fill: the theme key colour, white for the Classic theme. */
    fun fill(p: JabraPrefs): Int = JabraThemes.cur(p).key ?: 0xFFFFFFFF.toInt()

    /** v0.2.6 Key border: Borderless = flat key (theme bg colour, transparent-ish on gradient themes), soft highlight while pressed. */
    private fun flat(p: JabraPrefs, b: Button) {
        val d = b.resources.displayMetrics.density
        val t = JabraThemes.cur(p)
        val base = if (t.bg2 != null) ((t.key ?: 0xFFFFFFFF.toInt()) and 0x00FFFFFF) or 0x2A000000 else t.bg
        val press = ((t.text ?: 0xFF222222.toInt()) and 0x00FFFFFF) or 0x33000000
        val up = GradientDrawable()
        up.setColor(base)
        up.cornerRadius = 8f * d
        val down = GradientDrawable()
        down.setColor(press)
        down.cornerRadius = 8f * d
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), down)
        sl.addState(intArrayOf(), up)
        b.background = sl
        b.backgroundTintList = null
        b.stateListAnimator = null
    }

    fun shape(p: JabraPrefs, b: Button, kind: Int) {
        try {
            if (JabraThemes.borderless(p)) { flat(p, b); return }   // v0.2.6
            if (!on(p)) return
            val d = b.resources.displayMetrics.density
            val ct = JabraThemes.customActive(p)   // v0.4.8: custom theme = own roundness + key opacity (only when theme 20 is chosen)
            var base = if (kind == FUNC) JabraThemes.funcFill(p) else fill(p)
            if (ct != null) base = ct.withKeyAlpha(base)
            val radius = if (kind == PILL) 200f * d else if (ct != null) ct.radiusDp() * d else 9f * d
            val up = GradientDrawable()
            up.setColor(base)
            up.cornerRadius = radius
            val down = GradientDrawable()
            down.setColor(LookColors.darken(base, 0.18f))
            down.cornerRadius = radius
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            b.background = sl
            b.backgroundTintList = null
            b.stateListAnimator = null
        } catch (_: Throwable) { }
    }
}
