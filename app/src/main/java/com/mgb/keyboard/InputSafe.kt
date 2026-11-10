package com.mgb.keyboard

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

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
