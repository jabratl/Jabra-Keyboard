package com.mgb.keyboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.ClipFold

/**
 * v0.43.0: a collapsible section header = [chevron] [the old header label] [count when folded].
 * The old label view is reused as it is (its tap still swaps Pinned / Recent), only the small chevron on the left folds / unfolds the section.
 * Any problem = the old label view itself (header without chevron, nothing collapses). Switch: clip_fold (default ON).
 */
object ClipFoldHeader {
    fun wrap(ctx: Context, label: View, folded: Boolean, count: Int, dim: Int, onFold: () -> Unit): View {
        try {
            val d = ctx.resources.displayMetrics.density
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val chev = TextView(ctx)
            chev.text = ClipFold.chevron(folded)
            chev.textSize = 13f
            chev.setTextColor(dim)
            chev.gravity = Gravity.CENTER
            chev.minimumHeight = (30 * d).toInt()
            chev.contentDescription = if (folded) "Show clips" else "Hide clips"
            chev.isClickable = true
            chev.setOnClickListener { try { onFold() } catch (_: Throwable) { } }
            row.addView(chev, LinearLayout.LayoutParams((34 * d).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT))
            (label.parent as? android.view.ViewGroup)?.removeView(label)
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))   // the label keeps the whole rest of the row = the old swap tap area
            val cnt = ClipFold.countText(folded, count)
            if (cnt.isNotEmpty()) {
                val c = TextView(ctx)
                c.text = cnt.trim()
                c.textSize = 12f
                c.setTextColor(dim)
                c.setPadding((4 * d).toInt(), 0, (8 * d).toInt(), 0)
                row.addView(c, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            return row
        } catch (_: Throwable) { return label }
    }
}
