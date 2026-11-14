package com.mgb.keyboard

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.ToolbarOrder

/**
 * v0.15.5 (N5): inline toolbar editor for the app screen (Tools & Panels > Toolbar).
 * Same data and rules as the keyboard ToolbarPanel: tb_order / tb_hidden / tb_on, Reset = order + hidden + on cleared and tb_fold OFF.
 * All list logic comes from typing/ToolbarOrder.java (nothing duplicated). Colours: AppUi / AppPalette only. Rows 48 dp.
 * Every call is inside try / catch; if building fails the caller simply shows no editor (the old rows stay).
 */
object AppToolbarEditor {
    class Handle(val view: View, val refresh: () -> Unit)

    fun build(ctx: Context, prefs: JabraPrefs, onChange: () -> Unit): Handle {
        fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        var armed = false

        fun small(label: String, desc: String, enabled: Boolean, minW: Int, click: () -> Unit): Button {
            val b = Button(ctx)
            b.text = label
            b.contentDescription = desc
            AppRows.styleAction(ctx, b)
            b.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(AppTokens.TOUCH))
            b.minWidth = dp(minW); b.minimumWidth = dp(minW)
            b.setPadding(dp(AppTokens.S2), 0, dp(AppTokens.S2), 0)
            b.textSize = 13f
            b.isEnabled = enabled
            b.alpha = if (enabled) 1f else 0.4f
            try { Glyphs.dress(b); b.contentDescription = desc } catch (_: Throwable) { }   // v0.17.72 (S5): drawn arrows, spoken name stays "Move X up"
            b.setOnClickListener { try { click() } catch (_: Throwable) { } }
            return b
        }

        fun fill() {
            list.removeAllViews()
            val all = ToolbarOrder.parse(prefs.str("tb_order", ""))
            val hidden = prefs.str("tb_hidden", "")
            val on = prefs.str("tb_on", "")
            fun changed() { fill(); try { onChange() } catch (_: Throwable) { } }
            for ((i, id) in all.withIndex()) {
                val off = ToolbarOrder.off(hidden, on, id)
                val line = LinearLayout(ctx)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER_VERTICAL
                line.minimumHeight = dp(AppTokens.TOUCH)
                val ic = Button(ctx)   // JabraIcons draws on a Button; here it is only a picture
                ic.background = null
                ic.minWidth = 0; ic.minimumWidth = 0; ic.minHeight = 0; ic.minimumHeight = 0
                ic.setPadding(0, 0, 0, 0)
                ic.isClickable = false; ic.isFocusable = false
                var drawn = false
                try { drawn = JabraIcons.apply(ic, Toolbar.icon(id), true, AppUi.text(ctx)) } catch (_: Throwable) { }
                if (!drawn) { ic.text = Toolbar.label(id); ic.textSize = 12f; ic.setTextColor(AppUi.text(ctx)) }
                ic.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                ic.alpha = if (off) 0.45f else 1f
                line.addView(ic, LinearLayout.LayoutParams(dp(28), dp(28)))
                val nm = TextView(ctx)
                nm.text = Toolbar.title(id) + (if (off) " (hidden)" else "")
                nm.textSize = 14f
                nm.maxLines = 2
                nm.setTextColor(if (off) AppUi.dim(ctx) else AppUi.text(ctx))
                nm.setPadding(dp(AppTokens.S2), 0, dp(AppTokens.S1), 0)
                line.addView(nm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val name = Toolbar.title(id)
                line.addView(small("First", "Move $name to the first place", i > 0, 44) {
                    prefs.setStr("tb_order", ToolbarOrder.serialize(ToolbarOrder.moveFirst(all, id))); changed()
                })
                line.addView(small("▲", "Move $name up", i > 0, 40) {
                    prefs.setStr("tb_order", ToolbarOrder.serialize(ToolbarOrder.up(all, id))); changed()
                })
                line.addView(small("▼", "Move $name down", i < all.size - 1, 40) {
                    prefs.setStr("tb_order", ToolbarOrder.serialize(ToolbarOrder.down(all, id))); changed()
                })
                line.addView(small(if (off) "Show" else "Hide", (if (off) "Show " else "Hide ") + name + " on the toolbar", true, 52) {
                    if (ToolbarOrder.isExtra(id)) prefs.setStr("tb_on", ToolbarOrder.setOn(on, id, off))   // extras are OFF until turned on
                    else prefs.setStr("tb_hidden", ToolbarOrder.setHidden(hidden, id, !off))
                    changed()
                })
                list.addView(line)
            }
        }

        val note = TextView(ctx)
        note.text = "Order of the keyboard toolbar (first = leftmost). Same list as in the keyboard: gear > More settings > Edit toolbar. The keyboard uses it when it opens next time."
        note.textSize = 12f
        note.setTextColor(AppUi.dim(ctx))
        note.setPadding(0, dp(AppTokens.S2), 0, dp(AppTokens.S1))
        box.addView(note)
        fill()
        box.addView(list)

        val rb = Button(ctx)
        rb.isAllCaps = false
        rb.text = "Reset toolbar"
        AppRows.styleAction(ctx, rb)
        rb.setOnClickListener {
            try {
                if (!armed) {
                    armed = true
                    rb.text = "Tap again: reset order, hidden items and fold"
                    rb.setTextColor(AppUi.warn(ctx))
                    rb.postDelayed({ armed = false; rb.text = "Reset toolbar"; rb.setTextColor(AppUi.text(ctx)) }, 3000)
                } else {
                    armed = false
                    rb.text = "Reset toolbar"
                    rb.setTextColor(AppUi.text(ctx))
                    prefs.setStr("tb_order", ""); prefs.setStr("tb_hidden", ""); prefs.setStr("tb_on", ""); prefs.setBool("tb_fold", false)
                    fill()
                    try { onChange() } catch (_: Throwable) { }
                }
            } catch (_: Throwable) { }
        }
        box.addView(rb)
        return Handle(box) { try { fill() } catch (_: Throwable) { } }
    }
}
