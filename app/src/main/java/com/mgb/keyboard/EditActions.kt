package com.mgb.keyboard

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.mgb.keyboard.typing.EditOps

/**
 * v0.16.13 (X13 part 2): runs the 8 optional toolbar edit items. All text logic is in typing/EditOps.java (pure, tested);
 * this object only reads the field, calls EditOps and writes the result back. Every call is wrapped in try / catch:
 * any problem (app refuses the text, no selection info) = nothing happens, never a crash.
 * Model: text = the field text from getExtractedText (absolute base = startOffset). Fallback without extracted text:
 * Delete word / Delete line still work through getTextBeforeCursor / getTextAfterCursor + deleteSurroundingText (no selection only).
 * Never used in password fields (the caller checks pwdField).
 */
object EditActions {

    val IDS = listOf("selword", "selline", "delword", "delline", "linestart", "lineend", "dupline", "selnone")

    private const val WINDOW = 20000

    fun isEditId(id: String): Boolean = IDS.contains(id)

    /** Returns true when the item was handled (even if there was nothing to do). */
    fun run(ic: InputConnection, id: String): Boolean {
        if (!isEditId(id)) return false
        try {
            val req = ExtractedTextRequest()
            req.hintMaxChars = WINDOW
            req.hintMaxLines = 2000
            val ex = try { ic.getExtractedText(req, 0) } catch (_: Throwable) { null }
            val text = ex?.text?.toString()
            if (ex != null && text != null && ex.selectionStart >= 0 && ex.selectionEnd >= 0) {
                runExtracted(ic, id, text, ex.selectionStart, ex.selectionEnd, ex.startOffset)
            } else {
                runFallback(ic, id)
            }
        } catch (_: Throwable) { }
        return true
    }

    private fun runExtracted(ic: InputConnection, id: String, text: String, s: Int, e: Int, base: Int) {
        when (id) {
            "selword" -> EditOps.selectWord(text, s, e)?.let { ic.setSelection(base + it[0], base + it[1]) }
            "selline" -> EditOps.selectLine(text, s, e)?.let { ic.setSelection(base + it[0], base + it[1]) }
            "selnone" -> EditOps.collapse(text, s, e)?.let { ic.setSelection(base + it[0], base + it[1]) }
            "linestart" -> EditOps.lineStart(text, s, e).let { if (it >= 0) ic.setSelection(base + it, base + it) }
            "lineend" -> EditOps.lineEnd(text, s, e).let { if (it >= 0) ic.setSelection(base + it, base + it) }
            "delword" -> EditOps.deleteWord(text, s, e)?.let { deleteRange(ic, base + it[0], base + it[1]) }
            "delline" -> EditOps.deleteLine(text, s, e)?.let { deleteRange(ic, base + it[0], base + it[1]) }
            "dupline" -> EditOps.duplicateLine(text, s, e)?.let { d ->
                ic.beginBatchEdit()
                try {
                    ic.setSelection(base + d.pos, base + d.pos)
                    ic.commitText(d.text, 1)
                } finally { ic.endBatchEdit() }
            }
        }
    }

    private fun deleteRange(ic: InputConnection, a: Int, b: Int) {
        ic.beginBatchEdit()
        try {
            ic.setSelection(a, b)
            ic.commitText("", 1)
        } finally { ic.endBatchEdit() }
    }

    /** No extracted text: only the two delete items, only when nothing is selected, through relative deleteSurroundingText. */
    private fun runFallback(ic: InputConnection, id: String) {
        if (id != "delword" && id != "delline") return
        if (!ic.getSelectedText(0).isNullOrEmpty()) return
        val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
        val text = before + after
        val pos = before.length
        val r = (if (id == "delword") EditOps.deleteWord(text, pos, pos) else EditOps.deleteLine(text, pos, pos)) ?: return
        val back = (pos - r[0]).coerceAtLeast(0)
        val fwd = (r[1] - pos).coerceAtLeast(0)
        if (back > 0 || fwd > 0) ic.deleteSurroundingText(back, fwd)
    }
}
