package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.mgb.keyboard.typing.SizePct

/**
 * v0.1.4 "Size" page (More settings -> Size >): keyboard height % and one-hand width %, portrait AND landscape each.
 * Prefs: kh_pct_p / kh_pct_l (height %, 70..150) and oh_w_p / oh_w_l (one-hand width %, 50..95). Unset (-1) = old behaviour
 * (old kh_p / kh_l levels and 82% / 60% width are read as fallback, never changed). Built in code like the other panels.
 */
object SizeKeys {
    fun heightKey(land: Boolean): String = if (land) "kh_pct_l" else "kh_pct_p"
    fun widthKey(land: Boolean): String = if (land) "oh_w_l" else "oh_w_p"
    const val GAP = "key_gap"   // v0.3.0: gap between keys in dp (unset = 2)
    const val BOLD = "letter_bold"   // v0.2.9: bold letters (default OFF)
    const val LETTER = "letter_pct"   // v0.2.8: letter text size % (unset = old 14 sp)
}

class SizePanel(private val ctx: Context, private val host: JabraHost) {

    private val prefs = JabraPrefs(ctx)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // v0.10.14 (D2.4): pk() = UiKit.btn (key-height tall, 12f, same 2dp margins, theme style, feedback first). Old body kept as pkOld() = fallback.
    private fun pk(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, host.keyHeightDp(), 12f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return pkOld(label, weight, onClick)
    }

    private fun pkOld(label: String, weight: Float, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(host.keyHeightDp()), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    // v0.10.14 (D2.4): row() = UiKit.row (same horizontal LinearLayout). Old body kept as rowOld() = automatic fallback.
    private fun row(): LinearLayout {
        try { return UiKit.row(ctx) } catch (_: Throwable) { }
        return rowOld()
    }

    private fun rowOld(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    // v0.10.14 (D2.4): note() = UiKit.noteText (11sp, note colour, 6 / 2dp padding). Old body kept as noteOld() = fallback.
    private fun note(text: String): TextView {
        try { return UiKit.noteText(ctx, prefs, text) } catch (_: Throwable) { }
        return noteOld(text)
    }

    private fun noteOld(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.setTextColor(try { JabraThemes.noteColor(prefs) } catch (_: Throwable) { Color.parseColor("#555555") })
        t.setPadding(dp(6), dp(2), dp(6), dp(2))
        return t
    }

    /** Height % now shown for this orientation (unset = the percent that matches the old Small / Medium / Large / XL level). */
    private fun heightNow(land: Boolean): Int {
        val v = prefs.int(SizeKeys.heightKey(land), SizePct.UNSET)
        return if (SizePct.heightValid(v)) v else SizePct.pctFromDp(prefs.keyHeightDp(land))
    }

    private fun widthNow(land: Boolean): Int {
        val v = prefs.int(SizeKeys.widthKey(land), SizePct.UNSET)
        return if (SizePct.widthValid(v)) v else Math.round(SizePct.widthFrac(SizePct.UNSET, land) * 100f)
    }

    private fun label(title: String, v: Int, unit: String = "%"): String = title + ": " + v + unit

    /** One line: [ - ] [ title: value% ] [ + ]. */
    private fun sizeRow(title: String, get: () -> Int, set: (Int) -> Unit, min: Int, max: Int, step: Int, unit: String = "%"): LinearLayout {
        val r = row()
        val mid = pk(label(title, get(), unit), 3f) { }
        mid.isClickable = false
        val minus = pk("-", 1f) { val nv = SizePct.step(get(), -1, min, max, step); set(nv); mid.text = label(title, nv, unit) }
        val plus = pk("+", 1f) { val nv = SizePct.step(get(), 1, min, max, step); set(nv); mid.text = label(title, nv, unit) }
        r.addView(minus); r.addView(mid); r.addView(plus)
        return r
    }

    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.2.6
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        for (land in booleanArrayOf(false, true)) {
            val tail = if (land) " landscape" else " portrait"
            root.addView(sizeRow("Height" + tail, { heightNow(land) }, { prefs.setInt(SizeKeys.heightKey(land), it) }, SizePct.H_MIN, SizePct.H_MAX, SizePct.H_STEP))
        }
        for (land in booleanArrayOf(false, true)) {
            val tail = if (land) " landscape" else " portrait"
            root.addView(sizeRow("One-hand width" + tail, { widthNow(land) }, { prefs.setInt(SizeKeys.widthKey(land), it) }, SizePct.W_MIN, SizePct.W_MAX, SizePct.W_STEP))
        }

        // v0.2.8: letter (alphabet) size, one value for portrait + landscape
        root.addView(sizeRow("Letter size", { val v = prefs.int(SizeKeys.LETTER, SizePct.UNSET); if (SizePct.letterValid(v)) v else 100 }, { prefs.setInt(SizeKeys.LETTER, it) }, SizePct.L_MIN, SizePct.L_MAX, SizePct.L_STEP))

        // v0.3.0: gap between keys (smaller gap = bigger keys, easier to hit)
        root.addView(sizeRow("Key gap", { SizePct.gapDp(prefs.int(SizeKeys.GAP, SizePct.G_DEF)) }, { prefs.setInt(SizeKeys.GAP, it) }, SizePct.G_MIN, SizePct.G_MAX, 1, " dp"))

        // v0.2.9: bold letters toggle
        val rbold = row()
        rbold.addView(pk(if (prefs.bool(SizeKeys.BOLD, false)) "Bold letters: ON" else "Bold letters: OFF", 1f) { b ->
            val nv = !prefs.bool(SizeKeys.BOLD, false); prefs.setBool(SizeKeys.BOLD, nv)
            b.text = if (nv) "Bold letters: ON" else "Bold letters: OFF"
        })
        root.addView(rbold)

        root.addView(note("Letter size: 100% = old size; use + for bigger letters. Height: 100% = 48 dp = old Medium. One-hand width needs One-hand Left / Right (More settings). Then ABC (apply). Reset = back to the old Height levels and 82% / 60% width."))

        val rr = row()
        rr.addView(pk("Reset sizes", 1f) { btn ->
            for (land in booleanArrayOf(false, true)) { prefs.setInt(SizeKeys.heightKey(land), SizePct.UNSET); prefs.setInt(SizeKeys.widthKey(land), SizePct.UNSET) }
            prefs.setInt(SizeKeys.LETTER, SizePct.UNSET)   // v0.2.8
            prefs.setBool(SizeKeys.BOLD, false)   // v0.2.9
            prefs.setInt(SizeKeys.GAP, SizePct.UNSET)   // v0.3.0
            host.showPanel("size")
        })
        root.addView(rr)

        val rb = row()
        rb.addView(pk("< More settings", 1f) { host.showPanel("more") })
        rb.addView(pk("ABC  (apply)", 1f) { host.showLetters() })
        root.addView(rb)
        return root
    }
}
