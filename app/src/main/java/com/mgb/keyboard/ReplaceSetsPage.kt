package com.mgb.keyboard

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.mgb.keyboard.typing.AppTokens
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceRules
import com.mgb.keyboard.typing.ReplaceSets
import org.json.JSONObject

/*
 * v0.25.0 (R2) SAVED SETS of Find & Replace rules. Pref rp_sets = JSON {name: {r: rules text, c: matchCase 0/1, w: word 0/1}}, up to 20 sets.
 * Goes into Backup / Restore with all other prefs (it is the user's own data, not typing history). Pure rules = typing/ReplaceRules.java.
 */
object ReplaceStore {
    const val KEY = "rp_sets"
    const val MAX = 20

    class Def(var rules: String, var matchCase: Boolean, var word: Boolean) {
        fun opt(): ReplaceEngine.Opt {
            val o = ReplaceEngine.Opt()
            o.mode = if (word) ReplaceEngine.MODE_WORD else ReplaceEngine.MODE_TEXT
            o.matchCase = matchCase; o.bangla = false; o.escapes = false
            return o
        }
    }

    fun fromJson(s: String): LinkedHashMap<String, Def> {
        val out = LinkedHashMap<String, Def>()
        try {
            if (s.isBlank()) return out
            val j = JSONObject(s)
            val it = j.keys()
            while (it.hasNext() && out.size < MAX) {
                val n = it.next()
                val o = j.optJSONObject(n) ?: continue
                out[n.take(40)] = Def(o.optString("r", ""), o.optInt("c", 0) == 1, o.optInt("w", 0) == 1)
            }
        } catch (_: Throwable) { }
        return out
    }

    fun toJson(m: Map<String, Def>): String {
        val j = JSONObject()
        try {
            for ((n, d) in m) {
                val o = JSONObject()
                o.put("r", d.rules); o.put("c", if (d.matchCase) 1 else 0); o.put("w", if (d.word) 1 else 0)
                j.put(n, o)
            }
        } catch (_: Throwable) { }
        return j.toString()
    }

    fun load(prefs: JabraPrefs): LinkedHashMap<String, Def> = try { fromJson(prefs.str(KEY, "")) } catch (_: Throwable) { LinkedHashMap() }
    fun save(prefs: JabraPrefs, m: Map<String, Def>) { try { prefs.setStr(KEY, toJson(m)) } catch (_: Throwable) { } }

    /** Ready-made sets as Defs (read only). */
    fun ready(): LinkedHashMap<String, Def> {
        val m = LinkedHashMap<String, Def>()
        try { for (d in ReplaceSets.all()) m[d.name] = Def(d.text, d.matchCase, false) } catch (_: Throwable) { }
        return m
    }
}

/**
 * v0.25.0 (R2) full-screen page (same pattern as HistPage): list of sets, tap = edit the rules as text (one line per rule),
 * live "N rules, M unreadable lines", Test box, Save / Cancel / Delete (two taps), Export / Import through the clipboard.
 * Everything is in try / catch: a problem = the page does not open, the keyboard panel still works.
 */
object ReplaceSetsPage {
    private fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun button(ctx: Context): Button {
        val b = Button(ctx)
        b.isAllCaps = false
        b.text = "Find & Replace sets (edit rules)"
        b.contentDescription = "Open the Find and Replace rule sets"
        AppRows.styleAction(ctx, b)
        b.setOnClickListener { open(ctx) }
        return b
    }

    /** v0.28.0 (R3): small card for Home (under the page tiles): title + one line, tap = the rule sets page. Any problem = null (no card). */
    fun homeCard(ctx: Context): android.view.View? = try {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S3))
        card.minimumHeight = dp(ctx, 56)
        try { card.background = AppUi.cardBg(ctx, 12) } catch (_: Throwable) { }
        card.isClickable = true
        card.isFocusable = true
        val t = TextView(ctx)
        t.text = "Find & Replace"
        t.textSize = AppTokens.T_TITLE.toFloat()
        t.setTypeface(null, Typeface.BOLD)
        try { t.setTextColor(AppUi.text(ctx)) } catch (_: Throwable) { }
        card.addView(t)
        val s = TextView(ctx)
        s.text = "Rule sets for the Rp key (Korean, Japanese, Chinese, Russian, Arabic...)"
        s.textSize = AppTokens.T_LABEL.toFloat()
        try { s.setTextColor(AppUi.dim(ctx)) } catch (_: Throwable) { }
        card.addView(s)
        card.contentDescription = "Find and Replace rule sets. Opens the rule sets page."
        card.setOnClickListener { open(ctx) }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(ctx, AppTokens.S2)
        card.layoutParams = lp
        card
    } catch (_: Throwable) { null }

    fun open(ctx: Context) { try { show(ctx) } catch (_: Throwable) { } }

    private fun show(ctx: Context) {
        val text = AppUi.text(ctx)
        val dim = AppUi.dim(ctx)
        val warn = AppUi.warn(ctx)
        val prefs = JabraPrefs(ctx)
        val dlg = Dialog(ctx, android.R.style.Theme_Material_NoActionBar)
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(AppUi.bg(ctx))
        root.setPadding(dp(ctx, AppTokens.S3), dp(ctx, AppTokens.S2), dp(ctx, AppTokens.S3), 0)

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(ctx)
        title.text = "Find & Replace sets"
        title.textSize = AppTokens.T_HEAD.toFloat()
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(text)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = Button(ctx)
        close.isAllCaps = false
        close.text = "Close"
        close.minimumHeight = dp(ctx, AppTokens.TOUCH)
        AppRows.styleAction(ctx, close)
        close.setOnClickListener { try { dlg.dismiss() } catch (_: Throwable) { } }
        bar.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bar)

        val scroll = ScrollView(ctx)
        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun label(t: String, color: Int): TextView {
            val v = TextView(ctx)
            v.text = t
            v.textSize = AppTokens.T_BODY.toFloat()
            v.setTextColor(color)
            v.setPadding(0, dp(ctx, AppTokens.S1), 0, dp(ctx, AppTokens.S1))
            return v
        }
        fun act(t: String, onClick: () -> Unit): Button {
            val b = Button(ctx)
            b.isAllCaps = false
            b.text = t
            b.minimumHeight = dp(ctx, AppTokens.TOUCH)
            AppRows.styleAction(ctx, b)
            b.setOnClickListener { try { onClick() } catch (_: Throwable) { } }
            return b
        }
        fun toast(t: String) { try { Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show() } catch (_: Throwable) { } }

        var list: () -> Unit = { }

        // ---------------------------------------------------------------- editor
        fun edit(origName: String?, def: ReplaceStore.Def, readOnly: Boolean) {
            body.removeAllViews()
            val nameBox = EditText(ctx)
            nameBox.setText(origName ?: "")
            nameBox.hint = "Set name"
            nameBox.setTextColor(text)
            nameBox.isEnabled = !readOnly
            nameBox.inputType = InputType.TYPE_CLASS_TEXT
            body.addView(nameBox)

            val rulesBox = EditText(ctx)
            rulesBox.setText(def.rules)
            rulesBox.hint = "one rule per line:\nfind => with\na | b | c => with   (variants)\nre: find => with\ndel: text\ndel-re: pattern\n# comment"
            rulesBox.setTextColor(text)
            rulesBox.typeface = Typeface.MONOSPACE
            rulesBox.textSize = 14f
            rulesBox.minLines = 8
            rulesBox.gravity = Gravity.TOP
            rulesBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            rulesBox.isEnabled = !readOnly
            body.addView(rulesBox)

            val infoTv = label("", dim)
            body.addView(infoTv)

            var mc = def.matchCase
            var wd = def.word
            val flags = LinearLayout(ctx)
            flags.orientation = LinearLayout.HORIZONTAL
            val bCase = act("", { })
            val bWord = act("", { })
            fun flagLabels() { bCase.text = (if (mc) "✓ " else "") + "Match case (Aa)"; bWord.text = (if (wd) "✓ " else "") + "Whole words only" }
            flagLabels()
            if (!readOnly) {
                bCase.setOnClickListener { mc = !mc; flagLabels() }
                bWord.setOnClickListener { wd = !wd; flagLabels() }
            } else { bCase.isEnabled = false; bWord.isEnabled = false }
            flags.addView(bCase, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            flags.addView(bWord, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(flags)

            body.addView(label("Test: type sample text, see the result", dim))
            val sample = EditText(ctx)
            sample.hint = "Sample text"
            sample.setTextColor(text)
            sample.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            body.addView(sample)
            val resultTv = label("", text)
            resultTv.setTextIsSelectable(true)
            body.addView(resultTv)

            fun update() {
                try {
                    val p = ReplaceRules.parse(rulesBox.text.toString())
                    var s = p.active().toString() + (if (p.active() == 1) " rule" else " rules") + ", " + p.bad.size + " unreadable " + (if (p.bad.size == 1) "line" else "lines")
                    if (p.bad.isNotEmpty()) s += " (line " + p.bad.take(5).joinToString(", ") + (if (p.bad.size > 5) "…" else "") + ")"
                    if (p.tooMany) s += "  ·  only the first " + ReplaceRules.MAX_RULES + " rules are used"
                    infoTv.text = s
                    infoTv.setTextColor(if (p.bad.isNotEmpty() || p.tooMany) warn else dim)
                    val o = ReplaceEngine.Opt(); o.mode = if (wd) ReplaceEngine.MODE_WORD else ReplaceEngine.MODE_TEXT; o.matchCase = mc; o.bangla = false
                    val rr = ReplaceRules.run(sample.text.toString(), p.rules, o)
                    resultTv.text = if (sample.text.isEmpty()) "" else rr.out + (if (rr.err != null) "\n⚠ " + rr.err else "")
                } catch (_: Throwable) { }
            }
            val tw = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { }
                override fun afterTextChanged(s: Editable?) { update() }
            }
            rulesBox.addTextChangedListener(tw)
            sample.addTextChangedListener(tw)
            bCase.setOnClickListener { if (!readOnly) { mc = !mc; flagLabels(); update() } }
            bWord.setOnClickListener { if (!readOnly) { wd = !wd; flagLabels(); update() } }
            update()

            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            if (readOnly) {
                row.addView(act("Copy as my set") {
                    val m = ReplaceStore.load(prefs)
                    if (m.size >= ReplaceStore.MAX) { toast("20 sets is the limit"); return@act }
                    var n = (origName ?: "Set") + " (mine)"
                    var k = 2
                    while (m.containsKey(n)) { n = (origName ?: "Set") + " (mine " + k + ")"; k++ }
                    m[n] = ReplaceStore.Def(def.rules, def.matchCase, def.word)
                    ReplaceStore.save(prefs, m)
                    toast("Copied as \"" + n + "\"")
                    list()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            } else {
                row.addView(act("Save") {
                    val n = nameBox.text.toString().trim().take(40)
                    if (n.isEmpty()) { toast("Type a name first"); return@act }
                    val m = ReplaceStore.load(prefs)
                    if (origName == null || n != origName) {
                        if (m.containsKey(n)) { toast("That name is already used"); return@act }
                        if (origName == null && m.size >= ReplaceStore.MAX) { toast("20 sets is the limit"); return@act }
                    }
                    // keep the order: rebuild the map with the changed entry in place
                    val out = LinkedHashMap<String, ReplaceStore.Def>()
                    var placed = false
                    for ((k, v) in m) { if (k == origName) { out[n] = ReplaceStore.Def(rulesBox.text.toString(), mc, wd); placed = true } else out[k] = v }
                    if (!placed) out[n] = ReplaceStore.Def(rulesBox.text.toString(), mc, wd)
                    ReplaceStore.save(prefs, out)
                    toast("Saved")
                    list()
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (origName != null) {
                    var armed = false
                    val del = act("Delete") { }
                    del.setOnClickListener {
                        try {
                            if (!armed) { armed = true; del.text = "Sure? Delete"; return@setOnClickListener }
                            val m = ReplaceStore.load(prefs); m.remove(origName); ReplaceStore.save(prefs, m)
                            toast("Deleted"); list()
                        } catch (_: Throwable) { }
                    }
                    row.addView(del, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
            row.addView(act("Cancel") { list() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(row)
        }

        // ---------------------------------------------------------------- list
        list = {
            body.removeAllViews()
            body.addView(label("Rules run from top to bottom. In the keyboard: Rp > Sets, tap a set, then Replace all. Same one-line syntax as the Formatter rules.", dim))
            val mine = ReplaceStore.load(prefs)
            body.addView(label("My sets (" + mine.size + " / " + ReplaceStore.MAX + ")", text))
            if (mine.isEmpty()) body.addView(label("None yet. Tap New set, or copy a ready-made set below.", dim))
            for ((n, d) in mine) {
                val c = ReplaceRules.parse(d.rules).active()
                body.addView(act(n + "  ·  " + c + (if (c == 1) " rule" else " rules")) { edit(n, d, false) })
            }
            body.addView(act("+ New set") {
                if (mine.size >= ReplaceStore.MAX) toast("20 sets is the limit") else edit(null, ReplaceStore.Def("", false, false), false)
            })
            val io = LinearLayout(ctx)
            io.orientation = LinearLayout.HORIZONTAL
            io.addView(act("Export (copy)") {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Find & Replace sets", ReplaceStore.toJson(ReplaceStore.load(prefs))))
                    toast("Copied")
                } catch (_: Throwable) { toast("Could not copy") }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            io.addView(act("Import (paste)") {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val t = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                    val inc = ReplaceStore.fromJson(t)
                    if (inc.isEmpty()) { toast("Clipboard has no sets"); return@act }
                    val m = ReplaceStore.load(prefs)
                    var added = 0
                    for ((k, v) in inc) {
                        if (m.size >= ReplaceStore.MAX) break
                        var n = k; var i = 2
                        while (m.containsKey(n)) { n = k + " (" + i + ")"; i++ }
                        m[n] = v; added++
                    }
                    ReplaceStore.save(prefs, m)
                    toast("Imported " + added)
                    list()
                } catch (_: Throwable) { toast("Could not import") }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            body.addView(io)
            body.addView(label("Ready-made sets (read only: open, test, or copy as my set)", text))
            for ((n, d) in ReplaceStore.ready()) body.addView(act(n) { edit(n, d, true) })
        }
        list()

        dlg.setContentView(root)
        try { dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) } catch (_: Throwable) { }
        dlg.show()
    }
}
