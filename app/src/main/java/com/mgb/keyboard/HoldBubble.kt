package com.mgb.keyboard

import android.view.View
import com.mgb.keyboard.typing.HoldPreview

/**
 * v0.17.31 (UI7): "hold preview bubble". When a key is HELD and its long-press text has been typed (hold "m" -> "?"),
 * the key bubble changes from the key label to that text, so you SEE what was typed. It stays until the finger lifts
 * (the existing release code already hides the bubble). Typing itself is NOT changed: the text is typed at the same
 * moment as before, this only adds the bubble.
 * Works only while "Key popup" is ON (same switch as the normal bubble). Action texts ("@paste") show nothing.
 * Reuses KeyPopup.showText() (the bubble the space-bar language swipe already uses). Never throws.
 * Delete this file + the 2 hook lines marked "v0.17.31" (HintKeys.kt, CustomRows.kt) = back to v0.17.30.
 */
object HoldBubble {
    fun show(anchor: View, typed: String, prefs: JabraPrefs) {
        try {
            if (!KeyCfg.popupOn(prefs)) return
            val label = HoldPreview.label(typed) ?: return
            KeyPopup.showText(anchor, label, prefs)
        } catch (_: Throwable) { }
    }
}
