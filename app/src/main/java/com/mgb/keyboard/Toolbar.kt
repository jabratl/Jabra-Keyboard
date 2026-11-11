package com.mgb.keyboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.mgb.keyboard.typing.ToolbarOrder

/** v0.2.1: MgbIme implements this: makeKey(id) returns the finished key for an item id (or "fold"), null = skip it. */
interface ToolbarHost {
    fun makeKey(id: String): Button?
}

/**
 * v0.2.1 custom toolbar (setting tb_custom; default ON since v0.2.7, OFF = the old fixed edit bar).
 * One row: fixed fold / unfold key on the left + a horizontally scrollable strip of keys built from the saved order
 * (prefs tb_order / tb_hidden, see typing/ToolbarOrder.java). Folded (pref tb_fold) = fold key + the FIRST visible item only.
 * build() returns null on any problem -> MgbIme uses the old edit bar.
 */
object Toolbar {

    private const val KEY_W = 44   // dp, fixed key width (the strip scrolls when the keys do not fit)
    private const val KEY_H = 36   // dp, same height as the old edit bar

    fun label(id: String): String = when (id) {
        "clip" -> "📋"; "all" -> "All"; "cut" -> "Cut"; "copy" -> "Copy"; "paste" -> "Paste"; "plain" -> "Plain"
        "fmt" -> "Aa"; "mode" -> "PH"
        "emoji" -> "\u263A"; "numpad" -> "123"; "sym" -> "#+="; "gear" -> "\u2699"; "voice" -> "\uD83C\uDFA4"
        "globe" -> "\uD83C\uDF10"; "theme" -> "Th"; "onehand" -> "1H"; "size" -> "Sz"; "pad" -> "Pad"; "undo" -> "\u21B6"; "redo" -> "\u21B7"; "vault" -> "Vlt"   // v0.4.4
        else -> id
    }

    fun title(id: String): String = when (id) {
        "fmt" -> "Formatter (Aa)"; "clip" -> "Clipboard"; "mode" -> "Mode (Phone / PC)"; "all" -> "Select all"
        "cut" -> "Cut"; "copy" -> "Copy"; "paste" -> "Paste"; "plain" -> "Paste plain"
        "emoji" -> "Emoji panel"; "numpad" -> "Number pad (123)"; "sym" -> "Symbols"; "gear" -> "Settings (gear)"
        "voice" -> "Voice typing"; "globe" -> "Next keyboard"; "theme" -> "Theme (cycle)"; "onehand" -> "One-hand (cycle)"; "size" -> "Size page"; "pad" -> "Cursor / select pad"; "undo" -> "Undo"; "redo" -> "Redo"; "vault" -> "Text Vault (snippets / notes / glossary)"   // v0.4.4
        else -> id
    }

    /** Icon name in JabraIcons (same names as the old edit bar). */
    fun icon(id: String): String = if (id == "mode") "mode_phone" else if (id == "voice") "mic" else id

    fun build(ctx: Context, prefs: JabraPrefs, host: ToolbarHost): View? = try { make(ctx, prefs, host) } catch (_: Throwable) { null }

    private fun make(ctx: Context, prefs: JabraPrefs, host: ToolbarHost): View? {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * d).toInt()
        fun fixed(): LinearLayout.LayoutParams {
            val lp = LinearLayout.LayoutParams(dp(KEY_W), dp(KEY_H))
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            return lp
        }

        val ids = ArrayList<String>()
        for (id in ToolbarOrder.visible(prefs.str("tb_order", ""), prefs.str("tb_hidden", ""), prefs.str("tb_on", ""))) {
            if (id == "fmt" && !prefs.bool("fmt_on", true)) continue     // old setting Formatter OFF = item hidden
            if (id == "mode" && !prefs.bool("mode_key", true)) continue  // old setting Mode key OFF = item hidden
            ids.add(id)
        }
        val shown: List<String> = if (prefs.bool("tb_fold", false)) ids.take(1) else ids

        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        val fold = host.makeKey("fold") ?: return null
        fold.layoutParams = fixed()
        bar.addView(fold)

        val strip = LinearLayout(ctx)
        strip.orientation = LinearLayout.HORIZONTAL
        for (id in shown) {
            var k0: Button? = null
            try { k0 = host.makeKey(id) } catch (_: Throwable) { }
            val k = k0 ?: continue
            k.layoutParams = fixed()
            strip.addView(k)
        }
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(strip)
        hs.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bar.addView(hs)
        return bar
    }
}
