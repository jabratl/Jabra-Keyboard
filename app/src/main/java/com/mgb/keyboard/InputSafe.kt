package com.mgb.keyboard

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.mgb.keyboard.typing.PcKeyPlan   // v0.17.49 (PC1)

/**
 * v0.3.1: input safety layer. Stage 1 = EXACTLY the key event the platform's sendDownUpKeyEvents() sends
 * (same flags), so apps that work today behave the same. Stage 2 (only when the app refuses the key event)
 * = plain text edit through the InputConnection. Never throws; every function returns normally.
 */
object InputSafe {
    private const val FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE

    /** One key down + up (with meta bits). true = the app accepted both events. */
    fun key(ic: InputConnection?, code: Int, meta: Int = 0): Boolean {
        if (ic == null) return false
        return try {
            val t = SystemClock.uptimeMillis()
            val d = ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))
            val u = ic.sendKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))
            d && u
        } catch (_: Throwable) { false }
    }

    // ---------- v0.17.49 (PC1): real modifier events + hold ----------
    const val COMBO_REFUSED = 0   // the very first event was refused: NOTHING was sent, caller may fall back
    const val COMBO_PARTIAL = 1   // some event was refused after the first: modifiers were still released
    const val COMBO_OK = 2        // every event accepted

    private fun send(ic: InputConnection, t0: Long, e: PcKeyPlan.Ev, repeat: Int = 0): Boolean =
        ic.sendKeyEvent(KeyEvent(t0, SystemClock.uptimeMillis(), e.action, e.code, repeat, e.meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))

    /** Plays PcKeyPlan: modifier DOWN(s), key DOWN, key UP, modifier UP(s). Same flags as key(). Never throws; modifiers never stay down. */
    fun keyCombo(ic: InputConnection?, code: Int, mods: Int): Int {
        if (ic == null) return COMBO_REFUSED
        val plan = PcKeyPlan.plan(code, mods)
        val t = SystemClock.uptimeMillis()
        var first = true
        var all = true
        try {
            for (e in plan) {
                val ok = send(ic, t, e)
                if (first && !ok) return COMBO_REFUSED
                first = false
                if (!ok) all = false
            }
        } catch (_: Throwable) {
            try { for (e in PcKeyPlan.modsUp(mods)) send(ic, t, e) } catch (_: Throwable) { }
            return if (first) COMBO_REFUSED else COMBO_PARTIAL
        }
        return if (all) COMBO_OK else COMBO_PARTIAL
    }

    /**
     * PC key with modifiers. Fallback chain: keyCombo (real modifier events, only when real = true) -> key() with meta bits only
     * -> Ctrl+A / C / V / X / Z through performContextMenuAction. No modifiers = plain key(). true = something was accepted.
     */
    fun pcKey(ic: InputConnection?, code: Int, mods: Int, real: Boolean): Boolean {
        if (ic == null) return false
        if (mods == 0) return key(ic, code, 0)
        if (real) {
            val r = keyCombo(ic, code, mods)
            if (r != COMBO_REFUSED) return r == COMBO_OK
        }
        if (key(ic, code, PcKeyPlan.legacyMeta(mods))) return true
        return ctxFallback(ic, code, mods)
    }

    private fun ctxFallback(ic: InputConnection, code: Int, mods: Int): Boolean {
        if (mods != PcKeyPlan.MOD_CTRL) return false
        val id = when (code) {
            KeyEvent.KEYCODE_A -> android.R.id.selectAll
            KeyEvent.KEYCODE_C -> android.R.id.copy
            KeyEvent.KEYCODE_V -> android.R.id.paste
            KeyEvent.KEYCODE_X -> android.R.id.cut
            KeyEvent.KEYCODE_Z -> android.R.id.undo
            else -> return false
        }
        return try { ic.performContextMenuAction(id) } catch (_: Throwable) { false }
    }

    /** Start of a held key: modifier DOWN(s) (only when real) + key DOWN. false = first event refused, nothing sent. */
    fun holdDown(ic: InputConnection?, code: Int, mods: Int, real: Boolean): Boolean {
        if (ic == null) return false
        return try {
            val t = SystemClock.uptimeMillis()
            val m = if (real) PcKeyPlan.realMeta(mods) else PcKeyPlan.legacyMeta(mods)
            var first = true
            if (real) for (e in PcKeyPlan.modsDown(mods)) { val ok = send(ic, t, e); if (first && !ok) return false; first = false }
            val d = ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, m, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))
            if (first && !d) return false
            true
        } catch (_: Throwable) { false }
    }

    /** Repeat of a held key: DOWN with a growing repeat count (like a hardware keyboard). */
    fun holdRepeat(ic: InputConnection?, code: Int, mods: Int, real: Boolean, n: Int): Boolean {
        if (ic == null) return false
        return try {
            val t = SystemClock.uptimeMillis()
            val m = if (real) PcKeyPlan.realMeta(mods) else PcKeyPlan.legacyMeta(mods)
            ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, n, m, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))
        } catch (_: Throwable) { false }
    }

    /** End of a held key: key UP, then modifier UP(s) in reverse. Never throws. */
    fun holdUp(ic: InputConnection?, code: Int, mods: Int, real: Boolean) {
        if (ic == null) return
        try {
            val t = SystemClock.uptimeMillis()
            val m = if (real) PcKeyPlan.realMeta(mods) else PcKeyPlan.legacyMeta(mods)
            ic.sendKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, m, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, FLAGS))
        } catch (_: Throwable) { }
        if (real) try { val t = SystemClock.uptimeMillis(); for (e in PcKeyPlan.modsUp(mods)) send(ic, t, e) } catch (_: Throwable) { }
    }

    /** Backspace. Fallback: delete the selection, else one character before the cursor. */
    fun backspace(ic: InputConnection?) {
        if (ic == null) return
        if (key(ic, KeyEvent.KEYCODE_DEL)) return
        try {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingTextInCodePoints(1, 0)
        } catch (_: Throwable) { }
    }

    /** v0.3.2: delete one word before the cursor (selection = delete the selection). Falls back to one backspace. */
    fun deleteWord(ic: InputConnection?) {
        if (ic == null) return
        try {
            val sel = ic.getSelectedText(0)
            if (!sel.isNullOrEmpty()) { ic.commitText("", 1); return }
            val n = com.mgb.keyboard.typing.WordDelete.count(ic.getTextBeforeCursor(com.mgb.keyboard.typing.WordDelete.WINDOW, 0))
            if (n > 0 && ic.deleteSurroundingText(n, 0)) return
        } catch (_: Throwable) { }
        backspace(ic)
    }

    /** Normal Enter (no editor action). Fallback: a new line character. */
    fun enter(ic: InputConnection?) {
        if (ic == null) return
        if (key(ic, KeyEvent.KEYCODE_ENTER)) return
        try { ic.commitText("\n", 1) } catch (_: Throwable) { }
    }

    /** Move the cursor n steps (negative = left, max 20). Fallback: setSelection from the field's own cursor position. */
    fun moveCursor(ic: InputConnection?, n: Int) {
        if (ic == null || n == 0) return
        val code = if (n < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        var i = minOf(Math.abs(n), 20)
        while (i > 0) {
            if (!key(ic, code)) { setCaretBy(ic, if (n < 0) -i else i); return }
            i--
        }
    }

    private fun setCaretBy(ic: InputConnection, d: Int) {
        try {
            val ex = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val len = ex.text?.length ?: return
            val pos = (ex.startOffset + ex.selectionEnd + d).coerceIn(ex.startOffset, ex.startOffset + len)
            ic.setSelection(pos, pos)
        } catch (_: Throwable) { }
    }
}
