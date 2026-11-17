package com.mgb.keyboard

/**
 * v0.29.0: FmtIo of ONE clip for the Find & Replace panel. The clip text is the "field": Replace saves the result back
 * into the same clip (ClipStore.replace, old code untouched), Undo puts the old text back. No selection, never a password field.
 * Clipboard / Sel buttons of the panel still use the real field / clipboard (delegated).
 */
class ClipReplaceIo(private val store: ClipStore, private var text: String, private val real: FmtIo) : FmtIo {
    override fun blocked(): Boolean = false
    override fun selected(): String? = null
    override fun wholeText(): String? = text
    private fun put(from: String, to: String): Boolean = try {
        val it0 = store.view("").firstOrNull { it.text == from }
        if (it0 == null) false else if (store.replace(it0, to) == 0) { text = to; true } else false
    } catch (_: Throwable) { false }
    override fun replaceSelection(s: String): Boolean = put(text, s)
    override fun replaceAll(s: String): Boolean = put(text, s)
    override fun undoReplace(res: String, orig: String): Boolean = if (text == res) put(res, orig) else false
    override fun clipText(): String? = real.clipText()
    override fun clipHtml(): String? = real.clipHtml()
    override fun setClip(plain: String, html: String?): Boolean = real.setClip(plain, html)
}
