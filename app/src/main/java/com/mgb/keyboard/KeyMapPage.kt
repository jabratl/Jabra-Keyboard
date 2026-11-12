package com.mgb.keyboard

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.KeyMapData
import com.mgb.keyboard.typing.LangNames

/**
 * v0.16.4 (plan X, X4): the "Bangলা key map" reference page of the app screen.
 * A button (in Languages & layouts > Bangla section) opens a full-screen page: search box, then the tables
 * Vowels + kar | Consonants | Signs | Conjuncts | Rules + examples. Two columns like the Ridmik screenshots
 * (Bangla letter big, what you type in the accent colour). Search by roman (ng, Ng) or by Bangla (ঙ).
 * All data + search = typing/KeyMapData.java (every row is checked against the real engine in tools/KeyMapDataTest.java).
 * App screen only: the keyboard, the prefs and the typing are not touched. Colours = AppUi (follows the app Light / Dark setting).
 * Everything in try / catch: a failure = no button / no page, the settings page stays as it was.
 */
object KeyMapPage {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    /** The button for the settings page. */
    fun button(ctx: Context): Button {
        val b = Button(ctx)
        b.isAllCaps = false
        b.text = "Open key map (all letters, rules, examples)"
        b.contentDescription = "Open the " + LangNames.AVRO + " key map: all letters, rules and examples"
        AppRows.styleAction(ctx, b)
        b.setOnClickListener { open(ctx) }
        return b
    }

    /** Opens the page. Any problem = nothing happens. */
    fun open(ctx: Context) {
        try { show(ctx) } catch (_: Throwable) { }
    }

    private fun show(ctx: Context) {
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val accent = AppUi.accent(ctx)

        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)
        root.isFocusable = true
        root.isFocusableInTouchMode = true   // the search box does not grab the focus (no keyboard pops up) until it is tapped

        // ---- top bar: title + Close ----
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = LangNames.AVRO + " key map"
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(text)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(ctx)
        close.isAllCaps = false
        close.text = "Close"
        close.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, close)   // same flat look as the other app buttons (own layout params are given below)
        close.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
        bar.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar)

        // ---- search ----
        val search = EditText(ctx)
        search.hint = "Search: type ঙ or ng"
        search.setSingleLine(true)
        search.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        search.setTextColor(text)
        search.setHintTextColor(dim)
        search.textSize = 16f
        search.background = AppUi.cardBg(ctx, 8)
        search.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
        search.minimumHeight = dp(ctx, AppTokens.TOUCH)
        search.contentDescription = "Search the key map by letters you type or by Bangla"
        val slp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        slp.topMargin = dp(ctx, AppTokens.S2)
        root.addView(search, slp)

        val status = TextView(ctx)
        status.textSize = 12f
        status.setTextColor(dim)
        status.setPadding(dp(ctx, AppTokens.S1), dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S1))
        root.addView(status)

        // ---- scrolling content ----
        val sv = ScrollView(ctx)
        val content = LinearLayout(ctx)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(0, 0, 0, dp(ctx, AppTokens.S4))
        sv.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- small builders ----
        fun header(t: String) {
            val h = TextView(ctx)
            h.text = t
            h.textSize = 16f
            h.setTypeface(null, Typeface.BOLD)
            h.setTextColor(text)
            h.setPadding(dp(ctx, AppTokens.S1), dp(ctx, AppTokens.S3), 0, dp(ctx, AppTokens.S1))
            content.addView(h)
        }

        fun line(t: String) {
            val v = TextView(ctx)
            v.text = t
            v.textSize = 13f
            v.setTextColor(text)
            v.setPadding(dp(ctx, AppTokens.S1), dp(ctx, 3), dp(ctx, AppTokens.S1), dp(ctx, 3))
            content.addView(v)
        }

        // one table cell: big Bangla letter + roman in the accent colour (+ small note)
        fun cell(row: KeyMapData.Row): View {
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.background = AppUi.cardBg(ctx, 8)
            box.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
            val top = LinearLayout(ctx)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            val bn = TextView(ctx)
            bn.text = KeyMapData.shown(row.bangla)
            bn.textSize = 18f
            bn.setTextColor(text)
            top.addView(bn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val ro = TextView(ctx)
            ro.text = row.roman
            ro.textSize = 14f
            ro.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            ro.setTextColor(accent)
            top.addView(ro, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            box.addView(top)
            if (row.note.isNotEmpty()) {
                val nt = TextView(ctx)
                nt.text = row.note
                nt.textSize = 11f
                nt.setTextColor(dim)
                box.addView(nt)
            }
            box.contentDescription = "Type " + row.roman + " to get " + KeyMapData.shown(row.bangla) + (if (row.note.isNotEmpty()) ". " + row.note else "")
            return box
        }

        // rows of 2 cells (like the screenshots); an odd last cell gets an empty partner so it keeps half the width
        fun table(rows: List<KeyMapData.Row>) {
            var i = 0
            while (i < rows.size) {
                val pair = LinearLayout(ctx)
                pair.orientation = LinearLayout.HORIZONTAL
                for (k in 0..1) {
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.setMargins(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
                    val j = i + k
                    if (j < rows.size) pair.addView(cell(rows[j]), lp) else pair.addView(View(ctx), lp)
                }
                content.addView(pair, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                i += 2
            }
        }

        fun examples(list: List<KeyMapData.Example>) {
            for (x in list) {
                val box = LinearLayout(ctx)
                box.orientation = LinearLayout.VERTICAL
                box.background = AppUi.cardBg(ctx, 8)
                box.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2))
                val top = LinearLayout(ctx)
                top.orientation = LinearLayout.HORIZONTAL
                top.gravity = Gravity.CENTER_VERTICAL
                val ro = TextView(ctx)
                ro.text = x.roman
                ro.textSize = 14f
                ro.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                ro.setTextColor(accent)
                top.addView(ro, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val bn = TextView(ctx)
                bn.text = "\u2192  " + KeyMapData.shown(x.bangla)
                bn.textSize = 18f
                bn.setTextColor(text)
                top.addView(bn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                box.addView(top)
                if (x.rule.isNotEmpty()) {
                    val nt = TextView(ctx)
                    nt.text = x.rule
                    nt.textSize = 11f
                    nt.setTextColor(dim)
                    box.addView(nt)
                }
                box.contentDescription = "Type " + x.roman + " to get " + KeyMapData.shown(x.bangla) + ". " + x.rule
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
                content.addView(box, lp)
            }
        }

        fun render(q: String) {
            content.removeAllViews()
            val query = q.trim()
            if (query.isEmpty()) {
                status.text = "Type letters (ng, Ng) or Bangla (ঙ) to search."
                for (gi in 0 until 4) {
                    val g = KeyMapData.GROUPS[gi]
                    header(g)
                    table(KeyMapData.rowsOf(g))
                }
                header(KeyMapData.G_RULES)
                for (r in KeyMapData.rules()) line("\u2022 " + r)
                header("Examples (type the left text, then space)")
                examples(KeyMapData.examples())
                return
            }
            val rows = KeyMapData.search(query)
            val exs = KeyMapData.searchExamples(query)
            val rules = KeyMapData.searchRules(query)
            status.text = rows.size.toString() + " letters, " + exs.size + " examples, " + rules.size + " rules for \"" + query + "\""
            if (rows.isEmpty() && exs.isEmpty() && rules.isEmpty()) {
                line("Nothing found. Try the letters you type (like ng) or the Bangla letter (like ঙ).")
                return
            }
            if (rows.isNotEmpty()) { header("Letters"); table(rows) }
            if (rules.isNotEmpty()) { header("Rules"); for (r in rules) line("\u2022 " + r) }
            if (exs.isNotEmpty()) { header("Examples"); examples(exs) }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(s: Editable?) {
                try { render(s?.toString() ?: "") } catch (_: Throwable) { }
            }
        })

        render("")
        dlg.setContentView(root)
        try {
            val w = dlg.window
            w?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w?.setBackgroundDrawable(ColorDrawable(AppUi.bg(ctx)))
            w?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        } catch (_: Throwable) { }
        dlg.show()
        try { root.requestFocus() } catch (_: Throwable) { }
    }
}
