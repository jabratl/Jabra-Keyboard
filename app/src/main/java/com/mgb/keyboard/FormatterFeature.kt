package com.mgb.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.mgb.keyboard.formatter.FmtChanges
import com.mgb.keyboard.formatter.NovelFormatter
import com.mgb.keyboard.typing.ClipPickPlan
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
    fun selectRange(start: Int, end: Int): Boolean = false   // v0.25.0 (R2): select a match in the field (default: not supported)
    /** v0.36.2: change the field MATCH BY MATCH (base = where the edited piece starts in the field). false = not possible here -> the caller uses replaceAll(after). Default: not supported. */
    fun replaceEdits(base: Int, before: String, after: String, edits: List<com.mgb.keyboard.typing.ReplaceEngine.Hit>): Boolean = false
    /** v0.37.1: the field text WITH its styles (bold / italic / links) when the app gives them, else null. selection = only the selected text. */
    fun richText(selection: Boolean): CharSequence? = null
    fun cursorPos(): Int = -1   // v0.29.0: caret position in wholeText() (-1 = unknown; used by Find & Replace "From / To cursor")
}

class ImeFmtIo(private val svc: InputMethodService, private val isPwd: () -> Boolean) : FmtIo {
    private fun ic() = svc.currentInputConnection
    private fun cm() = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun blocked(): Boolean = try { isPwd() } catch (_: Throwable) { false }

    /** v0.29.0: caret position = length of the text before the caret (only when nothing is selected; a selection = unknown). */
    override fun cursorPos(): Int = try {
        val c = ic()
        if (c == null || !c.getSelectedText(0).isNullOrEmpty()) -1 else (c.getTextBeforeCursor(100001, 0)?.length ?: -1)
    } catch (_: Throwable) { -1 }

    /** v0.25.0 (R2): highlight a match. true only when the app took it AND the field now reports exactly that selection length (a wrong offset is never shown as success). */
    override fun selectRange(start: Int, end: Int): Boolean = try {
        val c = ic()
        if (c == null || start < 0 || end <= start) false else { c.setSelection(start, end) && (c.getSelectedText(0)?.length ?: -1) == end - start }
    } catch (_: Throwable) { false }

    override fun selected(): String? = try { ic()?.getSelectedText(0)?.toString() } catch (_: Throwable) { null }

    override fun richText(selection: Boolean): CharSequence? = try {
        val c = ic()
        val st = android.view.inputmethod.InputConnection.GET_TEXT_WITH_STYLES
        if (c == null) null
        else if (selection) c.getSelectedText(st)
        else {
            val sb = android.text.SpannableStringBuilder()
            val bf = c.getTextBeforeCursor(100000, st); val sl = c.getSelectedText(st); val af = c.getTextAfterCursor(100000, st)
            if (bf != null) sb.append(bf)
            if (sl != null) sb.append(sl)
            if (af != null) sb.append(af)
            sb
        }
    } catch (_: Throwable) { null }

    // ---- v0.36.2: match-by-match replace (the text between the matches is never rewritten, so its bold / italic / links stay) ----
    private class EditRec(val res: String, val orig: String, val inv: List<com.mgb.keyboard.typing.ReplaceEngine.Hit>, val base: Int)
    private val editRecs = ArrayList<EditRec>()

    /** Last match first (earlier offsets stay valid): select it, check the selected text really is the found text, commit the replacement. false = anything unexpected. */
    private fun applyEdits(c: android.view.inputmethod.InputConnection, base: Int, edits: List<com.mgb.keyboard.typing.ReplaceEngine.Hit>): Boolean {
        for (h in edits.sortedByDescending { it.start }) {
            val a = base + h.start
            val b = base + h.end
            if (!c.setSelection(a, b)) return false
            if (b > a) { val got = c.getSelectedText(0)?.toString() ?: return false; if (got != h.found) return false }
            if (!c.commitText(h.repl, 1)) return false
        }
        return true
    }

    override fun replaceEdits(base: Int, before: String, after: String, edits: List<com.mgb.keyboard.typing.ReplaceEngine.Hit>): Boolean {
        val c = ic() ?: return false
        if (edits.isEmpty() || base < 0) return false
        if (!com.mgb.keyboard.typing.ReplaceEdits.matches(before, after, edits)) return false
        // v0.42.5: more than 2000 matches used to mean "rewrite the whole text" (format of the whole field lost). Now the closest matches are joined (max 2000 edits, 60000 matches).
        val eds: List<com.mgb.keyboard.typing.ReplaceEngine.Hit> = if (edits.size <= 2000) edits else (if (edits.size > 60000) return false else (com.mgb.keyboard.typing.ReplaceEdits.coalesce(before, edits, 2000) ?: return false))
        if (!com.mgb.keyboard.typing.ReplaceEdits.matches(before, after, eds)) return false
        var ok = false
        try {
            if (wholeText() != before) return false   // the field changed meanwhile: do nothing here
            c.beginBatchEdit()
            ok = applyEdits(c, base, eds)
        } catch (_: Throwable) { ok = false } finally { try { c.endBatchEdit() } catch (_: Throwable) { } }
        if (ok) ok = try { wholeText() == after } catch (_: Throwable) { false }   // never report success for a wrong result
        if (ok) {
            try { c.setSelection(after.length, after.length) } catch (_: Throwable) { }   // caret at the end, same as the whole-text replace
            editRecs.add(EditRec(after, before, com.mgb.keyboard.typing.ReplaceEdits.inverse(eds), base))
            while (editRecs.size > 10) editRecs.removeAt(0)
        }
        return ok   // false after a half-done try: the caller's replaceAll(after) still writes the right text
    }

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
        try {   // v0.36.2: a match-by-match replace is undone match by match too (format of the rest stays)
            val i = editRecs.indexOfLast { it.res == res && it.orig == orig }
            if (i >= 0) {
                val rec = editRecs[i]
                if (wholeText() == res) {
                    var ok = false
                    try { c.beginBatchEdit(); ok = applyEdits(c, rec.base, rec.inv) } catch (_: Throwable) { ok = false } finally { try { c.endBatchEdit() } catch (_: Throwable) { } }
                    if (ok) ok = wholeText() == orig
                    if (ok) { try { c.setSelection(orig.length, orig.length) } catch (_: Throwable) { }; editRecs.removeAt(i); return true }
                    editRecs.removeAt(i)
                    return replaceAll(orig)   // half-done: write the old text whole (right text, format of the field is lost)
                }
            }
        } catch (_: Throwable) { }
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


/*
 * v0.52.0 Formatter "one screen" redesign (from formatter-redesign-mock.html). Same engine, same option keys, same prefs, same FmtIo.
 * Layout (top to bottom): [ABC] [preset v] [Text|Clip|HTML]  /  live preview (Result | Original) + change chips  /  4 sections (Para Keep Clean Rules)  /  [Scope v] [Replace] [Undo] [Copy v].
 * No Preview button (the preview is always live), no 6 tabs, no cycle buttons (choice = chip, number = - / + stepper, on / off = toggle chip).
 * Menus (preset / scope / copy) open inside the stage area, not as a floating popup (a popup is not safe inside a keyboard window).
 * Rules: one line = one rule; "#off " in front = rule switched off (the engine reads # lines as comments, so no engine change).
 * NEW prefs: fmt_scope ("sel" | "all"), fmt_preset ("b:Name" | "u:Name"). Old fmt_float is no longer used (the floating window is replaced by the live preview).
 */
class FormatterPanel(private val ctx: Context, private val host: JabraHost, private val io: FmtIo, private val clips: () -> List<Pair<String, String?>> = { emptyList() }) {

    private val prefs = JabraPrefs(ctx)
    private val store = FmtStore(prefs)

    private var sec = ""          // "" | para | keep | clean | rules : the open option section
    private var menu = ""         // "" | preset | scope | copy : an open menu
    private var focus = ""        // change kind that is highlighted in the Original view
    private var status = ""
    private var confirm = ""
    private var confirmAt = 0L
    private var savedY = 0
    private var sv: ScrollView? = null
    private var o: MutableMap<String, String> = LinkedHashMap()
    private var undoRes: String? = null
    private var undoOrig: String? = null
    private var pickedClip: String? = null
    private var pickedHtml: String? = null
    private var picking = false

    private var selPreset: String
        get() = prefs.str("fmt_preset", "")
        set(v) { prefs.setStr("fmt_preset", v) }

    /** Call when the panel is opened from outside. */
    fun reset() { sec = ""; menu = ""; focus = ""; status = ""; confirm = ""; savedY = 0; pickedClip = null; pickedHtml = null; picking = false }

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    private val fb: (View) -> Unit = { v -> host.feedback(v) }

    // ---------- small view helpers ----------
    private fun btn(label: String, weight: Float, hDp: Int, onClick: (Button) -> Unit): Button {
        try {
            val b = UiKit.btn(ctx, prefs, label, weight, hDp, 12f, fb, onClick)
            b.setPadding(dp(2), 0, dp(2), 0)
            return b
        } catch (_: Throwable) { }
        val b = Button(ctx)
        b.text = label; b.isAllCaps = false; b.minWidth = 0; b.minimumWidth = 0; b.textSize = 12f
        b.setOnClickListener { try { host.feedback(b) } catch (_: Throwable) { }; try { onClick(b) } catch (_: Throwable) { } }
        val lp = LinearLayout.LayoutParams(if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT, dp(hDp), weight)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun chip(label: String, on: Boolean, hDp: Int = 30, onClick: (Button) -> Unit): Button {
        try { return UiKit.chip(ctx, prefs, label, on, hDp, fb, onClick) } catch (_: Throwable) { }
        val b = btn(label, 0f, hDp, onClick)
        if (on) paint(b, BLUE, WHITE)
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

    private fun paintBg(v: View) { try { JabraThemes.paint(prefs, v) } catch (_: Throwable) { v.setBackgroundColor(Color.parseColor("#DDDDDD")) } }
    private fun row(): LinearLayout { try { return UiKit.row(ctx) } catch (_: Throwable) { }; val r = LinearLayout(ctx); r.orientation = LinearLayout.HORIZONTAL; return r }

    private fun panelInk(): Int = try { if (JabraTokens.isClassic(prefs)) Color.parseColor("#1D2327") else JabraTokens.panelText(prefs) } catch (_: Throwable) { Color.parseColor("#1D2327") }
    private fun panelDim(): Int = try { if (JabraTokens.isClassic(prefs)) Color.parseColor("#555555") else JabraTokens.note(prefs) } catch (_: Throwable) { Color.parseColor("#555555") }
    private fun cardInk(): Int = try { JabraTokens.cardText(prefs) } catch (_: Throwable) { Color.BLACK }
    private fun cardDim(): Int = try { JabraTokens.cardNote(prefs) } catch (_: Throwable) { Color.GRAY }

    private fun label(text: String): TextView {
        val t = TextView(ctx)
        t.text = text; t.textSize = 11f; t.setTextColor(panelDim())
        t.setPadding(dp(6), dp(4), dp(6), dp(1))
        return t
    }

    private fun mkText(s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(ctx)
        t.text = s; t.textSize = sp; t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    /** One scrollable line of views (a chip row never wraps, it scrolls). */
    private fun line(views: List<View>): View {
        val h = HorizontalScrollView(ctx)
        h.isHorizontalScrollBarEnabled = false
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        for (v in views) r.addView(v)
        h.addView(r)
        return h
    }

    private fun fmtNum(v: Double): String { val x = Math.round(v * 10.0) / 10.0; return if (x == Math.floor(x)) x.toInt().toString() else x.toString() }

    private fun choice(key: String, values: List<String>, names: List<String>? = null): View {
        val cur = o[key] ?: ""
        val vs = ArrayList<View>()
        for (i in values.indices) {
            val v = values[i]
            val nm = if (names != null) names[i] else (if (v.isEmpty()) "Auto" else v)
            vs.add(chip(nm, cur == v) { store.put(key, v); refresh() })
        }
        return line(vs)
    }

    private fun toggle(key: String, title: String): Button = chip(title, o[key] == "true") { store.put(key, if (o[key] == "true") "false" else "true"); refresh() }

    private fun stepper(key: String, d: Double, min: Double, max: Double, unit: String): View {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        fun bump(sign: Int) {
            val cur = (o[key] ?: "0").toDoubleOrNull() ?: 0.0
            store.put(key, fmtNum(Math.max(min, Math.min(max, cur + sign * d))))
            refresh()
        }
        r.addView(chip("\u2212", false) { bump(-1) })
        val t = mkText((o[key] ?: "") + unit, 12f, panelInk(), true)
        t.minWidth = dp(44); t.gravity = Gravity.CENTER
        r.addView(t)
        r.addView(chip("+", false) { bump(1) })
        return r
    }

    private fun refresh(keepScroll: Boolean = true) {
        savedY = if (keepScroll) (try { sv?.scrollY ?: 0 } catch (_: Throwable) { 0 }) else 0
        host.showPanel("format")
    }

    /** Two-tap safety: first tap shows [msg] and returns true (caller stops); second tap within 4 s returns false (caller continues). */
    private fun needConfirm(id: String, msg: String): Boolean {
        val now = System.currentTimeMillis()
        if (confirm == id && now - confirmAt < 4000L) { confirm = ""; return false }
        confirm = id; confirmAt = now
        status = msg
        refresh()
        return true
    }

    private fun confirmOn(id: String): Boolean = confirm == id && System.currentTimeMillis() - confirmAt < 4000L

    private fun scopeAll(): Boolean = prefs.str("fmt_scope", "sel") == "all"

    // ---------- build ----------
    /** Returns null on any problem -> keyboard shows normal letters. */
    fun build(): View? = try { make() } catch (_: Throwable) { null }

    private fun make(): View {
        o = store.current()
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        paintBg(root)
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // the source is read once per build; a read problem is shown in the preview, not as a status
        val keepStatus = status
        val s: Src? = if (picking) null else readSource()
        val srcMsg = if (s == null) status else ""
        status = keepStatus

        val srcKey = prefs.str("fmt_src", "text")
        root.addView(topBar(srcKey))

        val scroll = ScrollView(ctx)
        scroll.isFillViewport = true
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112))   // MgbIme.stretchToLetters gives the last ScrollView weight 1
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        scroll.addView(col)

        var below: View? = null
        if (picking) {
            root.addView(stageHeader("Choose a clip", "Back") { picking = false; refresh(false) })
            clipPicker(col)
        } else if (menu.isNotEmpty()) {
            val title = when (menu) { "preset" -> "Presets"; "scope" -> "Replace what?"; else -> "Copy / output" }
            root.addView(stageHeader(title, "Close") { menu = ""; refresh(false) })
            when (menu) { "preset" -> presetMenu(col); "scope" -> scopeMenu(col); else -> copyMenu(col) }
        } else if (sec.isNotEmpty()) {
            val title = when (sec) { "para" -> "Paragraphs"; "keep" -> "Keep"; "clean" -> "Clean"; else -> "Rules" }
            root.addView(stageHeader(title, "Done") { sec = ""; refresh(false) })
            if (s != null) {
                val live = try { NovelFormatter.plainText(o, s.blocks) } catch (_: Throwable) { "" }
                val lv = mkText(if (live.length > 150) live.substring(0, 150) else live, 11f, panelDim())
                lv.maxLines = 2; lv.ellipsize = TextUtils.TruncateAt.END
                lv.setPadding(dp(8), 0, dp(8), dp(2))
                root.addView(lv)
            }
            when (sec) { "para" -> paraSection(col); "keep" -> keepSection(col); "clean" -> cleanSection(col); else -> rulesSection(col, inputText(s)) }
        } else {
            root.addView(previewHeader(srcKey))
            scroll.background = try { UiKit.cardBackground(ctx, prefs) } catch (_: Throwable) { null }
            val pv = TextView(ctx)
            pv.textSize = 13f
            pv.setTextColor(cardInk())
            pv.setPadding(dp(10), dp(6), dp(10), dp(8))
            pv.setTextIsSelectable(false)
            if (s == null) { pv.text = if (srcMsg.isEmpty()) "Nothing to show." else srcMsg; pv.setTextColor(cardDim()) }
            else pv.text = previewText(s, srcKey)
            col.addView(pv)
            if (s != null) below = chipsRow(s, srcKey)
        }
        root.addView(scroll)
        sv = scroll
        if (below != null) root.addView(below)
        root.addView(sections())
        root.addView(actions(s))
        status = ""   // a status line shows once (until the next tap)
        val y = savedY
        scroll.post { try { scroll.scrollTo(0, y) } catch (_: Throwable) { } }
        return root
    }

    private fun topBar(srcKey: String): View {
        val top = row()
        top.addView(btn("ABC", 0.9f, 36) { host.showLetters() })
        val nm = presetName()
        val pb = btn(nm + (if (modified()) " \u2022" else "") + "  \u25BE", 2.4f, 36) { menu = if (menu == "preset") "" else "preset"; sec = ""; refresh(false) }
        try { pb.gravity = Gravity.START or Gravity.CENTER_VERTICAL; pb.maxLines = 1; pb.ellipsize = TextUtils.TruncateAt.END; pb.setPadding(dp(8), 0, dp(4), 0) } catch (_: Throwable) { }
        top.addView(pb)
        for ((k, n) in listOf("text" to "Text", "clip" to "Clip", "html" to "HTML")) {
            val b = btn(n, 0.9f, 36) { prefs.setStr("fmt_src", k); confirm = ""; status = ""; picking = false; refresh(false) }
            b.textSize = 11f
            if (k == srcKey) paint(b, BLUE, WHITE)
            top.addView(b)
        }
        return top
    }

    private fun stageHeader(title: String, closeLabel: String, onClose: () -> Unit): View {
        val h = row()
        h.gravity = Gravity.CENTER_VERTICAL
        val t = mkText(if (status.isNotEmpty()) status else title, 12f, panelInk(), true)
        t.maxLines = 2; t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(6), 0, dp(4), 0)
        h.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        h.addView(chip(closeLabel, true) { onClose() })
        return h
    }

    private fun previewHeader(srcKey: String): View {
        val h = row()
        h.gravity = Gravity.CENTER_VERTICAL
        val t = mkText(if (status.isNotEmpty()) status else "Preview", 12f, panelInk(), true)
        t.maxLines = 2; t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(dp(6), 0, dp(4), 0)
        h.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (srcKey == "clip") h.addView(chip(if (pickedClip == null) "Clip: latest \u25B8" else "Clip \u2713 \u25B8", pickedClip != null) { picking = true; refresh(false) })
        val orig = prefs.str("fmt_prev", "result") == "original"
        h.addView(chip("Result", !orig) { prefs.setStr("fmt_prev", "result"); focus = ""; refresh(false) })
        h.addView(chip("Original", orig) { prefs.setStr("fmt_prev", "original"); refresh(false) })
        return h
    }

    /** The text the changes are counted on: the field text for Text source, the plain text of the blocks for Clip / HTML. */
    private fun inputText(s: Src?): String {
        if (s == null) return ""
        if (prefs.str("fmt_src", "text") == "text") return s.shownOriginal
        return try { s.blocks.joinToString("\n\n") { b -> b.runs.joinToString("") { r -> r.t } } } catch (_: Throwable) { "" }
    }

    private fun previewText(s: Src, srcKey: String): CharSequence {
        val orig = prefs.str("fmt_prev", "result") == "original"
        if (orig) {
            val full = if (srcKey == "html") s.shownOriginal else inputText(s)
            val cut = if (full.length > 3000) full.substring(0, 3000) else full
            val sp = SpannableString(cut)
            if (srcKey != "html") {
                try {
                    for (c in FmtChanges.spans(cut, o)) {
                        if (c.end > cut.length) continue
                        sp.setSpan(UnderlineSpan(), c.start, c.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (focus.isNotEmpty() && c.k == focus) sp.setSpan(BackgroundColorSpan(0x662271B1), c.start, c.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } catch (_: Throwable) { }
            }
            return if (cut.isEmpty()) "(empty)" else sp
        }
        val html0 = try { NovelFormatter.inlineHtml(o, s.blocks) } catch (_: Throwable) { "" }
        if (html0.isEmpty()) return "(empty: nothing left after your rules / options)"
        if (html0.length > 20000) { val p = NovelFormatter.plainText(o, s.blocks); return if (p.length > 3000) p.substring(0, 3000) else p }
        val sym = (o["sceneText"] ?: "* * *").replace("&", "&amp;").replace("<", "&lt;")
        val html = html0.replace("<hr>", "<p style=\"text-align:center\">" + sym + "</p>")
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)   // minSdk 24
    }

    private fun chipsRow(s: Src, srcKey: String): View {
        val text = inputText(s)
        val ch = try { FmtChanges.changes(text, o) } catch (_: Throwable) { LinkedHashMap<String, Int>() }
        val total = FmtChanges.total(ch)
        val vs = ArrayList<View>()
        val cnt = mkText(if (total > 0) "$total changes" else "Already clean \u2713", 12f, if (total > 0) panelInk() else Color.parseColor(GREEN), true)
        cnt.setPadding(dp(6), 0, dp(4), 0)
        vs.add(cnt)
        for ((k, n) in ch) {
            vs.add(chip(FmtChanges.name(k) + " " + n, focus == k) {
                focus = if (focus == k) "" else k
                if (focus.isNotEmpty()) { prefs.setStr("fmt_prev", "original"); status = "Original view: spots are underlined" }
                refresh(false)
            })
        }
        if ((o["autoH"] ?: "").isEmpty() && FmtChanges.titleSuggest(text)) {
            vs.add(chip("+ Chapter title \u2192 h2", false) { store.put("autoH", "2"); refresh() })
        }
        val l = line(vs)
        return l
    }

    private fun sections(): View {
        val r = row()
        val items = listOf(
            Triple("para", "Para", FmtChanges.paraSummary(o)),
            Triple("keep", "Keep", FmtChanges.keepSummary(o)),
            Triple("clean", "Clean", FmtChanges.cleanSummary(o) + " \u00B7 " + (if (o["scene"] == "center") "scene " + (o["sceneText"] ?: "") else (o["scene"] ?: ""))),
            Triple("rules", "Rules", FmtChanges.rulesSummary(o)))
        for ((id, name, sum) in items) {
            val b = btn(name, 1f, 44) { sec = if (sec == id) "" else id; menu = ""; focus = ""; status = ""; refresh(false) }
            val sp = SpannableString(name + "\n" + sum)
            sp.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(RelativeSizeSpan(0.82f), name.length + 1, sp.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            try { b.text = sp; b.maxLines = 2; b.ellipsize = TextUtils.TruncateAt.END; b.textSize = 12f } catch (_: Throwable) { }
            if (id == sec) paint(b, BLUE, WHITE)
            r.addView(b)
        }
        return r
    }

    private fun actions(s: Src?): View {
        val r = row()
        val srcKey = prefs.str("fmt_src", "text")
        val whole = srcKey != "clip" && (scopeAll() || s?.usedSel != true)
        if (srcKey == "text") r.addView(btn((if (scopeAll()) "All" else "Selection") + " \u25BE", 0.9f, 42) { menu = if (menu == "scope") "" else "scope"; sec = ""; refresh(false) })
        val kind = "replaceText"
        val label = if (confirmOn(kind)) "Tap again to confirm" else if (srcKey == "clip") "Insert result" else if (whole) (if (scopeAll()) "Replace all" else "Replace field") else "Replace selection"
        val go = btn(label, 2.2f, 42) { menu = ""; run(kind) }
        paint(go, if (confirmOn(kind)) "#C0392B" else BLUE, WHITE)
        try { go.setTypeface(go.typeface, Typeface.BOLD); go.textSize = 13f } catch (_: Throwable) { }
        r.addView(go)
        if (undoRes != null) { val u = btn("Undo", 0.8f, 42) { undo() }; paint(u, "#B45309", WHITE); r.addView(u) }
        r.addView(btn("Copy \u25BE", 0.9f, 42) { menu = if (menu == "copy") "" else "copy"; sec = ""; refresh(false) })
        return r
    }

    // ---------- option sections ----------
    private fun paraSection(col: LinearLayout) {
        col.addView(label("Indent"))
        col.addView(line(listOf(toggle("indent", "Indent"), stepper("indentVal", 0.5, 0.0, 6.0, ""), chip("skip after heading", o["skipFirst"] == "true") { store.put("skipFirst", if (o["skipFirst"] == "true") "false" else "true"); refresh() })))
        col.addView(choice("indentUnit", listOf("em", "px", "rem", "%")))
        col.addView(label("Gap between paragraphs"))
        col.addView(line(listOf(stepper("spacing", 0.2, 0.0, 3.0, ""))))
        col.addView(choice("spacingUnit", listOf("em", "px", "rem")))
        col.addView(label("Line height"))
        col.addView(choice("lh", LH_VALS))
        col.addView(label("Font size"))
        col.addView(choice("fs", FS_VALS))
        col.addView(label("Align"))
        col.addView(choice("align", listOf("none", "left", "justify", "center", "right"), listOf("Auto", "Left", "Justify", "Center", "Right")))
    }

    private fun keepSection(col: LinearLayout) {
        col.addView(label("Keep styles"))
        col.addView(line(listOf(toggle("keepB", "Bold"), toggle("keepI", "Italic"), toggle("keepU", "Underline"))))
        col.addView(label("Headings"))
        col.addView(line(listOf(toggle("headings", "Keep headings"), toggle("hcenter", "Center headings"))))
    }

    private fun cleanSection(col: LinearLayout) {
        col.addView(label("Tidy"))
        col.addView(line(listOf(toggle("collapse", "Collapse blank lines"), toggle("md", "Markdown \u2192 style"), toggle("zw", "Invisible chars"))))
        col.addView(line(listOf(toggle("dots", "... \u2192 \u2026"), toggle("dash", "-- \u2192 \u2014"))))
        col.addView(label("Quotes"))
        col.addView(choice("quotes", listOf("none", "straight", "curly"), listOf("Leave", "Straight", "Curly")))
        col.addView(label("Scene break"))
        col.addView(choice("scene", listOf("center", "hr", "text", "remove"), listOf("Symbol", "Line", "As text", "Remove")))
        if (o["scene"] == "center") {
            col.addView(label("Symbol"))
            val vs = ArrayList<View>()
            for (sy in SYMS) vs.add(chip(sy, o["sceneText"] == sy) { store.put("sceneText", sy); refresh() })
            vs.add(chip("from clipboard", false) {
                val t = (io.clipText() ?: "").lineSequence().firstOrNull()?.trim() ?: ""
                if (t.isEmpty() || t.length > 20) status = "Clipboard first line must be 1-20 characters." else { store.put("sceneText", t); status = "" }
                refresh()
            })
            col.addView(line(vs))
        }
        col.addView(label("Chapter title as heading (first 3 lines)"))
        col.addView(choice("autoH", listOf("", "1", "2", "3"), listOf("Off", "h1", "h2", "h3")))
    }

    private fun rulesSection(col: LinearLayout, text: String) {
        val src = o["rules"] ?: ""
        val R = NovelFormatter.parseRules(src)
        col.addView(label(R.repCount().toString() + " replace \u00B7 " + R.delCount().toString() + " delete \u00B7 " + R.bad.toString() + " unreadable lines"))
        val rl = FmtChanges.ruleLines(src)
        if (rl.isEmpty()) col.addView(label("(no rules yet)"))
        for (r in rl) {
            val rr = row()
            rr.gravity = Gravity.CENTER_VERTICAL
            rr.addView(chip(if (r.off) "OFF" else "ON", !r.off) { store.put("rules", FmtChanges.toggleRule(o["rules"] ?: "", r.line)); refresh() })
            val tv = mkText(r.text, 12f, panelInk())
            tv.maxLines = 1; tv.ellipsize = TextUtils.TruncateAt.END
            tv.setPadding(dp(4), 0, dp(4), 0)
            rr.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val n = if (r.off) 0 else FmtChanges.matches(text, r)
            rr.addView(mkText(n.toString() + (if (n == 1) " match" else " matches"), 11f, panelDim()))
            col.addView(rr)
        }
        col.addView(line(listOf(
            chip("+ Add from clipboard", false) {
                val t = io.clipText() ?: ""
                if (t.isBlank()) status = "Clipboard is empty." else { store.put("rules", if (src.isEmpty()) t else src.trimEnd() + "\n" + t); status = "Rules added." }
                refresh()
            },
            chip("Replace from clipboard", false) {
                val t = io.clipText() ?: ""
                if (t.isBlank()) { status = "Clipboard is empty."; refresh() }
                else if (src.isNotEmpty() && needConfirm("rulesRep", "Tap again: replaces your current rules.")) { }
                else { store.put("rules", t); status = "Rules set."; refresh() }
            },
            chip("Copy rules", false) { status = if (src.isEmpty()) "No rules to copy." else if (io.setClip(src, null)) "Rules copied." else "Copy failed."; refresh() },
            chip("Clear", false) {
                if (src.isNotEmpty() && needConfirm("rulesClr", "Tap Clear again to delete all rules.")) { }
                else { store.put("rules", ""); status = "Rules cleared."; refresh() }
            })))
        col.addView(label("One rule per line: find => replace (empty replace removes). re: = regex. del: / del-re: removes paragraphs. # = comment. ON / OFF switches a rule."))
    }

    // ---------- menus (inside the stage) ----------
    private fun menuItem(label: String, sub: String, on: Boolean, onClick: () -> Unit): View {
        val r = row()
        val b = btn(label + (if (sub.isEmpty()) "" else "   \u00B7 " + sub), 1f, 38) { onClick() }
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.setPadding(dp(10), 0, dp(10), 0)
        if (on) paint(b, BLUE, WHITE)
        r.addView(b)
        return r
    }

    private fun scopeMenu(col: LinearLayout) {
        col.addView(menuItem("Selection", "up to what you selected (no selection = whole field)", !scopeAll()) { prefs.setStr("fmt_scope", "sel"); confirm = ""; menu = ""; refresh(false) })
        col.addView(menuItem("Whole field", "always asks to confirm", scopeAll()) { prefs.setStr("fmt_scope", "all"); confirm = ""; menu = ""; refresh(false) })
    }

    private fun copyMenu(col: LinearLayout) {
        col.addView(menuItem("Formatted", "rich text", false) { menu = ""; run("copyRich") })
        col.addView(menuItem("HTML code", "", false) { menu = ""; run("copyCode") })
        col.addView(menuItem("WordPress block", "", false) { menu = ""; run("copyWp") })
        col.addView(menuItem("Plain text", "", false) { menu = ""; run("copyPlain") })
        col.addView(menuItem("Replace with HTML code", "instead of clean text", false) { menu = ""; run("replaceCode") })
        col.addView(label("Code style"))
        col.addView(choice("mode", listOf("inline", "class", "plain"), listOf("Inline on every p", "One style block", "Plain p")))
        col.addView(label("Between paragraphs (code)"))
        col.addView(choice("sep", listOf("single", "none", "blank"), listOf("Single break", "Nothing", "Blank line")))
    }

    private fun presetMenu(col: LinearLayout) {
        val users = store.userPresets()
        for (n in store.builtin.keys) col.addView(menuItem(n, "", selPreset == "b:" + n) { applyPreset("b:" + n) })
        for (n in users.keys) col.addView(menuItem("\u2605 " + n, "saved", selPreset == "u:" + n) { applyPreset("u:" + n) })
        col.addView(line(listOf(
            chip("Save current (name from clipboard)", false) { savePreset() },
            chip("Reset to preset", false) { if (selPreset.isEmpty()) { status = "Pick a preset first."; refresh() } else applyPreset(selPreset) })))
        col.addView(line(listOf(
            chip("Delete selected", false) { deletePreset() },
            chip("Export (copy JSON)", false) {
                val u = store.userPresets()
                status = if (u.isEmpty()) "No saved presets to export." else if (io.setClip(store.presetsToJson(u), null)) "Presets JSON copied." else "Copy failed."
                refresh()
            },
            chip("Import (from clipboard)", false) { importPresets() },
            chip("Reset all", false) {
                if (needConfirm("resetAll", "Tap again: resets ALL settings including rules (saved presets stay).")) { }
                else { store.saveCurrent(store.defaults); selPreset = ""; status = "Settings reset."; refresh() }
            })))
    }

    private fun presetName(): String {
        val id = selPreset
        if (id.length < 3) return "Current"
        val n = id.substring(2)
        return if (id.startsWith("u:")) "\u2605 " + n else n
    }

    private fun presetMap(id: String): Map<String, String>? {
        if (id.length < 3) return null
        val n = id.substring(2)
        val m = LinkedHashMap<String, String>(store.defaults)
        if (id.startsWith("b:")) m.putAll(store.builtin[n] ?: return null)
        else m.putAll(store.userPresets()[n] ?: return null)
        return m
    }

    private fun modified(): Boolean {
        val id = selPreset
        val b = presetMap(id) ?: return false
        val c = store.current()
        for ((k, v) in b) {
            if (k == "rules" && id.startsWith("b:")) continue   // built-in presets never touch your rules
            if ((c[k] ?: "") != v) return true
        }
        return false
    }

    private fun applyPreset(id: String) {
        val base = presetMap(id)
        if (base == null) { status = "Preset not found."; refresh(); return }
        val m = LinkedHashMap<String, String>(base)
        if (id.startsWith("b:")) m["rules"] = o["rules"] ?: ""   // built-in presets never wipe your rules
        store.saveCurrent(m)
        selPreset = id
        menu = ""
        status = "Preset: " + id.substring(2)
        refresh(false)
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
        if (!selPreset.startsWith("u:")) { status = "Select one of your \u2605 presets first, then Delete."; refresh(); return }
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

    // ---------- clip picker (unchanged logic, v0.48.1) ----------
    private fun clipLabel(t: String): String {
        val one = t.trim().replace(Regex("\\s+"), " ")
        return if (one.length > 48) one.substring(0, 48) + "..." else one
    }

    private fun clipPicker(col: LinearLayout) {
        val list = try { clips() } catch (_: Throwable) { emptyList() }
        val texts = list.map { it.first }
        val htmls = list.map { !it.second.isNullOrEmpty() }
        val f = ClipPickPlan.sanitizeFilter(prefs.str("fmt_pick_f", "0").toIntOrNull() ?: 0)
        val srt = ClipPickPlan.sanitizeSort(prefs.str("fmt_pick_s", "0").toIntOrNull() ?: 0)
        val shown = ClipPickPlan.plan(texts, htmls, f, srt)
        col.addView(label("Tap a clip to use it as the source. " + ClipPickPlan.count(texts, htmls, ClipPickPlan.F_ALL) + " saved."))
        val rt = row()
        val latest = btn("Latest (system clipboard)", 1f, 40) { pickedClip = null; pickedHtml = null; picking = false; refresh(false) }
        if (pickedClip == null) paint(latest, GREEN, WHITE)
        rt.addView(latest)
        rt.addView(btn("Sort: " + ClipPickPlan.S_NAMES[srt], 0.7f, 40) { prefs.setStr("fmt_pick_s", ClipPickPlan.nextSort(srt).toString()); refresh(false) })
        col.addView(rt)
        val rf = row()
        for (i in 0 until ClipPickPlan.F_NAMES.size) {
            val c = btn(ClipPickPlan.F_NAMES[i] + " " + ClipPickPlan.count(texts, htmls, i), 1f, 34) { prefs.setStr("fmt_pick_f", i.toString()); refresh(false) }
            c.textSize = 11f
            if (i == f) paint(c, BLUE, WHITE)
            rf.addView(c)
        }
        col.addView(rf)
        if (list.isEmpty()) { col.addView(label("No saved clips yet. Turn on clipboard history and copy something.")); return }
        if (shown.isEmpty()) { col.addView(label("No clip matches this filter. Tap All.")); return }
        for (e in shown) {
            val b = btn(e.head() + "\n" + e.info(), 1f, 54) { pickedClip = e.text; pickedHtml = list[e.index].second; picking = false; refresh(false) }
            try { b.isAllCaps = false; b.gravity = Gravity.START or Gravity.CENTER_VERTICAL; b.maxLines = 3; b.ellipsize = TextUtils.TruncateAt.END; b.textSize = 11f; b.setPadding(dp(8), 0, dp(8), 0) } catch (_: Throwable) { }
            if (e.text == pickedClip) paint(b, GREEN, WHITE)
            val rr = row()
            rr.addView(b)
            col.addView(rr)
        }
    }

    // ---------- running ----------
    private class Src(val blocks: List<NovelFormatter.Block>, val usedSel: Boolean, val shownOriginal: String, val undoOrig: String)

    /** Stage order for the text: selection -> whole field (source "text"/"html"), or clipboard html -> clipboard text (source "clip"). Scope "all" skips the selection. */
    private fun readSource(): Src? {
        if (io.blocked()) { status = "Not available in password fields."; return null }
        val src = prefs.str("fmt_src", "text")
        if (src == "clip") {
            val pc = pickedClip
            val html = if (pc != null) pickedHtml else io.clipHtml()
            val txt = if (pc != null) pc else io.clipText()
            var blocks: List<NovelFormatter.Block> = emptyList()
            if (!html.isNullOrBlank()) blocks = NovelFormatter.parseHtml(html)
            if (blocks.isEmpty() && !txt.isNullOrEmpty()) blocks = NovelFormatter.parsePlain(txt)
            if (blocks.isEmpty()) { status = "Clipboard is empty."; return null }
            return Src(blocks, true, if (!html.isNullOrBlank()) html else (txt ?: ""), io.selected() ?: "")
        }
        val sel = if (scopeAll()) null else io.selected()
        val usedSel = !sel.isNullOrEmpty()
        val txt = if (usedSel) (sel ?: "") else (io.wholeText() ?: "")
        if (txt.isEmpty()) { status = "No text found. Select text or type some first."; return null }
        val blocks = if (src == "html") NovelFormatter.parseHtml(txt) else NovelFormatter.parsePlain(txt)
        return Src(blocks, usedSel, txt, txt)
    }

    private fun stats(r: NovelFormatter.Result): String =
        (if (r.bad > 0) r.bad.toString() + " rule line(s) unreadable \u00B7 " else "") +
            r.paras.toString() + " paragraphs \u00B7 " + r.words.toString() + " words \u00B7 " + r.out.length.toString() + " characters of code"

    private fun run(kind: String) {
        status = ""
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
            else -> NovelFormatter.plainText(o2, s.blocks)
        }
        if (out.isEmpty()) { status = "Nothing to output (empty, or everything was removed by your rules)."; refresh(); return }
        if (replace) {
            val done = if (s.usedSel) io.replaceSelection(out) else io.replaceAll(out)
            if (done) {
                undoRes = out
                undoOrig = s.undoOrig
                status = "Replaced. " + stats(r)
            } else {
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
