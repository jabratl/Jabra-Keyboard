package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.ToolbarOrder

/**
 * v0.2.1 "Edit toolbar" page (More settings -> Edit toolbar >, route showPanel("toolbar")).
 * Top: Toolbar Classic / Custom switch (pref tb_custom). Middle (scrolls): one row per item = icon + name + [First] [up] [down] [Hide / Show].
 * Prefs: tb_order (str), tb_hidden (str). Reset toolbar = both cleared + tb_fold OFF. Built in code like the Size page.
 */
class ToolbarPanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)
    private var scrollY = 0   // keeps the list position after a tap (the page is rebuilt on every change)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    private fun pk(label: String, weight: Float, heightDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 11f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(heightDp), weight)
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

    private fun order(): List<String> = ToolbarOrder.parse(prefs.str("tb_order", ""))

    private fun save(l: List<String>, sv: ScrollView?) {
        prefs.setStr("tb_order", ToolbarOrder.serialize(l))
        refresh(sv)
    }

    private fun refresh(sv: ScrollView?) {
        try { if (sv != null) scrollY = sv.scrollY } catch (_: Throwable) { }
        host.showPanel("toolbar")
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // top: Classic / Custom
        val top = row()
        val sw = pk("", 1f, 40) { btn ->
            val nv = !prefs.bool("tb_custom", true)   // v0.2.7: default is now Custom
            prefs.setBool("tb_custom", nv)
            btn.text = "Toolbar: " + (if (nv) "Custom" else "Classic")
        }
        sw.text = "Toolbar: " + (if (prefs.bool("tb_custom", true)) "Custom" else "Classic")
        top.addView(sw)
        root.addView(top)

        // list (scrolls, about 5 rows high)
        val sv = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        val all = order()
        val hidden = prefs.str("tb_hidden", "")
        val on = prefs.str("tb_on", "")
        val nameColor = try { JabraThemes.iconColor(prefs) } catch (_: Throwable) { Color.parseColor("#222222") }
        for (id in all) {
            val isHidden = ToolbarOrder.off(hidden, on, id)
            val r = row()
            val ic = pk("", 0.9f, 40) { }
            ic.isClickable = false
            try { JabraIcons.putThemed(ctx, ic, Toolbar.icon(id)) } catch (_: Throwable) { }
            if (ic.text.isNullOrEmpty() && ic.foreground == null) ic.text = Toolbar.label(id)
            val nm = TextView(ctx)
            nm.text = Toolbar.title(id)
            nm.textSize = 12f
            nm.setTextColor(nameColor)
            nm.gravity = Gravity.CENTER_VERTICAL
            nm.alpha = if (isHidden) 0.45f else 1f
            nm.setPadding(dp(4), 0, 0, 0)
            nm.layoutParams = LinearLayout.LayoutParams(0, dp(40), 2.6f)
            r.addView(ic)
            r.addView(nm)
            r.addView(pk("First", 1.3f, 40) { save(ToolbarOrder.moveFirst(all, id), sv) })
            r.addView(pk("\u25B2", 0.8f, 40) { save(ToolbarOrder.up(all, id), sv) })
            r.addView(pk("\u25BC", 0.8f, 40) { save(ToolbarOrder.down(all, id), sv) })
            r.addView(pk(if (isHidden) "Show" else "Hide", 1.2f, 40) {
                if (ToolbarOrder.isExtra(id)) prefs.setStr("tb_on", ToolbarOrder.setOn(on, id, isHidden))   // v0.2.2: extras are OFF until turned on
                else prefs.setStr("tb_hidden", ToolbarOrder.setHidden(hidden, id, !isHidden))
                refresh(sv)
            })
            col.addView(r)
        }
        sv.addView(col)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5 * 44))
        root.addView(sv)
        sv.post { try { sv.scrollTo(0, scrollY) } catch (_: Throwable) { } }

        root.addView(note("Custom = scrolling toolbar in this order (first item = leftmost, after the fold key). Classic = the old bar. Formatter / Mode items also need Formatter ON / Mode key ON. Extra shortcuts (Emoji ... Size) are Hidden until you press Show. Scroll the list for them. Then ABC (apply)."))

        val rr = row()
        rr.addView(pk("Reset toolbar", 1f, 40) {
            prefs.setStr("tb_order", ""); prefs.setStr("tb_hidden", ""); prefs.setStr("tb_on", ""); prefs.setBool("tb_fold", false)
            scrollY = 0
            host.showPanel("toolbar")
        })
        root.addView(rr)

        val rb = row()
        rb.addView(pk("< More settings", 1f, 40) { scrollY = 0; host.showPanel("more") })
        rb.addView(pk("ABC  (apply)", 1f, 40) { scrollY = 0; host.showLetters() })
        root.addView(rb)
        return root
    }
}
