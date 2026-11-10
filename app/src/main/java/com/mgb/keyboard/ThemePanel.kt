package com.mgb.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.LookColors

/**
 * v0.2.4 theme picker (route showPanel("themes"), More settings -> Theme picker >).
 * Grid of small preview tiles drawn in code: background + 3 mini keys (2 normal, 1 function key) + name.
 * Blue frame + check mark = chosen theme. Tap a tile = pref "theme" = that index (same pref as the old cycle button), page redraws.
 * Press ABC (apply) to see it on the keyboard. build() returns null on any problem -> keyboard shows normal letters.
 */
class ThemePanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var scrollY = 0
    private var keep = false   // true right after a tap: keep the list position

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun pk(label: String, weight: Float): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 14f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(40), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun note(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** One preview tile, all drawn in code. */
    private inner class Tile(c: Context, private val idx: Int, private val chosen: Boolean) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val t = JabraThemes.ALL[idx]
            val w = width.toFloat()
            val h = height.toFloat()
            val r = dp(8).toFloat()
            val txt = t.text ?: Color.parseColor("#222222")
            p.style = Paint.Style.FILL
            p.color = t.bg
            rect.set(0f, 0f, w, h)
            canvas.drawRoundRect(rect, r, r, p)
            val keyC = t.key ?: Color.WHITE
            val fnC = LookColors.funcFill(idx)
            val pad = dp(8).toFloat()
            val gap = dp(4).toFloat()
            val kw = (w - 2f * pad - 2f * gap) / 3f
            val top = dp(15).toFloat()
            val kh = dp(20).toFloat()
            for (i in 0..2) {
                p.color = if (i == 2) fnC else keyC
                val l = pad + i * (kw + gap)
                rect.set(l, top, l + kw, top + kh)
                canvas.drawRoundRect(rect, dp(4).toFloat(), dp(4).toFloat(), p)
            }
            p.color = txt
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(11).toFloat()
            canvas.drawText(t.name, w / 2f, h - dp(9).toFloat(), p)
            p.style = Paint.Style.STROKE
            if (chosen) {
                p.strokeWidth = dp(3).toFloat()
                p.color = Color.parseColor("#2271B1")
            } else {
                p.strokeWidth = dp(1).toFloat()
                p.color = (txt and 0x00FFFFFF) or 0x55000000
            }
            rect.set(p.strokeWidth / 2f, p.strokeWidth / 2f, w - p.strokeWidth / 2f, h - p.strokeWidth / 2f)
            canvas.drawRoundRect(rect, r, r, p)
            if (chosen) {
                val cx = w - dp(11).toFloat()
                val cy = dp(11).toFloat()
                p.style = Paint.Style.FILL
                p.color = Color.parseColor("#2271B1")
                canvas.drawCircle(cx, cy, dp(8).toFloat(), p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp(2).toFloat()
                p.color = Color.WHITE
                canvas.drawLine(cx - dp(4).toFloat(), cy, cx - dp(1).toFloat(), cy + dp(3).toFloat(), p)
                canvas.drawLine(cx - dp(1).toFloat(), cy + dp(3).toFloat(), cx + dp(4).toFloat(), cy - dp(3).toFloat(), p)
            }
        }
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val cols = 3
        val cur = JabraThemes.index(prefs)
        if (!keep) scrollY = maxOf(0, (cur / cols) - 1) * dp(70)
        keep = false

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { root.setBackgroundColor(JabraThemes.bg(prefs)) } catch (_: Throwable) { }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val sv = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val n = JabraThemes.ALL.size
        var i = 0
        while (i < n) {
            val r = row()
            for (j in 0 until cols) {
                val idx = i + j
                if (idx < n) {
                    val tile = Tile(ctx, idx, idx == cur)
                    val lp = LinearLayout.LayoutParams(0, dp(64), 1f)
                    lp.setMargins(dp(3), dp(3), dp(3), dp(3))
                    tile.layoutParams = lp
                    tile.setOnClickListener {
                        try { host.feedback(tile) } catch (_: Throwable) { }
                        prefs.setInt("theme", idx)
                        try { scrollY = sv.scrollY } catch (_: Throwable) { }
                        keep = true
                        host.showPanel("themes")
                    }
                    r.addView(tile)
                } else {
                    val sp = View(ctx)
                    sp.layoutParams = LinearLayout.LayoutParams(0, dp(64), 1f)
                    r.addView(sp)
                }
            }
            col.addView(r)
            i += cols
        }
        sv.addView(col)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3 * 70))
        root.addView(sv)
        sv.post { try { sv.scrollTo(0, scrollY) } catch (_: Throwable) { } }

        root.addView(note("Tap a tile to choose a theme (blue frame = current). Then ABC (apply). The old Theme: tap-to-cycle button in More settings still works."))

        val rb = row()
        val back = pk("< More settings", 1f)
        back.setOnClickListener { try { host.feedback(back) } catch (_: Throwable) { }; scrollY = 0; host.showPanel("more") }
        val abc = pk("ABC  (apply)", 1f)
        abc.setOnClickListener { try { host.feedback(abc) } catch (_: Throwable) { }; scrollY = 0; host.showLetters() }
        rb.addView(back)
        rb.addView(abc)
        root.addView(rb)
        return root
    }
}
