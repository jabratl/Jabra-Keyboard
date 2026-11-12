package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.KeyMapData
import com.mgb.keyboard.typing.LangNames

/**
 * v0.16.5 (plan X, X5): keyboard panel "keymap" = compact cheat sheet of the Ridmik key map (route showPanel("keymap")).
 * Opened from the toolbar item "keymap" (an extra, OFF until you turn it on) and from the gear panel button "Key map".
 * Top: ABC (back to the letters) + title. Then chips: Vowels | Consonants | Signs | Conjuncts | Examples. Tap a chip = its rows
 * (Bangla letter + what you type, 3 per line). Data = typing/KeyMapData.java (every row is checked against the real engine in KeyMapDataTest).
 * Reads and writes NO pref. Keeps the letters-keyboard height ([fitPx], same idea as the clipboard) and scrolls when the list is longer
 * (MgbIme.capTall, name "keymap"). build() returns null on any problem -> the keyboard shows the letters.
 */
class KeyMapPanel(private val ctx: Context, private val host: JabraHost) {

    /** Height of the letters keyboard in px (0 = unknown). Set by MgbIme.showPanel before build(). */
    var fitPx: Int = 0

    private var sel = 0   // 0..3 = KeyMapData.GROUPS index, 4 = examples

    fun reset() { sel = 0 }

    private val pf: JabraPrefs by lazy { JabraPrefs(ctx) }
    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun refresh() { host.showPanel("keymap") }

    private fun dim(): Int = try { JabraTokens.dimText(pf) } catch (_: Throwable) { Color.DKGRAY }
    private fun fg(): Int = try { if (JabraTokens.isDark(pf)) Color.WHITE else Color.BLACK } catch (_: Throwable) { Color.BLACK }
    private fun accent(): Int = try { if (JabraTokens.isDark(pf)) Color.parseColor("#8AB4F8") else Color.parseColor("#1A5FB4") } catch (_: Throwable) { Color.parseColor("#1A5FB4") }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        try { JabraThemes.paint(pf, root) } catch (_: Throwable) { root.setBackgroundColor(Color.parseColor("#DDDDDD")) }
        root.setPadding(dp(2), dp(4), dp(2), dp(4))
        if (fitPx > 0) root.minimumHeight = fitPx

        // top: ABC + title
        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(UiKit.btn(ctx, pf, "ABC", 0f, 38, 12f, { v -> host.feedback(v) }) { host.showLetters() })
        val title = TextView(ctx)
        title.text = LangNames.AVRO + " key map"
        title.textSize = 14f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(fg())
        title.setPadding(dp(8), 0, 0, 0)
        top.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        root.addView(top)

        // chips (scroll sideways when they do not fit)
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("Vowels", "Consonants", "Signs", "Conjuncts", "Examples")
        for (i in names.indices) {
            val c = UiKit.chip(ctx, pf, names[i], i == sel, 30, { v -> host.feedback(v) }) { sel = i; refresh() }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            chips.addView(c, lp)
        }
        hs.addView(chips)
        root.addView(hs)

        // content
        if (sel in 0..3) {
            val rows = KeyMapData.rowsOf(KeyMapData.GROUPS[sel])
            var i = 0
            while (i < rows.size) {
                val line = LinearLayout(ctx)
                line.orientation = LinearLayout.HORIZONTAL
                for (k in 0..2) {
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(dp(2), dp(1), dp(2), dp(1))
                    val j = i + k
                    if (j < rows.size) line.addView(cell(rows[j].bangla, rows[j].roman), lp) else line.addView(View(ctx), lp)
                }
                root.addView(line)
                i += 3
            }
        } else {
            val ex = KeyMapData.examples()
            var i = 0
            while (i < ex.size) {
                val line = LinearLayout(ctx)
                line.orientation = LinearLayout.HORIZONTAL
                for (k in 0..1) {
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(dp(2), dp(1), dp(2), dp(1))
                    val j = i + k
                    if (j < ex.size) line.addView(cell(ex[j].bangla, ex[j].roman), lp) else line.addView(View(ctx), lp)
                }
                root.addView(line)
                i += 2
            }
        }
        return root
    }

    /** One small cell: Bangla (big) + the roman you type (accent colour). */
    private fun cell(bangla: String, roman: String): View {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(dp(6), dp(3), dp(6), dp(3))
        try {
            val g = android.graphics.drawable.GradientDrawable()
            g.setColor(if (JabraTokens.isDark(pf)) Color.parseColor("#33FFFFFF") else Color.parseColor("#22000000"))
            g.cornerRadius = dp(6).toFloat()
            box.background = g
        } catch (_: Throwable) { }
        val bn = TextView(ctx)
        bn.text = KeyMapData.shown(bangla)
        bn.textSize = 16f
        bn.setTextColor(fg())
        box.addView(bn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val ro = TextView(ctx)
        ro.text = roman
        ro.textSize = 12f
        ro.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        ro.setTextColor(accent())
        box.addView(ro)
        box.contentDescription = "Type " + roman + " to get " + KeyMapData.shown(bangla)
        return box
    }
}
