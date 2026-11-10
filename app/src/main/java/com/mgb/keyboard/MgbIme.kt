package com.mgb.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.mgb.keyboard.phonetic.AvroPhonetic
import com.mgb.keyboard.probhat.ProbhatLayout

/** v0.1: minimal English QWERTY keyboard. Every key press is wrapped in try/catch. */
class MgbIme : InputMethodService() {

    private var shift = false
    private val letterKeys = mutableListOf<Button>()

    // v0.0.3: Avro phonetic state
    private var bangla = false                 // saved in prefs key "bangla"
    private var latinOnly = false              // password / email / URL / number fields: always English
    private val buf = StringBuilder()          // roman letters of the word being typed
    private var langBtn: Button? = null
    private var dandaBtn: Button? = null

    // v0.0.4 Probhat + v0.0.5 extras state (all new; old fields untouched)
    private var probhat = false                // prefs key "bn_layout" = "probhat"
    private var container: FrameLayout? = null // holds the letters view OR a panel (symbols / numpad / settings)
    private var autoNumpad = false             // numpad was opened automatically for a number/phone field
    private var extrasRow: LinearLayout? = null // Probhat extra row (vowels, ঁ ঃ ৎ)
    // v0.0.6: clipboard history + edit bar
    private var pwdField = false               // true in password fields: nothing is recorded there
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val clipStore by lazy { ClipStore(JabraPrefs(this)) }
    private val clipPanel by lazy { ClipPanel(this, host, clipStore) }
    private val prefs by lazy { JabraPrefs(this) }
    private val panels by lazy { JabraPanels(this, host) }
    private val host = object : JabraHost {
        override fun typeText(s: String) { this@MgbIme.typeText(s) }
        override fun commitWord() { this@MgbIme.commitWord() }
        override fun backspace() { this@MgbIme.onBackspace() }
        override fun enter() { this@MgbIme.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        override fun showLetters() { this@MgbIme.showLetters() }
        override fun showPanel(name: String) { this@MgbIme.showPanel(name) }
        override fun feedback(v: View) { this@MgbIme.feedback(v) }
        override fun keyHeightDp(): Int = this@MgbIme.keyDp()
        override fun layoutChanged() { this@MgbIme.applyLayoutPref() }
    }

    override fun onCreateInputView(): View {
        return try {
            letterKeys.clear()
            val c = FrameLayout(this)
            c.addView(buildKeyboard())
            container = c
            c
        } catch (e: Throwable) {
            LinearLayout(this) // empty view instead of crashing
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun key(label: String, weight: Float, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minimumWidth = 0
        b.setPadding(0, 0, 0, 0)
        b.setOnClickListener {
            try { feedback(b) } catch (_: Throwable) { }   // v0.0.5: haptic/sound (own try, never blocks typing)
            try { onClick() } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(keyDp()), weight)   // v0.0.5: 48dp unless user changed height
        lp.setMargins(dp(2), dp(2), dp(2), dp(2))
        b.layoutParams = lp
        return b
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    private fun typeText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun letter(c: Char): Button {
        val b = key(c.toString(), 1f) { onLetter(c) }
        b.tag = c                       // v0.0.4: remember which letter this key is (labels change in Probhat mode)
        letterKeys.add(b)
        return b
    }

    private fun refreshLetters() {
        try { relabelLetters(); return } catch (_: Throwable) { }   // v0.0.4: handles Latin AND Probhat labels
        letterKeys.forEach { b ->
            val t = b.text.toString()
            b.text = if (shift) t.uppercase() else t.lowercase()
        }
    }

    // ---------- v0.0.3: Avro phonetic hooks (every one is try/catch; on any problem we just type Latin) ----------

    override fun onCreate() {
        super.onCreate()
        try { bangla = getSharedPreferences("jabra", MODE_PRIVATE).getBoolean("bangla", false) } catch (_: Throwable) { }
        try { probhat = prefs.str("bn_layout", "avro") == "probhat" } catch (_: Throwable) { probhat = false }
        // v0.0.6: listen to copy events (only text, only while this keyboard service lives)
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val l = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
            cm.addPrimaryClipChangedListener(l)
            clipListener = l
        } catch (_: Throwable) { }
    }

    override fun onDestroy() {
        try {
            val l = clipListener
            if (l != null) (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(l)
        } catch (_: Throwable) { }
        super.onDestroy()
    }

    /** v0.0.6: save the current clipboard text into the history (not in password fields, not if marked sensitive, not if turned OFF). */
    private fun captureClip() {
        try {
            if (pwdField || !prefs.bool("clip_on", true)) return
            val d = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return
            if (d.itemCount == 0) return
            val sensitive = try { d.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) ?: false } catch (_: Throwable) { false }
            if (sensitive) return
            val t = d.getItemAt(0).coerceToText(this)?.toString() ?: return
            clipStore.add(t)
        } catch (_: Throwable) { }
    }

    private fun clipText(): String? = try {
        val d = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        if (d == null || d.itemCount == 0) null else d.getItemAt(0).coerceToText(this)?.toString()
    } catch (_: Throwable) { null }

    private fun setClipText(s: String) {
        try { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("jabra", s)) } catch (_: Throwable) { }
    }

    /** v0.0.6 edit bar: stage 1 = the editor's own action; stage 2 = we do it ourselves; paste-plain is always text only. */
    private fun editAction(kind: String) {
        commitWord()
        val ic = currentInputConnection ?: return
        when (kind) {
            "all" -> if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON))
                ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 0, KeyEvent.META_CTRL_ON))
            }
            "copy" -> if (!ic.performContextMenuAction(android.R.id.copy)) {
                val s = ic.getSelectedText(0)
                if (!s.isNullOrEmpty()) setClipText(s.toString())
            }
            "cut" -> if (!ic.performContextMenuAction(android.R.id.cut)) {
                val s = ic.getSelectedText(0)
                if (!s.isNullOrEmpty()) { setClipText(s.toString()); ic.commitText("", 1) }
            }
            "paste" -> if (!ic.performContextMenuAction(android.R.id.paste)) pastePlain()
            "plain" -> pastePlain()
        }
    }

    private fun pastePlain() {
        val t = clipText() ?: return
        currentInputConnection?.commitText(t, 1)
    }

    private fun openClipboard() {
        clipPanel.reset()
        showPanel("clipboard")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            buf.setLength(0)
            val t = info?.inputType ?: 0
            val cls = t and InputType.TYPE_MASK_CLASS
            val v = t and InputType.TYPE_MASK_VARIATION
            latinOnly = cls != InputType.TYPE_CLASS_TEXT ||
                v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                v == InputType.TYPE_TEXT_VARIATION_URI
            pwdField = v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)   // v0.0.6
            captureClip()   // v0.0.6: catch text copied while we were not listening
            // v0.0.5: number / phone field -> number pad automatically (setting "numpad_auto"); other field -> back to letters
            try {
                val isNum = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                if (isNum && prefs.bool("numpad_auto", true)) { autoNumpad = true; showPanel("numpad") }
                else if (autoNumpad) { autoNumpad = false; showLetters() }
            } catch (_: Throwable) { }
            try { refreshLetters() } catch (_: Throwable) { }   // v0.0.4: Probhat labels follow the field type
        } catch (_: Throwable) { latinOnly = false }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        buf.setLength(0)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        try {
            // user moved the cursor / tapped elsewhere: forget the word being typed
            if (buf.isNotEmpty() && (candidatesStart < 0 || newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
                buf.setLength(0)
                currentInputConnection?.finishComposingText()
            }
        } catch (_: Throwable) { }
    }

    private fun phoneticOn(): Boolean = bangla && !latinOnly && !probhat   // v0.0.4: Probhat is not phonetic
    private fun probhatOn(): Boolean = bangla && !latinOnly && probhat

    // ---------- v0.0.4: Probhat (fixed layout) ----------

    /** Types the Bangla letter for this key. Returns false (-> normal typing) if the key is not in the table or anything fails. */
    private fun probhatType(ch: Char): Boolean {
        val s: String = (try { ProbhatLayout.map(ch) } catch (_: Throwable) { null }) ?: return false
        typeText(s)
        return true
    }

    /** Puts Bangla (Probhat) or Latin labels on the letter keys, and shows/hides the Probhat extra row. */
    private fun relabelLetters() {
        val pb = probhatOn()
        letterKeys.forEach { b ->
            val c = (b.tag as? Char) ?: return@forEach
            b.text = if (pb) ProbhatLayout.label(if (shift) c.uppercaseChar() else c)
                     else if (shift) c.uppercase() else c.toString()
        }
        extrasRow?.visibility = if (pb) View.VISIBLE else View.GONE
    }

    private fun extrasRowView(): LinearLayout {
        val r = row()
        ProbhatLayout.EXTRAS.forEach { s ->
            val k = key(ProbhatLayout.extraLabel(s), 1f) { commitWord(); typeText(s) }
            (k.layoutParams as LinearLayout.LayoutParams).height = dp(38)
            r.addView(k)
        }
        r.visibility = if (probhatOn()) View.VISIBLE else View.GONE
        extrasRow = r
        return r
    }

    private fun langLabel(): String = if (!bangla) "EN" else if (probhat) "প্রভা" else "বাং"

    /** After the language / layout changed: refresh the key captions and letter labels. */
    private fun afterLangChange() {
        langBtn?.text = langLabel()
        dandaBtn?.text = if (bangla) "।" else "?"
        refreshLetters()
    }

    /** Long-press on the language key: switch Bangla layout Avro <-> Probhat (and turn Bangla on). */
    private fun switchBnLayout() {
        commitWord()
        probhat = !probhat
        prefs.setStr("bn_layout", if (probhat) "probhat" else "avro")
        bangla = true
        try { getSharedPreferences("jabra", MODE_PRIVATE).edit().putBoolean("bangla", true).apply() } catch (_: Throwable) { }
        afterLangChange()
    }

    /** The settings panel changed "bn_layout": follow it. */
    private fun applyLayoutPref() {
        commitWord()
        probhat = prefs.str("bn_layout", "avro") == "probhat"
    }

    // ---------- v0.0.5: panels, height, haptic, space-drag ----------

    private fun keyDp(): Int = try { prefs.keyHeightDp(JabraPrefs.isLand(this)) } catch (_: Throwable) { 48 }

    private fun feedback(v: View) {
        try { if (prefs.bool("haptic", true)) v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Throwable) { }
        try {
            if (prefs.bool("sound", false)) (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
        } catch (_: Throwable) { }
    }

    private fun showLetters() {
        val c = container ?: return
        autoNumpad = false
        c.removeAllViews()
        try {
            letterKeys.clear()
            c.addView(buildKeyboard())
        } catch (_: Throwable) {
            c.addView(LinearLayout(this))
        }
    }

    private fun showPanel(name: String) {
        val c = container ?: return
        commitWord()
        val v = try { if (name == "clipboard") clipPanel.build() else panels.build(name) } catch (_: Throwable) { null }
        if (v == null) { showLetters(); return }
        c.removeAllViews()
        c.addView(v)
    }

    /** Space-drag: move the cursor n steps (negative = left). Stage 1 = DPAD key events; if the app ignores them nothing happens. */
    private fun moveCursor(n: Int) {
        commitWord()
        val code = if (n < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        var i = minOf(Math.abs(n), 20)
        while (i > 0) { sendDownUpKeyEvents(code); i-- }
    }

    private fun onLetter(c: Char) {
        val ch = if (shift) c.uppercaseChar() else c
        if (shift) { shift = false; refreshLetters() }
        if (probhatOn() && probhatType(ch)) return   // v0.0.4: fixed Bangla layout (not in password/email/URL/number fields)
        if (!phoneticOn()) { typeText(ch.toString()); return }
        buf.append(ch)
        renderWord()
    }

    private fun toBangla(roman: String): String =
        try { AvroPhonetic.convert(roman) } catch (_: Throwable) { roman } // fallback: show Latin letters

    private fun renderWord() {
        val ic = currentInputConnection ?: return
        ic.setComposingText(toBangla(buf.toString()), 1)
    }

    /** Finish the word being typed (called before space, punctuation, enter, language switch). */
    private fun commitWord() {
        if (buf.isEmpty()) return
        val ic = currentInputConnection
        val word = toBangla(buf.toString())
        buf.setLength(0)
        ic?.commitText(word, 1)
    }

    private fun onBackspace() {
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            if (buf.isEmpty()) currentInputConnection?.setComposingText("", 1) else renderWord()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    private fun toggleLang() {
        commitWord()
        bangla = !bangla
        try { getSharedPreferences("jabra", MODE_PRIVATE).edit().putBoolean("bangla", bangla).apply() } catch (_: Throwable) { }
        afterLangChange()   // v0.0.4: sets lang/danda captions and letter labels (was 2 lines here)
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        // v0.0.6: edit bar (setting "edit_bar", default ON): clipboard / select all / cut / copy / paste / paste plain
        try {
            if (prefs.bool("edit_bar", true)) {
                val eb = row()
                val items = arrayOf("📋" to "clip", "All" to "all", "Cut" to "cut", "Copy" to "copy", "Paste" to "paste", "Plain" to "plain")
                for ((label, kind) in items) {
                    val k = key(label, 1f) { if (kind == "clip") openClipboard() else editAction(kind) }
                    JabraIcons.put(this, k, kind)   // v0.0.7: own icon (kind = clip/all/cut/copy/paste/plain); Icons OFF = old text
                    (k.layoutParams as LinearLayout.LayoutParams).height = dp(36)
                    k.textSize = 12f
                    eb.addView(k)
                }
                root.addView(eb)
            }
        } catch (_: Throwable) { }

        // v0.0.5: number row (setting "numrow", default ON). If it fails the keyboard just has no number row.
        try {
            if (prefs.bool("numrow", true)) {
                val nr = row()
                "1234567890".forEach { d -> nr.addView(key(d.toString(), 1f) { commitWord(); typeText(d.toString()) }) }
                root.addView(nr)
            }
        } catch (_: Throwable) { }
        // v0.0.4: Probhat extra row (vowels, ঁ ঃ ৎ). Hidden (GONE) unless Probhat is active.
        try { root.addView(extrasRowView()) } catch (_: Throwable) { extrasRow = null }

        val r1 = row()
        "qwertyuiop".forEach { r1.addView(letter(it)) }
        root.addView(r1)

        val r2 = row()
        "asdfghjkl".forEach { r2.addView(letter(it)) }
        root.addView(r2)

        val r3 = row()
        r3.addView(JabraIcons.put(this, key("⇧", 1.5f) { shift = !shift; refreshLetters() }, "shift"))   // v0.0.7
        "zxcvbnm".forEach { r3.addView(letter(it)) }
        r3.addView(JabraIcons.put(this, key("⌫", 1.5f) { onBackspace() }, "back"))   // v0.0.7
        root.addView(r3)

        val r4 = row()
        r4.addView(key("?123", 1.2f) { showPanel("symbols") })   // v0.0.5: symbols / number pad / settings panel
        val lb = key(langLabel(), 1.3f) { toggleLang() }
        try { lb.setOnLongClickListener { try { switchBnLayout() } catch (_: Throwable) { }; true } } catch (_: Throwable) { }   // v0.0.4: long-press = Avro <-> Probhat
        langBtn = lb
        r4.addView(lb)
        r4.addView(key(",", 1f) { commitWord(); typeText(",") })
        val spaceAction = { commitWord(); typeText(" ") }
        val sb = key("space", 4f) { spaceAction() }
        // v0.0.5: drag on space = move cursor. If attaching fails, the normal click above still types a space.
        try { SpaceDragHelper(resources.displayMetrics.density, { n -> moveCursor(n) }, { spaceAction() }, { v -> feedback(v) }).attach(sb) } catch (_: Throwable) { }
        r4.addView(sb)
        val db = key(if (bangla) "।" else "?", 1f) { commitWord(); val d = dandaBtn?.text?.toString() ?: "."; typeText(if (latinOnly && d == "।") "?" else d) }
        dandaBtn = db
        r4.addView(db)
        r4.addView(key(".", 1f) { commitWord(); typeText(".") })
        r4.addView(JabraIcons.put(this, key("⏎", 1.4f) { commitWord(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }, "enter"))   // v0.0.7
        root.addView(r4)

        try { refreshLetters() } catch (_: Throwable) { }   // v0.0.4: right labels from the start (Probhat after rebuild / rotate)
        return root
    }
}
