package com.mgb.keyboard

import com.mgb.keyboard.typing.BarSize
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
import android.widget.ScrollView   // v0.3.3
import com.mgb.keyboard.phonetic.AvroPhonetic
import com.mgb.keyboard.probhat.ProbhatLayout

/** v0.1: minimal English QWERTY keyboard. Every key press is wrapped in try/catch. */
class MgbIme : InputMethodService() {

    private val shiftSt = com.mgb.keyboard.typing.ShiftState()   // v0.0.9: shift / caps lock / auto-capital logic (typing/ShiftState.java)
    private var shift: Boolean                                   // v0.0.9: same name as before, now backed by shiftSt
        get() = shiftSt.isShift()
        set(v) { shiftSt.setShift(v) }
    private var shiftBtn: Button? = null                         // v0.0.9: to show caps-lock state
    private val letterKeys = mutableListOf<Button>()
    private val numKeys = mutableListOf<Button>()   // v0.8.6: number-row keys that carry a Bangla digit hint

    // v0.0.3: Avro phonetic state
    private var bangla = false                 // saved in prefs key "bangla"
    private var latinOnly = false              // password / email / URL / number fields: always English
    private val buf = StringBuilder()          // roman letters of the word being typed
    private var sgTools = false                   // v0.10.1 (G2): toolbar page opened from the suggestion row (shown instead of the row)
    private val sg by lazy {   // v0.10.1 (G2): suggestion strip glue
        SuggestController(this, prefs,
            { try { if (buf.isNotEmpty()) toBangla(buf.toString()) else "" } catch (_: Throwable) { "" } },
            { try { currentInputConnection?.getTextBeforeCursor(com.mgb.keyboard.typing.SuggestPlan.LOOKBACK, 0)?.toString() ?: "" } catch (_: Throwable) { "" } },
            { bangla && !latinOnly },
            { latinOnly || pwdField },
            { text, del, fromAvro -> sgApply(text, del, fromAvro) },
            { openClipboard() },
            { prefs.bool("edit_bar", true) },
            { sgTools = true; showLetters() },
            { try { commitWord(); switcher.voice() } catch (_: Throwable) { } },
            selected = { try { currentInputConnection?.getSelectedText(0)?.toString() ?: "" } catch (_: Throwable) { "" } },   // v0.10.23 (W9)
            replaceSel = { res, start -> try {
                val ic = currentInputConnection
                if (ic != null) { ic.beginBatchEdit(); ic.commitText(res, 1); ic.setSelection(start, start + res.length); ic.endBatchEdit() }
            } catch (_: Throwable) { } },
            copyText = { t -> setClipText(t) })
    }
    private var langBtn: Button? = null
    private var spaceBtn: Button? = null   // v0.8.9: space key (shows the language name when space_lang is ON)
    private var dandaBtn: Button? = null

    // v0.0.4 Probhat + v0.0.5 extras state (all new; old fields untouched)
    private var probhat = false                // prefs key "bn_layout" = "probhat"
    private var container: FrameLayout? = null // holds the letters view OR a panel (symbols / numpad / settings)
    private var autoNumpad = false             // numpad was opened automatically for a number/phone field
    private var extrasRow: LinearLayout? = null // (old v0.0.4 Probhat vowel row; not built any more since v0.9.10.3)
    private val pbExtraKeys = mutableListOf<Pair<Button, Array<String>>>()   // v0.9.10.3: Probhat keys inside the 3 letter rows (GONE outside Probhat)
    private val homeGaps = mutableListOf<View>()   // v0.10.25: the two free-space views of the home row (a..l); GONE in Probhat; empty when row_gap is OFF
    // v0.0.6: clipboard history + edit bar
    private var pwdField = false               // true in password fields: nothing is recorded there
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private val clipStore by lazy { ClipStore(JabraPrefs(this)) }
    private val clipPanel by lazy { ClipPanel(this, host, clipStore) { fieldText() }.also { cp -> cp.toVault = { l -> vaultPanel.saveFromClips(l) } } }   // v0.2.5.1: Add button reads the field; v0.4.4: "To Vault" button
    private val vaultStore by lazy { VaultStore(this) }   // v0.4.4 Personal Text Vault (file vault_v1.json, loaded only when the panel opens)
    private val vaultPanel by lazy { VaultPanel(this, host, vaultStore) { fieldText() } }   // v0.4.4
    private val studioPanel by lazy { ThemeStudio(this, host) }   // v0.4.8 custom theme studio
    private val ttPanel by lazy { TextToolsPanel(this, host, ImeFmtIo(this) { pwdField }) }   // v0.4.6 text tools
    private val keyMapPanel by lazy { KeyMapPanel(this, host) }   // v0.16.5 (X5) key map cheat sheet (reads no pref)
    private val profilePanel by lazy { ProfilePanel(this, host, ProfileStore(this)) }   // v0.5.1 workspace profiles (file profiles_v1.json, read only when the panel opens)
    private val prefs by lazy { JabraPrefs(this) }
    private val panels by lazy { JabraPanels(this, host) }
    private val morePanel by lazy { MorePanel(this, host) }   // v0.0.9 "More settings" page
    private val emojiPanel by lazy { EmojiPanelSwitch(this, host) }   // v0.1.0 emoji panel; v0.8.1: new grid panel when the data file is loaded, else the old EmojiPanel
    private val sizePanel by lazy { SizePanel(this, host) }   // v0.1.4 "Size" page
    private val toolbarPanel by lazy { ToolbarPanel(this, host) }   // v0.2.1 "Edit toolbar" page
    private val toolbarQuick by lazy { ToolbarQuickPanel(this, host) }   // v0.2.7 quick add / remove page
    private val padPanel by lazy { PadPanel(this, host, padIo) }   // v0.2.3 cursor / select pad
    private val themePanel by lazy { ThemePanel(this, host) }   // v0.2.4 theme picker
    private val padIo = object : PadIo {   // v0.2.3
        override fun sendKey(code: Int, meta: Int) { this@MgbIme.padSend(code, meta) }
        override fun edit(kind: String) { this@MgbIme.editAction(kind) }
    }
    private val tbHost = object : ToolbarHost {   // v0.2.1
        override fun makeKey(id: String): Button? = this@MgbIme.toolbarKey(id)
    }
    private val typingPanel by lazy { TypingPanel(this, host) }   // v0.1.1 "Typing options" page
    private val spaceHelper by lazy { SpaceHelper(prefs) }   // v0.1.1 double space
    private val switcher by lazy { ImeSwitcher(this) }   // v0.1.1 Globe / Voice key
    private val pcState = com.mgb.keyboard.typing.PcState()   // v0.1.2 PC mode state (mode itself = pref pc_mode)
    private var pcRows: PcRows? = null   // v0.1.2
    private val fmtPanel by lazy { FormatterPanel(this, host, ImeFmtIo(this) { pwdField }) }   // v0.0.8 novel formatter
    private val host = object : JabraHost {
        override fun typeText(s: String) { this@MgbIme.typeText(s) }
        override fun commitWord() { this@MgbIme.commitWord() }
        override fun backspace() { this@MgbIme.onBackspace() }
        override fun enter() { this@MgbIme.doEnter() }   // v0.0.9: was sendDownUpKeyEvents(ENTER) - now uses the app's Send/Search/Go action when it has one
        override fun pickKeyboard() { this@MgbIme.pickKeyboard() }   // v0.0.9
        override fun showLetters() { this@MgbIme.showLetters() }
        override fun showPanel(name: String) { this@MgbIme.showPanel(name) }
        override fun feedback(v: View) { this@MgbIme.feedback(v) }
        override fun keyHeightDp(): Int = this@MgbIme.keyDp()
        override fun layoutChanged() { this@MgbIme.applyLayoutPref() }
        override fun fieldText(): String = this@MgbIme.fieldText()   // v0.5.0
        override fun currentApp(): String = try { currentInputEditorInfo?.packageName ?: "" } catch (_: Throwable) { "" }   // v0.10.18 (W6.1)
    }

    override fun onCreateInputView(): View {
        return try {
            letterKeys.clear(); numKeys.clear()
            val c = FrameLayout(this)
            c.addView(buildKeyboard())
            container = c
            try { KeyPopup.attachHost(c) } catch (_: Throwable) { }   // v0.9.9 (V4): overlay bubble view on top of the keyboard
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
        try { JabraThemes.style(prefs, b) } catch (_: Throwable) { }   // v0.1.0: theme colours (Classic = no change)
        try { com.mgb.keyboard.typing.KeyDesc.describe(label)?.let { b.contentDescription = it } } catch (_: Throwable) { }   // v0.10.20 (W7): spoken name for symbol keys (letters / digits: null = unchanged)
        b.setOnClickListener {
            val ps = KeyCfg.pStart()   // v0.9.10 (V5): meter (OFF = 0)
            try { if (KeyCfg.clickBuzz(b)) feedback(b) } catch (_: Throwable) { }   // v0.0.5: haptic/sound (own try, never blocks typing); v0.9.1: skipped when fb_down already buzzed at finger-down
            try { onClick() } catch (_: Throwable) { }
            KeyCfg.pEnd(com.mgb.keyboard.typing.KeyTiming.HANDLER, ps); KeyCfg.pCommit()
        }
        val lp = LinearLayout.LayoutParams(0, dp(keyDp()), weight)   // v0.0.5: 48dp unless user changed height
        val g = try { dp(com.mgb.keyboard.typing.SizePct.gapDp(prefs.int("key_gap", -1))) } catch (_: Throwable) { dp(2) }   // v0.3.0: key gap setting
        val gv = try { dp(com.mgb.keyboard.typing.EdgePlan.vGapDp(prefs.int("row_vgap", -1), com.mgb.keyboard.typing.SizePct.gapDp(prefs.int("key_gap", -1)))) } catch (_: Throwable) { g }   // v0.10.26: up-down gap (unset = same as the key gap = old)
        lp.setMargins(g, gv, g, gv)
        b.layoutParams = lp
        return b
    }

    /** v0.1.5: function key colour (only when look = Jabra; Classic = unchanged). */
    private fun fn(b: Button): Button { try { JabraLook.shape(prefs, b, JabraLook.FUNC) } catch (_: Throwable) { }; return b }

    private fun row(): LinearLayout {
        val r = GapRow(this)   // v0.9.10.4: a tap in the key gap goes to the nearest key (was lost)
        r.orientation = LinearLayout.HORIZONTAL
        return r
    }

    /** v0.1.1: space key = normal space, or double-space period / tab (settings ds_period / ds_tab, default OFF). Not in password / email / URL / number fields. */
    private fun spaceKey() {
        if (try { sg.autoCorrectOnSpace() } catch (_: Throwable) { false }) return   // v0.10.3 (G3.2): typo fixed on space (pref ac_on, default OFF)
        val done = try { !latinOnly && !pwdField && spaceHelper.handle(currentInputConnection, android.os.SystemClock.uptimeMillis(), bangla) } catch (_: Throwable) { false }
        if (!done) typeText(" ")
    }

    private var pendingKind = com.mgb.keyboard.typing.AutoCapGate.E_JUMP   // v0.6.1: what WE just typed; consumed by the next onUpdateSelection (default JUMP = ask)

    /** v0.10.1 (G2): a tap on a suggestion: replace the word being typed by [text] (word + space). */
    private fun sgApply(text: String, del: Int, fromAvro: Boolean) {
        if (fromAvro) buf.setLength(0)   // typeText() below replaces the composing text
        else if (del > 0) currentInputConnection?.deleteSurroundingText(del, 0)
        typeText(text)
    }

    private fun typeText(s: String) {
        try { sg.punctFix(s) } catch (_: Throwable) { }   // v0.10.6: "hello ," > "hello," right after a tapped suggestion (pref sp_punct, default ON)
        try { sg.noteTyped(s) } catch (_: Throwable) { }   // v0.10.2 (G3.1): learn the finished word (only when learn_on is ON; a pref read otherwise)
        try { sg.schedule() } catch (_: Throwable) { }   // v0.10.1 (G2): only restarts a 50 ms timer
        try { pendingKind = com.mgb.keyboard.typing.AutoCapGate.kindOf(s) } catch (_: Throwable) { pendingKind = com.mgb.keyboard.typing.AutoCapGate.E_JUMP }   // v0.6.1
        currentInputConnection?.commitText(s, 1)
    }

    private fun letter(c: Char): Button {
        val b = key(c.toString(), 1f) { onLetter(c) }
        b.tag = c                       // v0.0.4: remember which letter this key is (labels change in Probhat mode)
        letterKeys.add(b)
        try { val lp = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(lp)) b.textSize = com.mgb.keyboard.typing.SizePct.letterSp(lp) } catch (_: Throwable) { }   // v0.2.8: letter size %
        try { if (prefs.bool("letter_bold", false)) b.setTypeface(b.typeface, android.graphics.Typeface.BOLD) } catch (_: Throwable) { }   // v0.2.9: bold letters
        try { KeyPopup.attach(b, prefs) } catch (_: Throwable) { }   // v0.1.3: popup on keypress (setting key_popup)
        try { HintKeys.attach(b, prefs, hintHost); HintKeys.refresh(b, prefs, hintHost) } catch (_: Throwable) { }   // v0.1.6: long-press hint (replaces the listener, calls KeyPopup.touch)
        return b
    }

    /** v0.1.6: long-press hint letters (English only). */
    private val hintHost = object : HintHost {
        override fun hintTypingActive(): Boolean = try { com.mgb.keyboard.typing.LongPressHints.active(true, bangla && !latinOnly, pcState.isPc() && pcState.meta() != 0) } catch (_: Throwable) { false }
        override fun hintLabelActive(): Boolean = try { com.mgb.keyboard.typing.LongPressHints.active(true, bangla && !latinOnly, false) } catch (_: Throwable) { false }
        override fun banglaNumbers(): Boolean = bangla && !latinOnly   // v0.9.4 (R4)
        override fun typeHint(s: String) { commitWord(); typeText(s) }
        override fun hintFeedback(v: View) { feedback(v) }
    }
    private fun refreshHints() {
        try {
            // v0.6.1: read the 3 prefs ONCE for all 26 keys (was 3 reads per key on every Shift tap)
            val nr = prefs.bool("numrow", true); val cu = prefs.str("hint_custom", ""); val hh = prefs.bool("hint_hide", false)
            letterKeys.forEach { HintKeys.refresh(it, prefs, hintHost, nr, cu, hh) }
            numKeys.forEach { HintKeys.refresh(it, prefs, hintHost, nr, cu, hh) }   // v0.8.6
        } catch (_: Throwable) { try { letterKeys.forEach { HintKeys.refresh(it, prefs, hintHost) } } catch (_: Throwable) { } }
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
        try { EmojiStore.preload(this) } catch (_: Throwable) { }   // v0.8.1: load the emoji file once, in the background
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
        try { fbTone?.release(); fbTone = null } catch (_: Throwable) { }
        try { keyCfg.release() } catch (_: Throwable) { }   // v0.6.0
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
                InputSafe.key(ic, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)   // v0.3.1: real time + soft-keyboard flags (was time 0)
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

    /** v0.2.5.1: text for the clipboard "Add" button = selected text, else the whole field text. "" on any problem or in password fields. */
    private fun fieldText(): String = try {
        if (pwdField) "" else {
            commitWord()
            val ic = currentInputConnection
            if (ic == null) "" else {
                val sel = ic.getSelectedText(0)?.toString()
                if (!sel.isNullOrEmpty()) sel
                else (ic.getTextBeforeCursor(2001, 0)?.toString() ?: "") + (ic.getTextAfterCursor(2001, 0)?.toString() ?: "")
            }
        }
    } catch (_: Throwable) { "" }

    private fun openClipboard() {
        clipPanel.reset()
        showPanel("clipboard")
    }

    /** v0.0.8: opens the novel formatter panel (never in password fields). */
    private fun openFormatter() {
        if (pwdField) return
        fmtPanel.reset()
        showPanel("format")
    }

    /** v0.4.4: opens the Personal Text Vault panel (file is read now, not before). */
    private fun openVault() {
        vaultPanel.reset()
        showPanel("vault")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try { if (prefs.bool("bk_auto", true) && prefs.int("bk_day", 0) != BackupStore.today()) BackupStore.autoIfDue(this) } catch (_: Throwable) { }   // v0.4.0: auto backup once per day (background Thread)
        try { if (!prefs.bool("pc_remember", true) && prefs.bool("pc_mode", false)) { prefs.setBool("pc_mode", false); pcState.setPc(false); showLetters() } } catch (_: Throwable) { }   // v0.1.2: Remember mode OFF = always start in Phone mode
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
            if (sgTools) { sgTools = false; showLetters() }   // v0.10.1 (G2): a new field starts with the suggestion row again
            captureClip()   // v0.0.6: catch text copied while we were not listening
            // v0.0.5: number / phone field -> number pad automatically (setting "numpad_auto"); other field -> back to letters
            try {
                val isNum = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                if (isNum && prefs.bool("numpad_auto", true)) { autoNumpad = true; showPanel("numpad") }
                else if (autoNumpad) { autoNumpad = false; showLetters() }
            } catch (_: Throwable) { }
            try { shiftSt.reset(); autoCapUpdate() } catch (_: Throwable) { }   // v0.0.9: new field = shift/caps off, then sentence-start rule
            try { refreshLetters() } catch (_: Throwable) { }   // v0.0.4: Probhat labels follow the field type
        } catch (_: Throwable) { latinOnly = false }
        try { autoProfile(info?.packageName) } catch (_: Throwable) { }   // v0.10.19 (W6.2): auto-profile per app (pref ap_on, default OFF)
    }

    private var apLast = ""   // v0.10.19 (W6.2): profile applied for the current mapped app (memory only)

    /** v0.10.19 (W6.2): when ap_on and this app has a mapped profile that is not applied yet and would change something: apply it (same ProfileStore.apply as the panel) and rebuild the letters once. */
    private fun autoProfile(pkg: String?) {
        if (!prefs.bool("ap_on", false) || pkg.isNullOrEmpty()) return
        val map = com.mgb.keyboard.typing.AppProfileMap.parse(prefs.str("ap_json", ""))
        if (map.lookup(pkg).isEmpty()) { apLast = ""; return }   // app without a profile: forget, so coming back applies again
        val name = com.mgb.keyboard.typing.AppProfileApply.pick(map, pkg, apLast) ?: return
        val store = ProfileStore(this)
        val p = com.mgb.keyboard.typing.AppProfileApply.find(name, store.get(), com.mgb.keyboard.typing.ProfileCodec.starters()) ?: return   // deleted profile = ignored
        apLast = name
        if (!com.mgb.keyboard.typing.AppProfileApply.differs(p.values, getSharedPreferences("jabra", MODE_PRIVATE).all)) return   // nothing would change: no rebuild
        if (store.apply(p) && !autoNumpad) showLetters()   // number pad open (number field): the new look shows when the letters come back
    }

    /** v0.3.1: no full-screen "extract" editor in landscape (it hides the app and breaks many apps). Hidden pref fs_extract = true gives the old system behaviour. */
    override fun onEvaluateFullscreenMode(): Boolean =
        try { if (prefs.bool("fs_extract", false)) super.onEvaluateFullscreenMode() else false } catch (_: Throwable) { super.onEvaluateFullscreenMode() }

    override fun onFinishInput() {
        super.onFinishInput()
        try { sg.flushLearned() } catch (_: Throwable) { }   // v0.10.2 (G3.1)
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
        try { sg.selection(newSelStart, newSelEnd) } catch (_: Throwable) { }   // v0.10.23 (W9): remember the selection (plain assignment)
        try { sg.schedule() } catch (_: Throwable) { }   // v0.10.1 (G2): cursor moved / deleted
        val kind = pendingKind; pendingKind = com.mgb.keyboard.typing.AutoCapGate.E_JUMP   // v0.6.1: consume once; anything we did not type = JUMP
        autoCapUpdate(kind)   // v0.0.9 (has its own try/catch)
    }

    // ---------- v0.0.9: typing basics ----------

    /** Enter key: if the app asked for an action (Send / Search / Go / Next / Done) do that, else a normal Enter. Setting "enter_action". */
    private fun doEnter() {
        try {
            if (prefs.bool("enter_action", true)) {
                val a = com.mgb.keyboard.typing.EnterAction.decide(currentInputEditorInfo?.imeOptions ?: 0)
                if (a >= 0 && currentInputConnection?.performEditorAction(a) == true) return
            }
        } catch (_: Throwable) { }
        InputSafe.enter(currentInputConnection)   // v0.3.1: same key event; new-line text if the app refuses it
    }

    /** Opens the phone's own keyboard picker (switch to another keyboard). */
    private fun pickKeyboard() {
        try { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() } catch (_: Throwable) { }
    }

    /** Sentence-start capital for English typing (setting "autocap"). The field decides (inputType flags). Not used for Avro/Probhat (Avro uses capitals on purpose). */
    private fun autoCapUpdate(kind: Int = com.mgb.keyboard.typing.AutoCapGate.E_START) {
        try {
            if (!prefs.bool("autocap", true)) return
            if (phoneticOn() || probhatOn()) return
            val ic = currentInputConnection ?: return
            val inType = currentInputEditorInfo?.inputType ?: 0
            // v0.6.1: a plain letter we just typed cannot start a sentence -> skip the cross-process call (all other events still ask)
            try { if (!com.mgb.keyboard.typing.AutoCapGate.shouldCheck(kind, (inType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0)) return } catch (_: Throwable) { }
            val want = ic.getCursorCapsMode(inType) != 0
            if (shiftSt.autoCap(want)) refreshLetters()
        } catch (_: Throwable) { }
    }

    /** Caps lock = blue shift key, one-shot shift = light blue, off = normal. */
    private fun shiftVisual() {
        val b = shiftBtn ?: return
        val tint: Int? = if (shiftSt.isCaps()) Color.parseColor("#2271B1") else if (shiftSt.isShift()) Color.parseColor("#9CC3E6") else null
        b.backgroundTintList = if (tint == null) (try { JabraThemes.keyTint(prefs) } catch (_: Throwable) { null }) else android.content.res.ColorStateList.valueOf(tint)   // v0.1.0: off = theme key colour (Classic = null as before)
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
    /** v0.9.4 (R4): the Latin digit the number-row key [d] types / shows now (Bangla digit when numrow_bn is ON and a Bangla layout is active). */
    private fun numDigit(d: Char): String = try { com.mgb.keyboard.typing.HintStyle.digitLabel(d, prefs.bool("numrow_bn", false), bangla && !latinOnly) } catch (_: Throwable) { d.toString() }

    /** v0.9.4 (R4): number-row labels follow the language / field type (numKeys is empty unless a digit hint or numrow_bn is ON). */
    private fun relabelNumbers() {
        try {
            numKeys.forEach { b ->
                val c = (b.tag as? Char) ?: return@forEach
                val nt = numDigit(c)
                if (!nt.contentEquals(b.text)) b.text = nt
            }
        } catch (_: Throwable) { }
    }

    private fun relabelLetters() {
        val pb = probhatOn()
        letterKeys.forEach { b ->
            val c = (b.tag as? Char) ?: return@forEach
            val nt = if (pb) ProbhatLayout.label(if (shift) c.uppercaseChar() else c)
                     else if (shift) c.uppercase() else c.toString()
            if (!nt.contentEquals(b.text)) b.text = nt   // v0.6.1: no relayout when the label is already right
        }
        relabelNumbers()   // v0.9.4 (R4)
        relabelPbExtras(pb)   // v0.9.10.3: extra Probhat keys inside the letter rows (no separate vowel row, so no blank row)
        try { shiftVisual() } catch (_: Throwable) { }   // v0.0.9
        refreshHints()   // v0.1.6: hint labels follow the language / layout
    }

    /** v0.10.25: free space at the side of the home row (a..l). Not clickable, so GapRow sends a finger-down here to the nearest key ("a" or "l").
     *  Width = half a key in English / Avro (same key width as the row above); GONE in Probhat (the home row has 10 keys there). Rules: typing/RowGap.java. */
    private fun homeGapView(): View {
        val v = View(this)
        v.isClickable = false
        try { v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO } catch (_: Throwable) { }
        val w = com.mgb.keyboard.typing.RowGap.side(true, com.mgb.keyboard.typing.RowGap.homeKeys(false))
        v.layoutParams = LinearLayout.LayoutParams(0, dp(keyDp()), w)
        v.visibility = if (com.mgb.keyboard.typing.RowGap.side(true, com.mgb.keyboard.typing.RowGap.homeKeys(probhatOn())) > 0f) View.VISIBLE else View.GONE
        homeGaps.add(v)
        return v
    }

    /** v0.9.10.3: adds one Probhat-only key ({normal, shift}) to a letter row. It is GONE in English / Avro, so those rows are unchanged. */
    private fun pbExtra(r: LinearLayout, pair: Array<String>) {
        val b = key("", 1f) { onPbExtra(pair) }
        try { val lp = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(lp)) b.textSize = com.mgb.keyboard.typing.SizePct.letterSp(lp) } catch (_: Throwable) { }
        try { if (prefs.bool("letter_bold", false)) b.setTypeface(b.typeface, android.graphics.Typeface.BOLD) } catch (_: Throwable) { }
        val pb = probhatOn()
        b.text = ProbhatLayout.extraLabel(if (pb && shift) pair[1] else pair[0])
        b.visibility = if (pb) View.VISIBLE else View.GONE
        pbExtraKeys.add(Pair(b, pair))
        r.addView(b)
    }

    private fun onPbExtra(pair: Array<String>) {
        commitWord()
        val s = if (shift) pair[1] else pair[0]
        if (shift) { shiftSt.typed(); refreshLetters() }
        typeText(s)
    }

    private fun relabelPbExtras(pb: Boolean) {
        try {   // v0.10.25: home row gap only while the Probhat extra key is hidden. v0.10.27: pb_home_gap ON = Probhat also gets the left gap and its extra key is half width at the right (no key lost)
            val gapOn = prefs.bool("pb_home_gap", false)
            val rg = homeGaps.size > 0   // gaps exist only while row_gap is ON
            if (rg) {
                val lv = if (com.mgb.keyboard.typing.BottomPlan.leftGapShown(pb, gapOn, true)) View.VISIBLE else View.GONE
                val rv = if (com.mgb.keyboard.typing.BottomPlan.rightGapShown(pb, true)) View.VISIBLE else View.GONE
                if (homeGaps[0].visibility != lv) homeGaps[0].visibility = lv
                if (homeGaps.size > 1 && homeGaps[1].visibility != rv) homeGaps[1].visibility = rv
            }
            val kw = com.mgb.keyboard.typing.BottomPlan.pbKeyWeight(pb, gapOn, rg)
            pbExtraKeys.forEach { (b, pair) ->
                if (pair === ProbhatLayout.EXTRA_ROW2[0]) { val lp = b.layoutParams as LinearLayout.LayoutParams; if (lp.weight != kw) { lp.weight = kw; b.layoutParams = lp } }
            }
        } catch (_: Throwable) { }
        pbExtraKeys.forEach { (b, pair) ->
            val v = if (pb) View.VISIBLE else View.GONE
            if (b.visibility != v) b.visibility = v
            val nt = ProbhatLayout.extraLabel(if (pb && shift) pair[1] else pair[0])
            if (!nt.contentEquals(b.text)) b.text = nt
        }
    }

    private fun extrasRowView(): LinearLayout {
        val r = row()
        ProbhatLayout.EXTRAS.forEach { s ->
            val k = key(ProbhatLayout.extraLabel(s), 1f) { commitWord(); typeText(s) }
            (k.layoutParams as LinearLayout.LayoutParams).height = dp(38)
            r.addView(k)
        }
        r.visibility = extrasVis(probhatOn())   // v0.9.7 (V2)
        extrasRow = r
        return r
    }

    /** v0.9.7 (V2): visibility of the Probhat extra row. h_lock ON (default) = INVISIBLE (space kept) while Probhat can be chosen, so the keyboard height is the same in English / Avro / Probhat. OFF = v0.9.5 (GONE). Any problem = v0.9.5 rule. */
    private fun extrasVis(pb: Boolean): Int = try {
        when (com.mgb.keyboard.typing.HeightLock.mode(prefs.bool("h_lock", true), pb, prefs.bool("lang_probhat_on", true))) {
            com.mgb.keyboard.typing.HeightLock.VISIBLE -> View.VISIBLE
            com.mgb.keyboard.typing.HeightLock.INVISIBLE -> View.INVISIBLE
            else -> View.GONE
        }
    } catch (_: Throwable) { if (pb) View.VISIBLE else View.GONE }

    private fun langLabel(): String = if (!bangla) "EN" else (try { com.mgb.keyboard.typing.LangNames.keyLabel(probhat) } catch (_: Throwable) { if (probhat) "প্রভা" else "বাং" })   // v0.16.2: names from LangNames (old text = fallback)

    /** After the language / layout changed: refresh the key captions and letter labels. */
    private fun afterLangChange() {   // v0.9.10 (V5): timing wrapper, body unchanged below
        val ps = KeyCfg.pStart()
        try { afterLangChange0() } finally { KeyCfg.pEnd(com.mgb.keyboard.typing.KeyTiming.LANG, ps) }
    }

    private fun afterLangChange0() {
        try { sg.schedule() } catch (_: Throwable) { }   // v0.10.1 (G2): other language = other word list
        langBtn?.text = langLabel()
        try { if (prefs.bool("space_lang", false)) { spaceBtn?.text = com.mgb.keyboard.typing.LangSwipe.spaceLabel(bangla, probhat); spaceBtn?.contentDescription = com.mgb.keyboard.typing.KeyDesc.spaceDesc(spaceBtn?.text?.toString()) } } catch (_: Throwable) { }   // v0.8.9; v0.10.20 spoken name
        dandaBtn?.text = if (bangla) "।" else "?"
        refreshLetters()
    }

    /** Long-press on the language key: switch Bangla layout Avro <-> Probhat (and turn Bangla on). */
    private fun switchBnLayout() {
        commitWord()
        try { applyLang(com.mgb.keyboard.typing.LangOrder.longPress(bangla, probhat, prefs.bool("lang_avro_on", true), prefs.bool("lang_probhat_on", true))); return } catch (_: Throwable) { }   // v0.5.0: rules in typing/LangOrder.java (defaults = old behaviour); any problem = old code below
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

    private var fbTone: android.media.ToneGenerator? = null

    /** Fix: haptic / sound used to follow the phone's "touch feedback / touch sounds" system switches, so with those OFF nothing happened.
     *  Now: Vibrate uses the Vibrator directly (VIBRATE permission), Sound plays the system click or, if the system click is OFF, a short tone. */
    private val keyCfg by lazy { KeyCfg(this) }   // v0.6.0

    /** v0.6.0: fast path = cached flags + one Vibrator lookup. Any problem (cache null / throws) = feedbackOld(), the exact v0.5.9 code. */
    private fun feedback(v: View) {
        val c = try { keyCfg.get() } catch (_: Throwable) { null }
        if (c == null) { feedbackOld(v); return }
        try {
            if (c.haptic) {
                var done = false
                try { if (c.hapticNative) { v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING); done = true } } catch (_: Throwable) { }   // v0.9.1: system key tap (strength level ignored)
                try {
                    val vb = if (done) null else keyCfg.vibrator()
                    if (vb != null) {
                        if (android.os.Build.VERSION.SDK_INT >= 26) vb.vibrate(android.os.VibrationEffect.createOneShot(c.hapticMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                        else @Suppress("DEPRECATION") vb.vibrate(c.hapticMs)
                        done = true
                    }
                } catch (_: Throwable) { }
                if (!done) {
                    try {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                    } catch (_: Throwable) { }
                }
            }
            if (c.sound) soundFx()
        } catch (_: Throwable) { feedbackOld(v) }
    }

    /** Sound part, same logic as v0.5.9 (only runs when Sound is ON). */
    private fun soundFx() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            val sysOn = try { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1 } catch (_: Throwable) { true }
            if (sysOn) am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
            else {
                if (fbTone == null) fbTone = android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 60)
                fbTone?.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 25)
            }
        } catch (_: Throwable) { }
    }

    private fun feedbackOld(v: View) {   // v0.5.9 body, unchanged
        if (prefs.bool("haptic", true)) {
            var done = false
            val hMs = try { com.mgb.keyboard.typing.TypingLevels.hapticMs(prefs.int("haptic_lv", com.mgb.keyboard.typing.TypingLevels.HAPTIC_DEFAULT)).toLong() } catch (_: Throwable) { 18L }   // v0.5.2: strength (default Medium = 18 ms = old)
            try {
                @Suppress("DEPRECATION")
                val vb = getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
                if (vb != null && vb.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= 26) vb.vibrate(android.os.VibrationEffect.createOneShot(hMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") vb.vibrate(hMs)
                    done = true
                }
            } catch (_: Throwable) { }
            if (!done) {
                try {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
                } catch (_: Throwable) { }
            }
        }
        if (prefs.bool("sound", false)) {
            try {
                val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
                val sysOn = try { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1 } catch (_: Throwable) { true }
                if (sysOn) am.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD)
                else {
                    if (fbTone == null) fbTone = android.media.ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 60)
                    fbTone?.startTone(android.media.ToneGenerator.TONE_CDMA_PIP, 25)
                }
            } catch (_: Throwable) { }
        }
    }

    // ---------- v0.1.2: PC mode ----------
    private val pcIo = object : PcIo {
        override fun sendKey(code: Int, extraMeta: Int) { this@MgbIme.pcSend(code, extraMeta) }
        override fun edit(kind: String) { this@MgbIme.editAction(kind) }
        override fun feedback(v: View) { this@MgbIme.feedback(v) }
        override fun keyDp(): Int = this@MgbIme.keyDp()
    }

    /** Key down + up with the sticky Ctrl / Alt (a once-modifier ends after this key). */
    private fun pcSend(code: Int, extraMeta: Int) {
        commitWord()
        val ic = currentInputConnection ?: return
        val m = pcState.meta() or extraMeta
        InputSafe.key(ic, code, m)   // v0.3.1: same event + soft-keyboard flags
        pcState.keySent()
        try { pcRows?.refresh() } catch (_: Throwable) { }
    }

    /** Mode key: one tap = Phone <-> PC (pref pc_mode), then the keyboard is rebuilt. */
    private fun toggleMode() {
        commitWord()
        pcState.setPc(!prefs.bool("pc_mode", false))
        prefs.setBool("pc_mode", pcState.isPc())
        showLetters()
    }

    /** v0.2.3: one movement key (down + up) with the given meta bits (Shift = extend selection). */
    private fun padSend(code: Int, meta: Int) {
        commitWord()
        val ic = currentInputConnection ?: return
        InputSafe.key(ic, code, meta)   // v0.3.1: same event + soft-keyboard flags
    }

    /** v0.2.2: what a toolbar item does. Only opens things that already exist (panels, switcher, theme / one-hand cycle). */
    private fun toolbarAction(id: String) {
        try { if (prefs.bool("tb_auto", false)) prefs.setStr("tb_use", com.mgb.keyboard.typing.ToolUsage.count(prefs.str("tb_use", ""), id)) } catch (_: Throwable) { }   // v0.5.4: usage count (only when "Move most used to front" is ON)
        when (id) {
            "clip" -> openClipboard()
            "fmt" -> openFormatter()
            "emoji" -> { emojiPanel.reset(); showPanel("emoji") }
            "numpad" -> showPanel("numpad")
            "sym" -> showPanel("symbols")
            "gear" -> showPanel("settings")
            "size" -> showPanel("size")
            "pad" -> showPanel("pad")
            "vault" -> openVault()   // v0.4.4
            "tt" -> { if (!pwdField) { ttPanel.reset(); showPanel("tt") } }   // v0.4.6 (never in password fields)
            "profiles" -> { profilePanel.reset(); showPanel("profiles") }   // v0.5.1
            "keymap" -> { keyMapPanel.reset(); showPanel("keymap") }   // v0.16.5
            "undo" -> padSend(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON)   // v0.2.7: Ctrl+Z (apps that ignore key events: nothing happens)
            "redo" -> padSend(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)   // v0.2.7: Ctrl+Shift+Z
            "voice" -> { commitWord(); switcher.voice() }
            "globe" -> { commitWord(); switcher.globe() }
            "theme" -> { prefs.setInt("theme", (JabraThemes.index(prefs) + 1) % JabraThemes.ALL.size); showLetters() }
            "onehand" -> { prefs.setInt("onehand", (JabraOneHand.mode(prefs) + 1) % 3); showLetters() }
            else -> editAction(id)
        }
    }

    /** v0.2.1: one key of the custom toolbar (id from typing/ToolbarOrder.java, or "fold"). */
    /** v0.16.6 (X6): toolbar / old edit bar key height in dp (pref tb_hpct, 100 = 36 = old). */
    private fun barH(): Int = try { BarSize.heightDp(prefs.int("tb_hpct", BarSize.DEF)) } catch (_: Throwable) { 36 }

    private fun toolbarKey(id: String): Button? {
        if (id == "fold" && prefs.bool("sg_on", true)) {   // v0.10.1 (G2): with the suggestion row ON this first toolbar key brings the suggestions back
            val bk = key("\u00AB", 1f) { sgTools = false; showLetters() }
            bk.textSize = try { BarSize.foldSp(prefs.int("tb_ipct", BarSize.DEF)) } catch (_: Throwable) { 14f }   // v0.16.6 (X6)
            try { ToolTip.attach(bk, prefs) { "Suggestions" } } catch (_: Throwable) { }
            return bk
        }
        if (id == "fold") {
            val folded = prefs.bool("tb_fold", false)
            val f = key(if (folded) "\u00BB" else "\u00AB", 1f) { prefs.setBool("tb_fold", !prefs.bool("tb_fold", false)); showLetters() }
            f.textSize = try { BarSize.foldSp(prefs.int("tb_ipct", BarSize.DEF)) } catch (_: Throwable) { 14f }   // v0.16.6 (X6)
            try { ToolTip.attach(f, prefs) { if (prefs.bool("tb_fold", false)) "Unfold toolbar" else "Fold toolbar" } } catch (_: Throwable) { }   // v0.8.8: name tip
            return f
        }
        if (id == "mode") { val mk = modeKey(barH()); try { mk.textSize = BarSize.textSp(prefs.int("tb_ipct", BarSize.DEF)) } catch (_: Throwable) { }; attachEdit(mk, Toolbar.title("mode")); return mk }   // v0.2.7
        val k = key(Toolbar.label(id), 1f) { toolbarAction(id) }   // v0.2.2
        JabraIcons.putThemed(this, k, Toolbar.icon(id))
        k.textSize = try { BarSize.textSp(prefs.int("tb_ipct", BarSize.DEF)) } catch (_: Throwable) { 12f }   // v0.16.6 (X6): 100 = 12 sp
        attachEdit(k, Toolbar.title(id))   // v0.2.7: hold 1.5 s = Edit shortcut; v0.8.8: hold 0.3 s = name tip
        return k
    }

    /** v0.2.7: hold a toolbar key 1.5 s -> "Edit shortcut" bubble -> quick add / remove page. */
    private fun attachEdit(b: Button, tip: String? = null) { try { ToolbarEdit.attach(b, prefs, tip) { showPanel("tbquick") } } catch (_: Throwable) { } }

    private fun modeKey(heightDp: Int?): Button {
        val on = pcState.isPc()
        val k = key(if (on) "PC" else "PH", 1f) { toggleMode() }
        JabraIcons.putThemed(this, k, if (on) "mode_pc" else "mode_phone")
        if (heightDp != null) { (k.layoutParams as LinearLayout.LayoutParams).height = dp(heightDp); k.textSize = 12f }
        return k
    }

    private fun showLetters() {
        val c = container ?: return
        autoNumpad = false
        inPanel = false   // v0.3.3
        c.removeAllViews()
        try {
            letterKeys.clear(); numKeys.clear()
            c.addView(buildKeyboard())
        } catch (_: Throwable) {
            c.addView(LinearLayout(this))
        }
        try { KeyPopup.attachHost(c) } catch (_: Throwable) { }   // v0.9.9 (V4): removeAllViews() removed the overlay, put it back on top
    }

    private fun showPanel(name: String) {
        val c = container ?: return
        commitWord()
        if (PanelGuard.blocked(this, name)) { BackupStore.toast(this, "Panel switched off after 2 crashes. See Health Check in the app screen."); showLetters(); return }   // v0.5.3
        val v = try { if (name == "clipboard") { try { clipPanel.fitPx = if (!inPanel && c.height > 0) c.height else lettersH } catch (_: Throwable) { }; clipPanel.build() } else if (name == "format") fmtPanel.build() else if (name == "more") morePanel.build() else if (name == "emoji") emojiPanel.build() else if (name == "typing") typingPanel.build() else if (name == "size") sizePanel.build() else if (name == "toolbar") toolbarPanel.build() else if (name == "tbquick") toolbarQuick.build() else if (name == "pad") padPanel.build() else if (name == "themes") themePanel.build() else if (name == "vault") vaultPanel.build() else if (name == "tt") ttPanel.build() else if (name == "profiles") profilePanel.build() else if (name == "keymap") { try { keyMapPanel.fitPx = if (!inPanel && c.height > 0) c.height else lettersH } catch (_: Throwable) { }; keyMapPanel.build() } else if (name == "tstudio") studioPanel.build() else panels.build(name) } catch (_: Throwable) { try { PanelGuard.failed(this, name) } catch (_: Throwable) { }; null }   // v0.0.8: "format"; v0.1.0: "emoji"; v0.1.1: "typing"
        if (v == null) { showLetters(); return }
        try { PanelGuard.ok(this, name) } catch (_: Throwable) { }   // v0.5.3
        try { JabraOneHand.apply(this, prefs, v) } catch (_: Throwable) { }   // v0.1.0
        try { if (!inPanel && c.height > 0) lettersH = c.height } catch (_: Throwable) { }   // v0.3.3: remember the letters-keyboard height
        inPanel = true
        val shown0 = try { capTall(name, v, c) } catch (_: Throwable) { v }   // v0.3.3: tall panel = scrolls, window never grows
        val shown = try { fitWide(shown0, c) } catch (_: Throwable) { shown0 }   // v0.10.21 (D4): window wider than 700dp = centred 600dp column
        c.removeAllViews()
        c.addView(shown)
    }

    // v0.3.3 FIX: a tall panel (More settings has ~13 rows) made the keyboard window taller than the screen part the app
    // leaves free -> the app / browser text bar jumped up and the bottom rows were cut off. Now such a panel is capped to
    // the letters-keyboard height and scrolls inside. Panels that already fit are NOT wrapped (no change).
    private var inPanel = false
    private var lettersH = 0
    /** v0.10.21 (D4): tablet / fold / split screen wider than 700dp: panel content in a centred 600dp column (letters keyboard untouched). Phones: returns v itself. */
    private fun fitWide(v: View, c: FrameLayout): View {
        val wDp = if (c.width > 0) (c.width / resources.displayMetrics.density).toInt() else resources.configuration.screenWidthDp
        if (!com.mgb.keyboard.typing.FitPlan.shouldWrap(wDp)) return v
        val h = v.layoutParams?.height ?: FrameLayout.LayoutParams.WRAP_CONTENT
        val box = FrameLayout(this)
        box.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, h)
        val lp = FrameLayout.LayoutParams(dp(com.mgb.keyboard.typing.FitPlan.maxContentWidthDp(wDp)), h)
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL
        v.layoutParams = lp
        box.addView(v)
        return box
    }

    private fun capTall(name: String, v: View, c: FrameLayout): View {
        if (name != "settings" && name != "more" && name != "typing" && name != "size" && name != "themes" && name != "vault" && name != "tt" && name != "profiles" && name != "tstudio" && name != "keymap") return v
        val cap = if (lettersH > 0) maxOf(lettersH, dp(keyDp() * 4 + 16)) else dp(38 + 5 * (keyDp() + 4))
        val w = if (c.width > 0) c.width else resources.displayMetrics.widthPixels
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        if (v.measuredHeight <= cap) return v
        val sv = ScrollView(this)
        sv.isFillViewport = true
        sv.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, cap)
        sv.addView(v)
        return sv
    }

    /** Space-drag: move the cursor n steps (negative = left). Stage 1 = DPAD key events; if the app ignores them nothing happens. */
    private fun moveCursor(n: Int) {
        commitWord()
        InputSafe.moveCursor(currentInputConnection, n)   // v0.3.1: same DPAD events; setSelection fallback if the app refuses them
    }

    private fun onLetter(c: Char) {
        try { if (pcState.isPc() && pcState.meta() != 0 && c in 'a'..'z') { pcSend(KeyEvent.KEYCODE_A + (c - 'a'), 0); return } } catch (_: Throwable) { }   // v0.1.2: Ctrl / Alt + letter in PC mode
        val ch = if (shift) c.uppercaseChar() else c
        if (shift) { shiftSt.typed(); refreshLetters() }   // v0.0.9: caps lock stays, one-shot shift ends
        if (probhatOn() && probhatType(ch)) return   // v0.0.4: fixed Bangla layout (not in password/email/URL/number fields)
        if (!phoneticOn()) { typeText(ch.toString()); return }
        buf.append(ch)
        renderWord()
    }

    private fun toBangla(roman: String): String {
        try {   // v0.16.3 (X3): pref bn_map 1 (default) = Ridmik key map; any problem falls back to the Classic Avro rules below
            if (prefs.int("bn_map", 1) != 0) return com.mgb.keyboard.phonetic.RidmikMap.convert(roman)
        } catch (_: Throwable) { }
        return try { AvroPhonetic.convert(roman) } catch (_: Throwable) { roman } // fallback: show Latin letters
    }

    private fun renderWord() {
        val ic = currentInputConnection ?: return
        ic.setComposingText(toBangla(buf.toString()), 1)
        try { sg.schedule() } catch (_: Throwable) { }   // v0.10.1 (G2)
    }

    /** Finish the word being typed (called before space, punctuation, enter, language switch). */
    private fun commitWord() {
        if (buf.isEmpty()) return
        val ic = currentInputConnection
        val word = toBangla(buf.toString())
        buf.setLength(0)
        ic?.commitText(word, 1)
    }

    /** v0.3.2: long hold on Backspace = delete one whole word per tick (setting bs_word). Avro word being typed = cleared at once. */
    private fun onBackspaceWord() {
        if (buf.isNotEmpty()) { buf.setLength(0); currentInputConnection?.setComposingText("", 1) }
        else InputSafe.deleteWord(currentInputConnection)
    }

    private fun onBackspace() {
        pendingKind = com.mgb.keyboard.typing.AutoCapGate.E_BACKSPACE   // v0.6.1
        if (buf.isEmpty() && (try { sg.undoAutoCorrect() } catch (_: Throwable) { false })) return   // v0.10.3 (G3.2): backspace right after an auto-correct = undo
        if (buf.isNotEmpty()) {
            buf.setLength(buf.length - 1)
            if (buf.isEmpty()) currentInputConnection?.setComposingText("", 1) else renderWord()
        } else {
            InputSafe.backspace(currentInputConnection)   // v0.3.1: same DEL event; delete-text fallback if the app refuses it
        }
    }

    /** v0.5.0: apply a LangOrder result {bangla, probhat}: same state + prefs writes as the old code. v0.9.8: persist = false while a space swipe is going on (saved once at finger up). */
    private fun applyLang(t: IntArray, persist: Boolean = true) {
        bangla = t[0] == 1
        probhat = t[1] == 1
        if (persist) persistLang()
        afterLangChange()
    }

    /** v0.9.8 (V3): the two prefs writes of applyLang, now callable once (swipe end). */
    private fun persistLang() {
        prefs.setStr("bn_layout", if (probhat) "probhat" else "avro")
        try { getSharedPreferences("jabra", MODE_PRIVATE).edit().putBoolean("bangla", bangla).apply() } catch (_: Throwable) { }
    }

    private val langSess = com.mgb.keyboard.typing.LangSwipe.Session()   // v0.9.8

    /** v0.8.9: space hold + swipe: [n] steps (+ = next language among those switched on). v0.9.8: state only in memory + soft tick; prefs are written at finger up (endLangSwipe). */
    private fun swipeLang(n: Int) {
        commitWord()
        try {
            val a = prefs.bool("lang_avro_on", true)
            val p = prefs.bool("lang_probhat_on", true)
            val nt = com.mgb.keyboard.typing.LangSwipe.step(bangla, probhat, a, p, n)
            langSess.begin(bangla, probhat)
            applyLang(nt, false)
            langSess.move(bangla, probhat)
            try {
                if (com.mgb.keyboard.typing.LangSwipe.tickOn(prefs.bool("space_lang", false), prefs.bool("sp_tick", true)))
                    spaceBtn?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            } catch (_: Throwable) { }
        } catch (_: Throwable) {
            try { applyLang(com.mgb.keyboard.typing.LangSwipe.step(bangla, probhat, prefs.bool("lang_avro_on", true), prefs.bool("lang_probhat_on", true), n)) } catch (_: Throwable) { }   // v0.8.9 behaviour
        }
    }

    /** v0.9.8 (V3): finger up = save the language ONCE (only if it changed); cancel = back to the language the swipe started on (nothing was saved). */
    private fun endLangSwipe(cancelled: Boolean) {
        try {
            if (cancelled) { val s = langSess.cancel(); if (s != null) applyLang(s, false) }
            else { val s = langSess.finish(); if (s != null) persistLang() }
        } catch (_: Throwable) { }
    }

    /** v0.9.2 (R3): bubble above the space key with the ACTIVE language while swiping (setting sp_preview, default ON; only with space_lang ON). */
    private fun langBubble(v: View) {
        try {
            if (com.mgb.keyboard.typing.LangSwipe.previewOn(prefs.bool("space_lang", false), prefs.bool("sp_preview", true)))
                KeyPopup.showText(v, com.mgb.keyboard.typing.LangSwipe.label(bangla, probhat), prefs)
        } catch (_: Throwable) { }
    }

    private fun toggleLang() {
        commitWord()
        try { applyLang(com.mgb.keyboard.typing.LangOrder.tap(bangla, probhat, prefs.bool("lang_avro_on", true), prefs.bool("lang_probhat_on", true), prefs.int("lang_tap", 0), prefs.int("lang_fav", 0))); return } catch (_: Throwable) { }   // v0.5.0 (defaults = old behaviour); any problem = old code below
        bangla = !bangla
        try { getSharedPreferences("jabra", MODE_PRIVATE).edit().putBoolean("bangla", bangla).apply() } catch (_: Throwable) { }
        afterLangChange()   // v0.0.4: sets lang/danda captions and letter labels (was 2 lines here)
    }

    private fun buildKeyboard(): View {   // v0.9.10 (V5): timing wrapper, body unchanged below
        val ps = KeyCfg.pStart()
        try { return buildKeyboard0() } finally { KeyCfg.pEnd(com.mgb.keyboard.typing.KeyTiming.BUILD, ps) }
    }

    private fun buildKeyboard0(): View {
        try { KeyPopup.invalidate() } catch (_: Throwable) { }   // v0.9.0: key bubble is rebuilt on the next press (theme / look may have changed)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        val edgePx = try { dp(com.mgb.keyboard.typing.EdgePlan.edgeDp(prefs.int("edge_dp", -1))) } catch (_: Throwable) { dp(2) }   // v0.10.26: edge distance (unset = 2 dp = old)
        root.setPadding(edgePx, dp(4), edgePx, dp(4))
        try { JabraThemes.paint(prefs, root) } catch (_: Throwable) { }   // v0.1.0: theme background (Classic = same grey)   // v0.2.6

        try { pcState.setPc(prefs.bool("pc_mode", false)) } catch (_: Throwable) { }   // v0.1.2: pref pc_mode is the one truth
        // v0.0.6: edit bar (setting "edit_bar", default ON): clipboard / select all / cut / copy / paste / paste plain
        try {
            if (prefs.bool("edit_bar", true) && !sg.wants(sgTools)) {   // v0.10.1 (G2): the suggestion row takes the toolbar's place
                val tb = try { if (prefs.bool("tb_custom", true)) Toolbar.build(this, prefs, tbHost) else null } catch (_: Throwable) { null }   // v0.2.1
                if (tb != null) root.addView(tb) else {   // v0.2.1: custom toolbar, else the old bar below
                val eb = row()
                try { if (prefs.bool("mode_key", true)) eb.addView(modeKey(barH())) } catch (_: Throwable) { }   // v0.1.2: first key of the edit bar
                val items0 = arrayOf("📋" to "clip", "All" to "all", "Cut" to "cut", "Copy" to "copy", "Paste" to "paste", "Plain" to "plain")
                val items = if (prefs.bool("fmt_on", true)) items0 + ("Aa" to "fmt") else items0   // v0.0.8: formatter button (setting "fmt_on")
                for ((label, kind) in items) {
                    val k = key(label, 1f) { if (kind == "clip") openClipboard() else if (kind == "fmt") openFormatter() else editAction(kind) }
                    JabraIcons.putThemed(this, k, kind)   // v0.0.7: own icon (kind = clip/all/cut/copy/paste/plain); Icons OFF = old text
                    (k.layoutParams as LinearLayout.LayoutParams).height = dp(barH())   // v0.16.6 (X6)
                    k.textSize = try { BarSize.textSp(prefs.int("tb_ipct", BarSize.DEF)) } catch (_: Throwable) { 12f }
                    eb.addView(k)
                }
                root.addView(eb)
                }   // v0.2.1
            }
        } catch (_: Throwable) { }

        try { sg.attach(root, sgTools) } catch (_: Throwable) { }   // v0.10.1 (G2): suggestion row at the top of the keyboard (pref sg_on; replaces the toolbar; its left icon opens the toolbar)
        // v0.1.2: PC rows (only in PC mode)
        try { if (pcState.isPc()) { val pr = PcRows(this, pcState, pcIo); pcRows = pr; val pv = pr.view(); if (pv != null) root.addView(pv) } } catch (_: Throwable) { }
        // v0.5.0: custom character row (pref xrow_chars, set in Typing options; default empty = no row)
        try {
            val cr = com.mgb.keyboard.typing.LangOrder.customRow(prefs.str("xrow_chars", ""))
            if (cr.isNotEmpty()) { val xr = row(); cr.forEach { c -> xr.addView(key(c, 1f) { commitWord(); typeText(c) }) }; root.addView(xr) }
        } catch (_: Throwable) { }
        // v0.0.5: number row (setting "numrow", default ON). If it fails the keyboard just has no number row.
        try {
            if (prefs.bool("numrow", true)) {
                val nr = row()
                "1234567890".forEach { d -> nr.addView(key(d.toString(), 1f) { commitWord(); typeText(numDigit(d)) }) }   // v0.9.4: numDigit = Latin unless numrow_bn
                try {   // v0.8.6: Bangla digit hint on the number row (setting hint_digits, default OFF = nothing attached, old behaviour)
                    if (prefs.bool("hint_digits", false) || prefs.bool("numrow_bn", false)) {   // v0.9.4: numrow_bn also needs the hint listener (Latin digit on long-press)
                        for (i in 0 until nr.childCount) { val nb = nr.getChildAt(i) as Button; nb.tag = "1234567890"[i]; numKeys.add(nb); HintKeys.attach(nb, prefs, hintHost); HintKeys.refresh(nb, prefs, hintHost) }
                        relabelNumbers()   // v0.9.4
                    } else {
                        for (i in 0 until nr.childCount) KeyCfg.attachDown(nr.getChildAt(i), prefs) { v -> feedback(v) }   // v0.9.1: number row without hints = tiny listener for fb_down
                    }
                } catch (_: Throwable) { }
                try { val lp = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(lp)) for (i in 0 until nr.childCount) (nr.getChildAt(i) as Button).textSize = com.mgb.keyboard.typing.SizePct.letterSp(lp) } catch (_: Throwable) { }   // v0.3.4: number row follows Letter size %
                try { if (prefs.bool("letter_bold", false)) for (i in 0 until nr.childCount) { val nb = nr.getChildAt(i) as Button; nb.setTypeface(nb.typeface, android.graphics.Typeface.BOLD) } } catch (_: Throwable) { }   // v0.3.4: and Bold letters
                try { if (prefs.bool("numrow_large", false)) for (i in 0 until nr.childCount) { (nr.getChildAt(i).layoutParams as LinearLayout.LayoutParams).height = dp(keyDp() + 12); (nr.getChildAt(i) as Button).textSize = 18f * (try { val lq = prefs.int("letter_pct", -1); if (com.mgb.keyboard.typing.SizePct.letterValid(lq)) lq / 100f else 1f } catch (_: Throwable) { 1f }) } } catch (_: Throwable) { }   // v0.1.3: Large number row (setting numrow_large)
                root.addView(nr)
            }
        } catch (_: Throwable) { }
        // v0.0.4: Probhat extra row (vowels, ঁ ঃ ৎ). Hidden (GONE) unless Probhat is active.
        // v0.9.10.3: the Probhat vowel row (and its reserved blank space in English / Avro) is gone; Probhat fits in the 3 letter rows
        pbExtraKeys.clear()
        homeGaps.clear()   // v0.10.25

        val r1 = row()
        "qwertyuiop".forEach { r1.addView(letter(it)) }
        try { ProbhatLayout.EXTRA_ROW1.forEach { pbExtra(r1, it) } } catch (_: Throwable) { }   // v0.9.10.3
        root.addView(r1)

        val r2 = row()
        val rowGapOn = try { prefs.bool("row_gap", true) } catch (_: Throwable) { false }   // v0.10.25: home row side gap (default ON; OFF = exactly v0.10.24)
        try { if (rowGapOn) r2.addView(homeGapView()) } catch (_: Throwable) { }   // v0.10.25: free space left of "a"
        "asdfghjkl".forEach { r2.addView(letter(it)) }
        try { ProbhatLayout.EXTRA_ROW2.forEach { pbExtra(r2, it) } } catch (_: Throwable) { }   // v0.9.10.3
        try { if (rowGapOn) r2.addView(homeGapView()) } catch (_: Throwable) { }   // v0.10.25: free space right of "l"
        root.addView(r2)

        val r3 = row()
        val shb = JabraIcons.putThemed(this, key("⇧", 1.5f) { shiftSt.tap(android.os.SystemClock.uptimeMillis()); refreshLetters() }, "shift")   // v0.0.7 + v0.0.9 (double tap = caps lock)
        shiftBtn = shb
        fn(shb)   // v0.1.5
        r3.addView(shb)
        "zxcvbnm".forEach { r3.addView(letter(it)) }
        try { ProbhatLayout.EXTRA_ROW3.forEach { pbExtra(r3, it) } } catch (_: Throwable) { }   // v0.9.10.3
        val bkb = JabraIcons.putThemed(this, key("⌫", 1.5f) { onBackspace() }, "back")   // v0.0.7
        try { if (prefs.bool("bs_repeat", true)) RepeatKeyHelper({ onBackspace() }, { v -> feedback(v) }, if (prefs.bool("bs_word", true)) ({ onBackspaceWord() }) else null).attach(bkb) } catch (_: Throwable) { }   // v0.0.9: hold = repeat
        r3.addView(fn(bkb))   // v0.1.5
        try {   // v0.10.26: extra gap beside Shift / Backspace (pref fn_gap, unset = 0 = old). The 2 big keys get a little less weight so z..m keep the width of the row above.
            val fg = com.mgb.keyboard.typing.EdgePlan.fnGapDp(prefs.int("fn_gap", -1))
            if (fg > 0) {
                val sdp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
                val fw = com.mgb.keyboard.typing.EdgePlan.fnWeight(fg, sdp, com.mgb.keyboard.typing.EdgePlan.edgeDp(prefs.int("edge_dp", -1)))
                (shb.layoutParams as LinearLayout.LayoutParams).let { it.weight = fw; it.rightMargin += dp(fg) }
                (bkb.layoutParams as LinearLayout.LayoutParams).let { it.weight = fw; it.leftMargin += dp(fg) }
            }
        } catch (_: Throwable) { }
        root.addView(r3)

        val r4 = row()
        val bst = try { com.mgb.keyboard.typing.BottomPlan.style(prefs.int("bot_style", 0)) } catch (_: Throwable) { 0 }   // v0.10.27: bottom row style (0 = old)
        val symKey = key("?123", com.mgb.keyboard.typing.BottomPlan.symWeight(bst)) { showPanel("symbols") }   // v0.0.5: symbols / number pad / settings panel
        try { JabraLongPress.attach(symKey, prefs) { pickKeyboard() } } catch (_: Throwable) { }
        KeyDots.attach(symKey, prefs)   // v0.10.27: "..." mark (pref key_dots, default OFF)   // v0.0.9: long-press = keyboard picker
        r4.addView(fn(symKey))   // v0.1.5
        val lb = key(langLabel(), com.mgb.keyboard.typing.BottomPlan.langWeight(bst)) { toggleLang() }
        try { JabraLongPress.attach(lb, prefs) { switchBnLayout() } } catch (_: Throwable) { }
        KeyDots.attach(lb, prefs)   // v0.10.27   // v0.0.4: long-press = Avro <-> Probhat
        val showLang = try { com.mgb.keyboard.typing.LangSwipe.langKeyVisible(prefs.bool("key_lang", true), prefs.bool("space_lang", false)) } catch (_: Throwable) { true }   // v0.8.9: EN key optional (only with space swipe ON)
        langBtn = if (showLang) lb else null
        if (showLang) r4.addView(fn(lb))   // v0.1.5
        try { if (prefs.bool("mode_key", true) && !prefs.bool("edit_bar", true)) r4.addView(modeKey(null)) } catch (_: Throwable) { }   // v0.1.2: edit bar OFF -> mode key here
        // v0.1.1: optional Globe / Voice / Emoji keys (settings key_globe / key_voice / key_emoji, all default OFF = same row as before)
        try {
            if (prefs.bool("key_globe", false)) r4.addView(JabraIcons.putThemed(this, key("\uD83C\uDF10", 1f) { commitWord(); switcher.globe() }, "globe"))
            if (prefs.bool("key_voice", false)) r4.addView(JabraIcons.putThemed(this, key("\uD83C\uDFA4", 1f) { commitWord(); switcher.voice() }, "mic"))
            if (prefs.bool("key_emoji", false)) r4.addView(JabraIcons.putThemed(this, key("\u263A", 1f) { emojiPanel.reset(); showPanel("emoji") }, "emoji"))
        } catch (_: Throwable) { }
        val commaKey = key(",", 1f) { commitWord(); typeText(",") }
        try { JabraLongPress.attach(commaKey, prefs) { emojiPanel.reset(); showPanel("emoji") } } catch (_: Throwable) { }
        KeyDots.attach(commaKey, prefs)   // v0.10.27   // v0.1.0: long-press "," = emoji panel
        r4.addView(commaKey)
        val spaceAction = { commitWord(); spaceKey() }   // v0.1.1: double space
        val sb = key("space", 4f) { spaceAction() }
        try { JabraLook.shape(prefs, sb, JabraLook.PILL) } catch (_: Throwable) { }   // v0.1.5: pill space key
        val spaceLangOn = try { prefs.bool("space_lang", false) } catch (_: Throwable) { false }   // v0.8.9
        spaceBtn = if (spaceLangOn) sb else null
        if (spaceLangOn) { try { sb.text = com.mgb.keyboard.typing.LangSwipe.spaceLabel(bangla, probhat); sb.contentDescription = com.mgb.keyboard.typing.KeyDesc.spaceDesc(sb.text.toString()) } catch (_: Throwable) { } }   // v0.10.20: "Space, English"
        // v0.0.5: drag on space = move cursor. If attaching fails, the normal click above still types a space.
        try {   // v0.1.1: cursor speed (setting cursor_speed); v0.8.9: space_lang ON = swipe at once = language (v0.9.3: no hold)
            val sdh = SpaceDragHelper(resources.displayMetrics.density, { n -> moveCursor(n) }, { spaceAction() }, { v -> feedback(v) }, com.mgb.keyboard.typing.TypingLevels.stepDp(prefs.int("cursor_speed", com.mgb.keyboard.typing.TypingLevels.CS_DEFAULT)))
            if (spaceLangOn) sdh.attachLang(sb, { n -> swipeLang(n) }, { v -> try { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) } catch (_: Throwable) { } },
                { v -> langBubble(v) }, { try { KeyPopup.hide() } catch (_: Throwable) { } },
                { c -> endLangSwipe(c) }, { try { com.mgb.keyboard.typing.LangSwipe.stepDp(prefs.int("sp_step", com.mgb.keyboard.typing.LangSwipe.STEP_DEFAULT)) } catch (_: Throwable) { com.mgb.keyboard.typing.LangSwipe.STEP_DP } }) else sdh.attach(sb)   // v0.9.8: save once at finger up, sp_step   // v0.9.2 (R3): live language bubble
        } catch (_: Throwable) { }
        r4.addView(sb)
        val db = key(if (bangla) "।" else "?", 1f) { commitWord(); val d = dandaBtn?.text?.toString() ?: "."; typeText(if (latinOnly && d == "।") "?" else d) }
        dandaBtn = db
        r4.addView(db)
        r4.addView(key(".", 1f) { commitWord(); typeText(".") })
        r4.addView(fn(JabraIcons.putThemed(this, key("⏎", com.mgb.keyboard.typing.BottomPlan.enterWeight(bst)) { commitWord(); doEnter() }, "enter")))   // v0.0.7 + v0.0.9
        try {   // v0.10.27: Ridmik-like bottom row: space takes what is left, so the row stays as wide as before (old style = space 4, nothing changes)
            if (com.mgb.keyboard.typing.BottomPlan.ridmik(bst)) {
                var others = 0f
                for (i in 0 until r4.childCount) { val c = r4.getChildAt(i); if (c !== sb) others += (c.layoutParams as LinearLayout.LayoutParams).weight }
                (sb.layoutParams as LinearLayout.LayoutParams).weight = com.mgb.keyboard.typing.BottomPlan.spaceWeight(bst, others)
            }
        } catch (_: Throwable) { }
        root.addView(r4)

        try { refreshLetters() } catch (_: Throwable) { }   // v0.0.4: right labels from the start (Probhat after rebuild / rotate)
        try { JabraOneHand.apply(this, prefs, root) } catch (_: Throwable) { }   // v0.1.0: one-handed mode (setting "onehand")
        return root
    }
}
