package com.mgb.keyboard

import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import com.mgb.keyboard.phonetic.AvroPhonetic

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

    override fun onCreateInputView(): View {
        return try {
            letterKeys.clear()
            buildKeyboard()
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
            try { onClick() } catch (_: Throwable) { }
        }
        val lp = LinearLayout.LayoutParams(0, dp(48), weight)
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
        letterKeys.add(b)
        return b
    }

    private fun refreshLetters() {
        letterKeys.forEach { b ->
            val t = b.text.toString()
            b.text = if (shift) t.uppercase() else t.lowercase()
        }
    }

    // ---------- v0.0.3: Avro phonetic hooks (every one is try/catch; on any problem we just type Latin) ----------

    override fun onCreate() {
        super.onCreate()
        try { bangla = getSharedPreferences("jabra", MODE_PRIVATE).getBoolean("bangla", false) } catch (_: Throwable) { }
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

    private fun phoneticOn(): Boolean = bangla && !latinOnly

    private fun onLetter(c: Char) {
        val ch = if (shift) c.uppercaseChar() else c
        if (shift) { shift = false; refreshLetters() }
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
        langBtn?.text = if (bangla) "বাং" else "EN"
        dandaBtn?.text = if (bangla) "।" else "?"
    }

    private fun buildKeyboard(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#DDDDDD"))
        root.setPadding(dp(2), dp(4), dp(2), dp(4))

        val r1 = row()
        "qwertyuiop".forEach { r1.addView(letter(it)) }
        root.addView(r1)

        val r2 = row()
        "asdfghjkl".forEach { r2.addView(letter(it)) }
        root.addView(r2)

        val r3 = row()
        r3.addView(key("⇧", 1.5f) { shift = !shift; refreshLetters() })
        "zxcvbnm".forEach { r3.addView(letter(it)) }
        r3.addView(key("⌫", 1.5f) { onBackspace() })
        root.addView(r3)

        val r4 = row()
        val lb = key(if (bangla) "বাং" else "EN", 1.3f) { toggleLang() }
        langBtn = lb
        r4.addView(lb)
        r4.addView(key(",", 1f) { commitWord(); typeText(",") })
        r4.addView(key("space", 4f) { commitWord(); typeText(" ") })
        val db = key(if (bangla) "।" else "?", 1f) { commitWord(); val d = dandaBtn?.text?.toString() ?: "."; typeText(if (latinOnly && d == "।") "?" else d) }
        dandaBtn = db
        r4.addView(db)
        r4.addView(key(".", 1f) { commitWord(); typeText(".") })
        r4.addView(key("⏎", 1.4f) { commitWord(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) })
        root.addView(r4)

        return root
    }
}
