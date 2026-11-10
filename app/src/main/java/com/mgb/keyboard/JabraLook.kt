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

    fun shape(p: JabraPrefs, b: Button, kind: Int) {
        try {
            if (!on(p)) return
            val d = b.resources.displayMetrics.density
            val base = if (kind == FUNC) LookColors.funcFill(JabraThemes.index(p)) else fill(p)
            val radius = if (kind == PILL) 200f * d else 9f * d
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
