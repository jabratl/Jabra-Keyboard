package com.mgb.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.formatter.NovelFormatter
import org.json.JSONObject

/*
 * v0.0.8 Novel formatter (port of novel-formatter_v2_updated.html). All new code lives in this file +
 * formatter/NovelFormatter.java (pure Java engine, tested against the original JS).
 * MgbIme.kt only has tiny hooks marked "v0.0.8". Every hook is try/catch; if anything fails the keyboard works as before.
 * Prefs file "jabra", NEW keys only: fmt_on(bool,true) fmt_cur(json) fmt_presets(json) fmt_src("text"|"clip"|"html") fmt_prev("result"|"original")
 * Option keys inside fmt_cur / fmt_presets = the HTML tool's names, so the presets JSON is the same format (nvPresets / nvCurrent).
 * No new permission, no network. Nothing is read from the app's text unless the user taps a button in the panel.
 */

/** What the formatter needs from the keyboard / phone. Implemented by ImeFmtIo. */
interface FmtIo {
    fun blocked(): Boolean                       // password field: formatter does nothing
    fun selected(): String?
    fun wholeText(): String?
    fun replaceSelection(s: String): Boolean     // commits text (replaces selection if any)
    fun replaceAll(s: String): Boolean           // replaces the whole field (checked, never inserts twice)
    fun undoReplace(res: String, orig: String): Boolean
    fun clipText(): String?
    fun clipHtml(): String?
    fun setClip(plain: String, html: String?): Boolean
}

class ImeFmtIo(private val svc: InputMethodService, private val isPwd: () -> Boolean) : FmtIo {
    private fun ic() = svc.currentInputConnection
    private fun cm() = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun blocked(): Boolean = try { isPwd() } catch (_: Throwable) { false }

    override fun selected(): String? = try { ic()?.getSelectedText(0)?.toString() } catch (_: Throwable) { null }

    /** Ordered stages (data): 1 = editor's extracted text, 2 = text around the cursor. A failed stage is logged, then the next one runs. */
    override fun wholeText(): String? {
        val stages = listOf<Pair<String, () -> String?>>(
            "extracted" to { ic()?.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString() },
            "around" to {
                val c = ic()
                if (c == null) null else {
                    val b = c.getTextBeforeCursor(100000, 0)?.toString() ?: ""
                    val s = c.getSelectedText(0)?.toString() ?: ""
                    val a = c.getTextAfterCursor(100000, 0)?.toString() ?: ""
                    b + s + a
                }
            }
        )
        for ((name, f) in stages) {
            try { val r = f(); if (!r.isNullOrEmpty()) return r } catch (e: Throwable) { android.util.Log.d("JabraFmt", "read stage " + name + " failed: " + e) }
        }
        return null
    }

    override fun replaceSelection(s: String): Boolean {
        val c = ic() ?: return false
        return try { c.commitText(s, 1) } catch (_: Throwable) { false }
    }

    /** Select everything (stage 1 = editor action, stage 2 = setSelection), verify the selection really is everything, then commit. */
    override fun replaceAll(s: String): Boolean {
        val c = ic() ?: return false
        val whole = wholeText() ?: return false
        var ok = false
        try {
            c.beginBatchEdit()
            val methods = listOf<Pair<String, () -> Boolean>>(
                "selectAll" to { c.performContextMenuAction(android.R.id.selectAll); true },
                "setSelection" to { c.setSelection(0, whole.length); true }
            )
            for ((name, m) in methods) {
                try {
                    m()
                    val n = c.getSelectedText(0)?.length ?: -1
                    if (n == whole.length) { ok = true; break }
                } catch (e: Throwable) { android.util.Log.d("JabraFmt", "select stage " + name + " failed: " + e) }
            }
            if (ok) ok = c.commitText(s, 1)
        } catch (_: Throwable) {
            ok = false
        } finally {
            try { c.endBatchEdit() } catch (_: Throwable) { }
        }
        return ok
    }

    /** Undo only if the text we inserted is still right before the cursor. */
    override fun undoReplace(res: String, orig: String): Boolean {
        val c = ic() ?: return false
        return try {
            val before = c.getTextBeforeCursor(res.length, 0)?.toString()
            if (before != res) false else {
                c.beginBatchEdit()
                c.deleteSurroundingText(res.length, 0)
                val ok = c.commitText(orig, 1)
                c.endBatchEdit()
                ok
            }
        } catch (_: Throwable) { false }
    }

    override fun clipText(): String? = try {
        val d = cm().primaryClip
        if (d == null || d.itemCount == 0) null else d.getItemAt(0).coerceToText(svc)?.toString()
    } catch (_: Throwable) { null }

    override fun clipHtml(): String? = try {
        val d = cm().primaryClip
        if (d == null || d.itemCount == 0) null else d.getItemAt(0).htmlText
    } catch (_: Throwable) { null }

    override fun setClip(plain: String, html: String?): Boolean = try {
        val d = if (html != null) ClipData.newHtmlText("jabra", plain, html) else ClipData.newPlainText("jabra", plain)
        cm().setPrimaryClip(d)
        true
    } catch (_: Throwable) { false }
}

/** Current settings (like nvCurrent) and user presets (like nvPresets), saved in the "jabra" prefs. */
class FmtStore(private val prefs: JabraPrefs) {
    val defaults: Map<String, String> = NovelFormatter.defaults()
    private val boolKeys: Set<String> = defaults.filter { it.value == "true" || it.value == "false" }.keys

    val builtin: Map<String, Map<String, String>> = linkedMapOf(
        "Default" to mapOf<String, String>(),
        "Classic novel (indent, tight)" to mapOf("indent" to "true", "indentVal" to "2", "spacing" to "0", "align" to "justify", "lh" to "1.7"),
        "Web reader (no indent, gap)" to mapOf("indent" to "false", "spacing" to "1.2", "lh" to "1.8", "align" to "none")
    )

    private fun toJson(m: Map<String, String>): JSONObject {
        val j = JSONObject()
        for ((k, v) in m) { if (k in boolKeys) j.put(k, v == "true") else j.put(k, v) }
        return j
    }

    private fun fromJson(j: JSONObject): MutableMap<String, String> {
        val m = LinkedHashMap<String, String>()
        val it = j.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k in defaults) m[k] = j.get(k).toString()
        }
        return m
    }

    fun current(): MutableMap<String, String> {
        val m = LinkedHashMap<String, String>(defaults)
        try {
            val s = prefs.str("fmt_cur", "")
            if (s.isNotEmpty()) m.putAll(fromJson(JSONObject(s)))
        } catch (_: Throwable) { }
        return m
    }

    fun saveCurrent(m: Map<String, String>) { try { prefs.setStr("fmt_cur", toJson(m).toString()) } catch (_: Throwable) { } }

    fun put(k: String, v: String) { val m = current(); m[k] = v; saveCurrent(m) }

    fun userPresets(): LinkedHashMap<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        try {
            val s = prefs.str("fmt_presets", "")
            if (s.isNotEmpty()) {
                val j = JSONObject(s)
                val it = j.keys()
                while (it.hasNext()) { val n = it.next(); out[n] = fromJson(j.getJSONObject(n)) }
            }
        } catch (_: Throwable) { }
        return out
    }

    fun saveUserPresets(p: Map<String, Map<String, String>>) {
        try {
            val j = JSONObject()
            for ((n, m) in p) j.put(n, toJson(m))
            prefs.setStr("fmt_presets", j.toString())
        } catch (_: Throwable) { }
    }

    fun presetsToJson(p: Map<String, Map<String, String>>): String {
        val j = JSONObject()
        for ((n, m) in p) j.put(n, toJson(m))
        return j.toString()
    }

    /** Parses clipboard JSON: {name:{options}} or one flat options object. Returns name -> options (unknown keys dropped). */
    fun parsePresets(text: String): Map<String, Map<String, String>> {
        val out = LinkedHashMap<String, Map<String, String>>()
        val j = JSONObject(text.trim())
        var allObjects = j.length() > 0
        val it = j.keys()
        while (it.hasNext()) { if (j.optJSONObject(it.next()) == null) allObjects = false }
        if (allObjects) {
            val it2 = j.keys()
            while (it2.hasNext()) { val n = it2.next(); out[n] = fromJson(j.getJSONObject(n)) }
        } else {
            val m = fromJson(j)
            if (m.isNotEmpty()) out["Imported"] = m
        }
        return out
    }
}

private val PAGES = listOf("run" to "Run", "para" to "Para", "keep" to "Keep", "clean" to "Clean", "rules" to "Rules", "presets" to "Presets")
private val INDENT_VALS = listOf("0", "1", "1.5", "2", "2.5", "3", "4")
private val GAP_VALS = listOf("0", "0.5", "0.8", "1", "1.2", "1.5", "2")
private val LH_VALS = listOf("", "1.4", "1.5", "1.6", "1.7", "1.8", "2")
private val FS_VALS = listOf("", "14px", "16px", "18px", "20px", "22px", "24px")
private val SYMS = listOf("* * *", "***", "---", "~ ~ ~", "\u25C6", "\u2042")
private const val BLUE = "#2271B1"
private const val GREEN = "#4CAF50"
private const val WHITE = "#FFFFFF"

class FormatterPanel(private val ctx: Context, private val host: JabraHost, private val io: FmtIo) {

    private val prefs = JabraPrefs(ctx)
    private val store = FmtStore(prefs)

    private var page = "run"
    private var status = ""
    private var peek = ""
    private var confirm = ""
    private var savedY = 0
    private var sv: ScrollView? = null
    private var o: MutableMap<String, String> = LinkedHashMap()
    private var selPreset = ""            // "b:name" or "u:name" (last applied, used by Delete)
    private var undoRes: String? = null   // text we inserted last
    private var undoOrig: String? = null  // text that was there before

    /** Call when the panel is opened from outside. */
    fun reset() { page = "run"; status = ""; peek = ""; confirm = ""; savedY = 0 }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    // ---------- small view helpers ----------
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        val b = Button(ctx)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.textSize = 12f
        b.setPadding(dp(2), 0, dp(2), 0)
        b.setOnClickListener {
            try { host.feedback(b) } catch (_: Throwable) { }
            try { onClick(b) } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(hDp), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun paint(b: Button, bg: String, fg: String) {
        try {
            val g = GradientDrawable()
            g.setColor(Color.parseColor(bg))
            g.cornerRadius = dp(6).toFloat()
            b.background = g
            b.setTextColor(Color.parseColor(fg))
        } catch (_: Throwable) { }
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun note(text: String, bold: Boolean): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 12f
        t.setTextColor(Color.parseColor(if (bold) "#1D2327" else "#555555"))
        t.setPadding(dp(6), dp(3), dp(6), dp(3))
        return t
    }

    private fun peekView(text: String): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = 11f
        t.typeface = Typeface.MONOSPACE
        t.maxLines = 5
        t.ellipsize = TextUtils.TruncateAt.END
        t.setTextColor(Color.parseColor("#B02A63"))
        t.setPadding(dp(8), dp(4), dp(8), dp(4))
        val g = GradientDrawable()
        g.setColor(Color.parseColor("#FFFEFB"))
        g.cornerRadius = dp(6).toFloat()
        t.background = g
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(2), dp(4), dp(4))
        t.layoutParams = lp
        return t
    }

    private fun refresh(keepScroll: Boolean = true) {
        savedY = if (keepScroll) (try { sv?.scrollY ?: 0 } catch (_: Throwable) { 0 }) else 0
        host.showPanel("format")
    }

    /** Two-tap safety: first tap shows [msg] and returns true (caller stops); second tap returns false (caller continues). */
    private fun needConfirm(id: String, msg: String): Boolean {
        if (confirm == id) { confirm = ""; return false }
        confirm = id
        status = msg
        refresh()
        return true
    }

    private fun tog(title: String, key: String): Button {
        val on = o[key] == "true"
        val b = btn(title + ": " + (if (on) "ON" else "OFF"), 1f, 40) {
            store.put(key, if (o[key] == "true") "false" else "true")
            refresh()
        }
        if (on) paint(b, GREEN, WHITE)
        return b
    }

    private fun cycle(title: String, cur: String, values: List<String>, names: List<String>?, set: (String) -> Unit): Button {
        val i = values.indexOf(cur)
        val raw = if (i >= 0 && names != null) names[i] else cur
        val shown = if (raw.isEmpty()) "-" else raw
        return btn(title + ": " + shown, 1f, 40) {
            set(values[(values.indexOf(cur) + 1) % values.size])
            refresh()
        }
    }

    private fun opt(title: String, key: String, values: List<String>, names: List<String>? = null): Button =
        cycle(title, o[key] ?: "", values, names) { v -> store.put(key, v) }

    private fun pref(title: String, key: String, def: String, values: List<String>, names: List<String>): Button =
        cycle(title, prefs.str(key, def), values, names) { v -> prefs.setStr(key, v) }

    // ---------- build ----------
    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        o = store.current()
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val top = row()
        top.addView(btn("ABC", 1.1f, 38) { host.showLetters() })
        for ((id, name) in PAGES) {
            val b = btn(name, 1f, 38) { page = id; confirm = ""; status = ""; refresh(false) }
            b.textSize = 11f
            if (id == page) paint(b, BLUE, WHITE)
            top.addView(b)
        }
        root.addView(top)

        val scroll = ScrollView(ctx)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        if (status.isNotEmpty()) col.addView(note(status, true))
        when (page) {
            "para" -> paraPage(col)
            "keep" -> keepPage(col)
            "clean" -> cleanPage(col)
            "rules" -> rulesPage(col)
            "presets" -> presetsPage(col)
            else -> runPage(col)
        }
        scroll.addView(col)
        root.addView(scroll)
        sv = scroll
        val y = savedY
        scroll.post { try { scroll.scrollTo(0, y) } catch (_: Throwable) { } }
        return root
    }

    // ---------- pages ----------
    private fun runPage(col: LinearLayout) {
        if (peek.isNotEmpty()) col.addView(peekView(peek))
        val r1 = row()
        r1.addView(pref("Source", "fmt_src", "text", listOf("text", "clip", "html"), listOf("Text", "Clipboard", "HTML code")))
        r1.addView(btn("Preview", 1f, 40) { preview() })
        r1.addView(pref("Show", "fmt_prev", "result", listOf("result", "original"), listOf("Result", "Original")))
        col.addView(r1)
        val r2 = row()
        val a = btn("Replace with HTML code", 1f, 44) { run("replaceCode") }
        val b = btn("Replace with clean text", 1f, 44) { run("replaceText") }
        paint(a, BLUE, WHITE); paint(b, BLUE, WHITE)
        r2.addView(a); r2.addView(b)
        col.addView(r2)
        val r3 = row()
        r3.addView(btn("Copy code", 1f, 40) { run("copyCode") })
        r3.addView(btn("Copy formatted", 1f, 40) { run("copyRich") })
        col.addView(r3)
        val r4 = row()
        r4.addView(btn("Copy WordPress block", 1f, 40) { run("copyWp") })
        r4.addView(btn("Copy plain text", 1f, 40) { run("copyPlain") })
        col.addView(r4)
        val r5 = row()
        r5.addView(opt("Style", "mode", listOf("inline", "class", "plain"), listOf("Inline on every p", "One style block", "Plain p")))
        r5.addView(opt("Between", "sep", listOf("single", "none", "blank"), listOf("Single break", "Nothing", "Blank line")))
        col.addView(r5)
        if (undoRes != null) {
            val ru = row()
            val u = btn("Undo last replace", 1f, 40) { undo() }
            paint(u, "#B45309", WHITE)
            ru.addView(u)
            col.addView(ru)
        }
        col.addView(note("Select text in the app first (or use whole field). Replace asks twice before changing the whole field.", false))
    }

    private fun paraPage(col: LinearLayout) {
        val r1 = row()
        r1.addView(tog("Indent", "indent"))
        r1.addView(opt("Size", "indentVal", INDENT_VALS))
        r1.addView(opt("Unit", "indentUnit", listOf("em", "px", "rem", "%")))
        col.addView(r1)
        val r2 = row()
        r2.addView(tog("No indent after heading / break", "skipFirst"))
        col.addView(r2)
        val r3 = row()
        r3.addView(opt("Gap", "spacing", GAP_VALS))
        r3.addView(opt("Gap unit", "spacingUnit", listOf("em", "px", "rem")))
        col.addView(r3)
        val r4 = row()
        r4.addView(opt("Line height", "lh", LH_VALS))
        r4.addView(opt("Font size", "fs", FS_VALS))
        col.addView(r4)
        val r5 = row()
        r5.addView(opt("Align", "align", listOf("none", "left", "justify", "center", "right"), listOf("Theme default", "left", "justify", "center", "right")))
        col.addView(r5)
    }

    private fun keepPage(col: LinearLayout) {
        val r1 = row()
        r1.addView(tog("Bold", "keepB"))
        r1.addView(tog("Italic", "keepI"))
        r1.addView(tog("Underline", "keepU"))
        col.addView(r1)
        val r2 = row()
        r2.addView(tog("Headings as h", "headings"))
        r2.addView(tog("Center headings", "hcenter"))
        col.addView(r2)
        val r3 = row()
        r3.addView(opt("Scene break", "scene", listOf("center", "hr", "text", "remove"), listOf("Centered symbol", "Line (hr)", "Leave as text", "Remove")))
        col.addView(r3)
        val r4 = row()
        r4.addView(opt("Symbol", "sceneText", SYMS))
        r4.addView(btn("Symbol from clipboard", 1f, 40) {
            val t = (io.clipText() ?: "").lineSequence().firstOrNull()?.trim() ?: ""
            if (t.isEmpty() || t.length > 20) status = "Clipboard first line must be 1-20 characters." else { store.put("sceneText", t); status = "" }
            refresh()
        })
        col.addView(r4)
        col.addView(note("Scene breaks are lines like ***, --- or ___.", false))
    }

    private fun cleanPage(col: LinearLayout) {
        val r1 = row()
        r1.addView(tog("Collapse spaces", "collapse"))
        r1.addView(opt("Quotes", "quotes", listOf("none", "straight", "curly"), listOf("Leave as is", "Straight", "Curly")))
        col.addView(r1)
        val r2 = row()
        r2.addView(tog("... to \u2026", "dots"))
        r2.addView(tog("-- to \u2014", "dash"))
        col.addView(r2)
        val r3 = row()
        r3.addView(tog("Markdown *it* **b**", "md"))
        r3.addView(tog("Remove invisible chars", "zw"))
        col.addView(r3)
        val r4 = row()
        r4.addView(opt("Chapter title as heading (first 3 lines)", "autoH", listOf("", "1", "2", "3"), listOf("Off", "h1", "h2", "h3")))
        col.addView(r4)
    }

    private fun rulesPage(col: LinearLayout) {
        val src = o["rules"] ?: ""
        val R = NovelFormatter.parseRules(src)
        col.addView(note(R.repCount().toString() + " replace \u00B7 " + R.delCount().toString() + " delete \u00B7 " + R.bad.toString() + " unreadable lines", true))
        col.addView(peekView(if (src.isEmpty()) "(no rules yet)" else src))
        val r1 = row()
        r1.addView(btn("Rules from clipboard (replace)", 1f, 44) {
            val t = io.clipText() ?: ""
            if (t.isBlank()) { status = "Clipboard is empty."; refresh() }
            else if (src.isNotEmpty() && needConfirm("rulesRep", "Tap again: replaces your current rules.")) { }
            else { store.put("rules", t); status = "Rules set."; refresh() }
        })
        r1.addView(btn("Add from clipboard", 1f, 44) {
            val t = io.clipText() ?: ""
            if (t.isBlank()) status = "Clipboard is empty." else { store.put("rules", if (src.isEmpty()) t else src.trimEnd() + "\n" + t); status = "Rules added." }
            refresh()
        })
        col.addView(r1)
        val r2 = row()
        r2.addView(btn("Copy rules", 1f, 40) { status = if (src.isEmpty()) "No rules to copy." else if (io.setClip(src, null)) "Rules copied." else "Copy failed."; refresh() })
        r2.addView(btn("Clear rules", 1f, 40) {
            if (src.isNotEmpty() && needConfirm("rulesClr", "Tap Clear again to delete all rules.")) { }
            else { store.put("rules", ""); status = "Rules cleared."; refresh() }
        })
        col.addView(r2)
        col.addView(note("One rule per line: find => replace (empty replace removes). re: before find = regex. del: removes every paragraph containing the text. del-re: same with regex. # = comment. Write rules in any notes app, copy, then tap Rules from clipboard.", false))
    }

    private fun presetsPage(col: LinearLayout) {
        val users = store.userPresets()
        for (n in store.builtin.keys) col.addView(presetRow("b:" + n, n))
        for (n in users.keys) col.addView(presetRow("u:" + n, "\u2605 " + n))
        val r1 = row()
        r1.addView(btn("Save current (name from clipboard)", 1f, 44) { savePreset() })
        r1.addView(btn("Delete selected", 1f, 44) { deletePreset() })
        col.addView(r1)
        val r2 = row()
        r2.addView(btn("Export (copy JSON)", 1f, 40) {
            val u = store.userPresets()
            status = if (u.isEmpty()) "No saved presets to export." else if (io.setClip(store.presetsToJson(u), null)) "Presets JSON copied." else "Copy failed."
            refresh()
        })
        r2.addView(btn("Import (from clipboard)", 1f, 40) { importPresets() })
        col.addView(r2)
        val r3 = row()
        r3.addView(btn("Reset all settings", 1f, 40) {
            if (needConfirm("resetAll", "Tap again: resets ALL settings including rules (saved presets stay).")) { }
            else { store.saveCurrent(store.defaults); selPreset = ""; status = "Settings reset."; refresh() }
        })
        col.addView(r3)
    }

    private fun presetRow(id: String, label: String): View {
        val r = row()
        val b = btn(label, 1f, 38) {
            val n = id.substring(2)
            val m = LinkedHashMap<String, String>(store.defaults)
            if (id.startsWith("b:")) {
                m["rules"] = o["rules"] ?: ""   // built-in presets never wipe your rules
                m.putAll(store.builtin[n] ?: emptyMap())
            } else {
                m.putAll(store.userPresets()[n] ?: emptyMap())
            }
            store.saveCurrent(m)
            selPreset = id
            status = "Preset applied: " + n
            refresh()
        }
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setPadding(dp(10), 0, dp(10), 0)
        if (id == selPreset) paint(b, BLUE, WHITE)
        r.addView(b)
        return r
    }

    private fun savePreset() {
        val users = store.userPresets()
        val first = (io.clipText() ?: "").lineSequence().firstOrNull()?.trim() ?: ""
        val base = if (first.isNotEmpty() && first.length <= 30) first else "My preset"
        var name = base
        var k = 2
        while (name in store.builtin || name in users) { name = base + " (" + k + ")"; k++ }
        users[name] = store.current()
        store.saveUserPresets(users)
        selPreset = "u:" + name
        status = "Saved preset: " + name
        refresh()
    }

    private fun deletePreset() {
        if (!selPreset.startsWith("u:")) { status = "Tap one of your \u2605 presets first, then Delete."; refresh(); return }
        if (needConfirm("delPreset", "Tap Delete again to remove: " + selPreset.substring(2))) return
        val users = store.userPresets()
        users.remove(selPreset.substring(2))
        store.saveUserPresets(users)
        selPreset = ""
        status = "Preset deleted."
        refresh()
    }

    private fun importPresets() {
        try {
            val t = io.clipText() ?: ""
            val inc = store.parsePresets(t)
            if (inc.isEmpty()) { status = "Nothing to import."; refresh(); return }
            val users = store.userPresets()
            var added = 0
            for ((n0, m0) in inc) {
                val m = LinkedHashMap<String, String>(store.defaults); m.putAll(m0)
                var n = n0
                var k = 2
                while (n in store.builtin || n in users) { n = n0 + " (" + k + ")"; k++ }   // never overwrites an existing preset
                users[n] = m
                added++
            }
            store.saveUserPresets(users)
            status = "Imported " + added + " preset(s)."
        } catch (_: Throwable) {
            status = "Clipboard is not valid presets JSON."
        }
        refresh()
    }

    // ---------- running ----------
    private class Src(val blocks: List<NovelFormatter.Block>, val usedSel: Boolean, val shownOriginal: String, val undoOrig: String)

    /** Stage order for the text: selection -> whole field (source "text"/"html"), or clipboard html -> clipboard text (source "clip"). */
    private fun readSource(): Src? {
        if (io.blocked()) { status = "Not available in password fields."; return null }
        val src = prefs.str("fmt_src", "text")
        if (src == "clip") {
            val html = io.clipHtml()
            val txt = io.clipText()
            var blocks: List<NovelFormatter.Block> = emptyList()
            if (!html.isNullOrBlank()) blocks = NovelFormatter.parseHtml(html)
            if (blocks.isEmpty() && !txt.isNullOrEmpty()) blocks = NovelFormatter.parsePlain(txt)
            if (blocks.isEmpty()) { status = "Clipboard is empty."; return null }
            return Src(blocks, true, if (!html.isNullOrBlank()) html else (txt ?: ""), io.selected() ?: "")
        }
        val sel = io.selected()
        val usedSel = !sel.isNullOrEmpty()
        val txt = if (usedSel) (sel ?: "") else (io.wholeText() ?: "")
        if (txt.isEmpty()) { status = "No text found. Select text or type some first."; return null }
        val blocks = if (src == "html") NovelFormatter.parseHtml(txt) else NovelFormatter.parsePlain(txt)
        return Src(blocks, usedSel, txt, txt)
    }

    private fun stats(r: NovelFormatter.Result): String =
        (if (r.bad > 0) r.bad.toString() + " rule line(s) unreadable \u00B7 " else "") +
            r.paras.toString() + " paragraphs \u00B7 " + r.words.toString() + " words \u00B7 " + r.out.length.toString() + " characters of code"

    private fun preview() {
        status = ""
        peek = ""
        val s = readSource()
        if (s != null) {
            val r = NovelFormatter.gen(store.current(), s.blocks)
            status = stats(r)
            val shown = if (prefs.str("fmt_prev", "result") == "original") s.shownOriginal else r.out
            peek = if (shown.length > 400) shown.substring(0, 400) else shown
            if (peek.isEmpty()) peek = "(empty)"
        }
        refresh(false)
    }

    private fun run(kind: String) {
        status = ""
        peek = ""
        val s = readSource()
        if (s == null) { refresh(); return }
        val src = prefs.str("fmt_src", "text")
        val replace = kind.startsWith("replace")
        // whole-field replace needs a second tap (we are about to overwrite the user's text)
        if (replace && !s.usedSel && src != "clip") {
            if (needConfirm(kind, "Tap again: this replaces ALL text in the field. Undo is available afterwards.")) return
        } else confirm = ""
        val o2 = store.current()
        val r = NovelFormatter.gen(o2, s.blocks)
        val out: String = when (kind) {
            "replaceCode", "copyCode" -> r.out
            "copyWp" -> NovelFormatter.wpBlock(r.out)
            else -> NovelFormatter.plainText(o2, s.blocks)   // replaceText, copyPlain, copyRich (plain part)
        }
        if (out.isEmpty()) { status = "Nothing to output (empty, or everything was removed by your rules)."; refresh(); return }
        if (replace) {
            val done = if (s.usedSel) io.replaceSelection(out) else io.replaceAll(out)
            if (done) {
                undoRes = out
                undoOrig = s.undoOrig
                status = "Done. " + stats(r)
            } else {
                // final fallback: leave the field untouched and put the result on the clipboard
                status = if (io.setClip(out, null)) "This field cannot be edited from here. Result copied: paste it yourself." else "Could not change the field."
            }
        } else {
            val ok = if (kind == "copyRich") io.setClip(out, NovelFormatter.inlineHtml(o2, s.blocks)) else io.setClip(out, null)
            status = if (ok) "Copied. " + stats(r) else "Copy failed."
        }
        refresh()
    }

    private fun undo() {
        val res = undoRes
        val orig = undoOrig
        if (res == null || orig == null) { refresh(); return }
        if (io.undoReplace(res, orig)) { undoRes = null; undoOrig = null; status = "Undone." }
        else status = "The text changed since, so Undo is not safe."
        refresh()
    }
}
