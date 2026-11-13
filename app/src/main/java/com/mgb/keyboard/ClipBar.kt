package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button
import com.mgb.keyboard.typing.BarInk
import com.mgb.keyboard.typing.EdgePlan
import com.mgb.keyboard.typing.LookColors

/**
 * v0.17.30 (UI6): small look helpers for the buttons at the top of the Clipboard panel, so they follow the keyboard keys.
 *  - func():   "ABC" = function-key fill (the same colour as ?123 / Shift / Backspace)
 *  - on():     a switched-on mode button ("Done" in select mode) = the soft "selected" blue, corner radius like the keys
 *  - pill():   small header-line buttons (Undo / Sort / View / Manage) = capsule, like the space bar
 *  - danger(): "Clear" = warm red label (only when it reads well on the key colour)
 * LOOK ONLY. Every helper does nothing unless Look = Jabra (Classic look stays exactly as today), never throws,
 * never changes size / weight / margin / click code. Nothing here is stored.
 * Delete this file + the 9 hook lines marked "v0.17.30" in ClipboardFeature.kt = back to v0.17.29.
 */
object ClipBar {

    private fun jabra(p: JabraPrefs): Boolean = try { JabraLook.on(p) } catch (_: Throwable) { false }

    /** "ABC": function key look. */
    fun func(p: JabraPrefs, b: Button): Button {
        try { if (jabra(p)) JabraLook.shape(p, b, JabraLook.FUNC) } catch (_: Throwable) { }
        return b
    }

    /** Small header-line button: capsule like the space bar. */
    fun pill(p: JabraPrefs, b: Button): Button {
        try { if (jabra(p)) JabraLook.shape(p, b, JabraLook.PILL) } catch (_: Throwable) { }
        return b
    }

    /** A mode that is ON right now: soft blue (BLUE = SELECTED), bold dark label, corners like the keys. */
    fun on(ctx: Context, p: JabraPrefs, b: Button): Button {
        try {
            if (!jabra(p)) return b
            val d = ctx.resources.displayMetrics.density
            val ct = JabraThemes.customActive(p)
            val rDp: Float = if (ct != null) ct.radiusDp() else EdgePlan.radiusDp(p.int("key_radius", -1)).toFloat()
            val up = GradientDrawable()
            up.setColor(JabraTokens.ACCENT_SOFT)
            up.cornerRadius = rDp * d
            val down = GradientDrawable()
            down.setColor(LookColors.darken(JabraTokens.ACCENT_SOFT, 0.18f))
            down.cornerRadius = rDp * d
            val sl = StateListDrawable()
            sl.addState(intArrayOf(android.R.attr.state_pressed), down)
            sl.addState(intArrayOf(), up)
            b.background = sl
            b.backgroundTintList = null
            b.stateListAnimator = null
            b.setTextColor(JabraTokens.ON_ACCENT_SOFT)
            b.setTypeface(b.typeface, Typeface.BOLD)
        } catch (_: Throwable) { }
        return b
    }

    /** Destructive action ("Clear"): warm red label. If neither red reads on the key colour, the old label colour stays. */
    fun danger(p: JabraPrefs, b: Button): Button {
        try {
            if (!jabra(p)) return b
            val fill = JabraTokens.btnFill(p) ?: Color.WHITE
            val ink = BarInk.pick(fill, 0xFFB3261C.toInt(), 0xFFFF8A80.toInt())
            if (ink != 0) b.setTextColor(ink)
        } catch (_: Throwable) { }
        return b
    }
}
