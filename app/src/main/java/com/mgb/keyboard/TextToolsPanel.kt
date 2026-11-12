package com.mgb.keyboard

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.TextTools
import com.mgb.keyboard.typing.TextTools2   // v0.10.16 (W5)

/*
 * v0.4.6 TEXT TOOLS panel (route showPanel("tt"), toolbar item "tt", OFF until the user adds it).
 * Pages: Count | Case | Clean | Repeat | Date | More (v0.10.16: lines / digits / URL / Base64 / JSON). Source = the selected text, else the whole field (same reader as the Formatter: FmtIo).
 * The field is read when the panel opens and after a Replace / Undo, NOT on every key (nothing runs on the key-press path).
 * Replace on the selection = one tap; Replace on the WHOLE field = two taps ("Sure?") and an Undo button afterwards.
 * Same grey look as ClipPanel / VaultPanel. build() returns null on any problem -> the keyboard shows the letters.
 */
class TextToolsPanel(private val ctx: Context, private val host: JabraHost, private val io: FmtIo) {

    private val prefs = JabraPrefs(ctx)

    private var page = 0                       // 0 Count, 1 Case, 2 Clean, 3 Repeat, 4 Date
    private var src = ""                       // text being worked on
    private var usedSel = false                // true = src is the selection
    private var caseMode = TextTools.UPPER
    private var optSpaces = true
    private var optBlank = true
    private var optTrail = true
    private var repN = 2
    private var repSep = 0
    private var datePreset = 0
    private var moreTool = TextTools2.SORT_AZ   // v0.10.16 (W5) page 5 "More": selected tool
    private var joinSep = 0
    private var moreErr = ""                    // message of the last failed More tool (nothing is changed then)
    private var msg = ""
    private var confirmAll = false
    private var undoRes: String? = null        // text we inserted (for Undo)
    private var undoOrig = ""

    /** Call when the panel is opened from outside: reads the field once. */
    fun reset() {
        msg = ""; confirmAll = false; undoRes = null
        readSource()
    }

    private fun readSource() {
        try {
            if (io.blocked()) { src = ""; usedSel = false; msg = "Not available in password fields."; return }
            val sel = io.selected()
            if (!sel.isNullOrEmpty()) { src = sel; usedSel = true }
            else { src = (io.wholeText() ?: "").take(100000); usedSel = false }
        } catch (_: Throwable) { src = ""; usedSel = false }
    }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private fun refresh() { host.showPanel("tt") }

    // v0.6.7 theme: buttons = UiKit.btn (12sp, follows theme / Jabra look / Borderless), old body kept as btnOld() = automatic fallback
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try { return UiKit.btn(ctx, prefs, label, weight, hDp, 12f, { v -> host.feedback(v) }, onClick) } catch (_: Throwable) { }
        return btnOld(label, weight, hDp, onClick)
    }

    private fun paintBg(v: View) { try { JabraThemes.paint(prefs, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }   // v0.6.7
    private fun dim(): Int = try { JabraTokens.dimText(prefs) } catch (_: Throwable) { Color.DKGRAY }   // v0.6.7

    private fun btnOld(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.textSize = 12f
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), if (weight > 0f) weight else 0f)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    /** The result text of the current page (computed here, only when the panel is drawn). */
    private fun result(): String = try {
        when (page) {
            0 -> TextTools.countsLine(src)
            1 -> TextTools.caseConvert(src, caseMode)
            2 -> TextTools.clean(src, optSpaces, optBlank, optTrail)
            3 -> TextTools.repeat(src, repN, TextTools.SEPARATORS[repSep])
            5 -> more()
            else -> TextTools.dateTime(datePreset, System.currentTimeMillis(), java.util.TimeZone.getDefault())
        }
    } catch (_: Throwable) { "" }

    /** v0.10.16 (W5): result of the selected More tool; an error message goes to moreErr and the result stays empty. */
    private fun more(): String = try {
        val r = TextTools2.apply(moreTool, src, joinSep)
        moreErr = r.err ?: ""
        r.out
    } catch (_: Throwable) { moreErr = "Could not do this"; "" }

    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)   // v0.6.7
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // tabs
        val tabs = row()
        tabs.addView(btn("ABC", 0.8f, 38) { host.showLetters() })   // v0.10.16: 0.8 (was 1) so the 6th tab "More" fits at 360 dp
        val names = arrayOf("Count", "Case", "Clean", "Repeat", "Date", "More")
        for (i in 0..5) {
            tabs.addView(btn((if (page == i) "✓ " else "") + names[i], 1.25f, 38) { page = i; confirmAll = false; msg = ""; refresh() })
        }
        root.addView(tabs)

        // options of the page
        val opt = row()
        val opt2 = row()   // v0.10.16 (W5): tool rows of the More page
        val opt3 = row()
        when (page) {
            1 -> {
                val n = arrayOf("UPPER", "lower", "Title", "Sentence")
                for (i in 0..3) opt.addView(btn((if (caseMode == i) "✓ " else "") + n[i], 1f, 32) { caseMode = i; confirmAll = false; refresh() })
            }
            2 -> {
                opt.addView(btn((if (optSpaces) "✓ " else "") + "Extra spaces", 1f, 32) { optSpaces = !optSpaces; confirmAll = false; refresh() })
                opt.addView(btn((if (optBlank) "✓ " else "") + "Blank lines", 1f, 32) { optBlank = !optBlank; confirmAll = false; refresh() })
                opt.addView(btn((if (optTrail) "✓ " else "") + "Line ends", 1f, 32) { optTrail = !optTrail; confirmAll = false; refresh() })
            }
            3 -> {
                opt.addView(btn("−", 0.6f, 32) { if (repN > 1) repN--; confirmAll = false; refresh() })
                opt.addView(btn(repN.toString() + "×", 0.8f, 32) { })
                opt.addView(btn("+", 0.6f, 32) { if (repN < TextTools.MAX_REPEAT) repN++; confirmAll = false; refresh() })
                opt.addView(btn("Between: " + TextTools.SEPARATOR_NAMES[repSep], 2f, 32) { repSep = (repSep + 1) % TextTools.SEPARATORS.size; confirmAll = false; refresh() })
            }
            4 -> {
                val now = System.currentTimeMillis()
                val tz = java.util.TimeZone.getDefault()
                opt.addView(btn("◀", 0.6f, 32) { datePreset = (datePreset + TextTools.DATE_FORMATS.size - 1) % TextTools.DATE_FORMATS.size; refresh() })
                opt.addView(btn(TextTools.dateTime(datePreset, now, tz), 3f, 32) { datePreset = (datePreset + 1) % TextTools.DATE_FORMATS.size; refresh() })
                opt.addView(btn("▶", 0.6f, 32) { datePreset = (datePreset + 1) % TextTools.DATE_FORMATS.size; refresh() })
            }
            5 -> {   // v0.10.16 (W5): group chips (Lines / Digits / Encode / JSON), then the tools of the group
                val grp = TextTools2.groupOf(moreTool)
                for (g in TextTools2.GROUP_NAMES.indices) {
                    opt.addView(btn((if (grp == g) "✓ " else "") + TextTools2.GROUP_NAMES[g], 1f, 32) {
                        if (grp != g) moreTool = TextTools2.firstOf(g)
                        confirmAll = false; refresh()
                    })
                }
                val tools = TextTools2.GROUP_TOOLS[grp]
                for ((k, tl) in tools.withIndex()) {
                    var label = (if (moreTool == tl) "✓ " else "") + TextTools2.TOOL_NAMES[tl]
                    if (tl == TextTools2.JOIN) label += ": " + TextTools2.JOIN_NAMES[joinSep.coerceIn(0, TextTools2.JOIN_NAMES.size - 1)]
                    val target = if (k < 4) opt2 else opt3
                    target.addView(btn(label, if (tl == TextTools2.JOIN) 1.6f else 1f, 32) {
                        if (tl == TextTools2.JOIN && moreTool == tl) joinSep = (joinSep + 1) % TextTools2.JOIN_SEPS.size   // tap again = next separator
                        moreTool = tl
                        confirmAll = false; refresh()
                    })
                }
            }
        }
        if (page != 0) root.addView(opt)
        if (page == 5) {
            root.addView(opt2)
            if (opt3.childCount > 0) root.addView(opt3)
        }

        // info line
        val shown = msg
        msg = ""
        val srcInfo = if (page == 4) "Inserts at the cursor" else if (src.isEmpty()) "No text: select text or type some first" else (if (usedSel) "Source: selection" else "Source: whole field") + " (" + src.length + " characters)"
        val info = TextView(ctx)
        info.text = if (shown.isNotEmpty()) shown else if (page == 5 && moreErr.isNotEmpty()) moreErr else srcInfo
        info.textSize = 12f
        info.setTextColor(dim())   // v0.6.7
        info.setPadding(dp(8), dp(2), dp(8), dp(2))
        root.addView(info)

        // result preview
        val res = result()
        val sv = ScrollView(ctx)
        sv.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72))
        val tv = TextView(ctx)
        tv.text = if (res.length > 600) res.substring(0, 600) + " …" else if (res.isEmpty()) "(nothing)" else res
        tv.textSize = 14f
        tv.setTextColor(try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK })   // v0.6.7: result box = key colour surface (Classic = black on white as before)
        tv.setBackgroundColor(try { if (JabraTokens.isClassic(prefs)) Color.WHITE else (JabraTokens.btnFill(prefs) ?: Color.WHITE) } catch (_: Throwable) { Color.WHITE })
        tv.setPadding(dp(8), dp(4), dp(8), dp(4))
        sv.addView(tv)
        root.addView(sv)

        // actions
        val act = row()
        val textPage = page in 1..3 || page == 5   // v0.10.16: More uses the same Replace + Undo flow
        if (textPage) {
            val ask = confirmAll && !usedSel
            act.addView(btn(if (ask) "Sure? Replace all" else "Replace", 1.6f, 40) {
                if (src.isEmpty() || res.isEmpty()) { msg = if (page == 5 && moreErr.isNotEmpty()) moreErr else "Nothing to replace"; refresh() }
                else if (res == src) { msg = "No change"; refresh() }
                else if (!usedSel && !confirmAll) { confirmAll = true; refresh() }
                else {
                    val ok = if (usedSel) io.replaceSelection(res) else io.replaceAll(res)
                    confirmAll = false
                    if (ok) { undoRes = res; undoOrig = src; msg = "Replaced"; readSource() } else msg = "Could not replace in this app (use Copy)"
                    refresh()
                }
            })
        } else {
            act.addView(btn("Insert", 1.6f, 40) {
                if (res.isEmpty()) msg = "Nothing to insert"
                else {
                    host.commitWord()
                    host.typeText(res)
                    host.showLetters()
                }
                if (res.isEmpty()) refresh()
            })
        }
        act.addView(btn("Copy", 1f, 40) {
            msg = if (res.isNotEmpty() && io.setClip(res, null)) "Copied" else "Could not copy"
            confirmAll = false
            refresh()
        })
        if (undoRes != null && textPage) {
            act.addView(btn("↶ Undo", 1f, 40) {
                val r = undoRes
                msg = if (r != null && io.undoReplace(r, undoOrig)) "Undone" else "Cannot undo (text changed)"
                undoRes = null
                readSource()
                refresh()
            })
        }
        root.addView(act)
        return root
    }
}
